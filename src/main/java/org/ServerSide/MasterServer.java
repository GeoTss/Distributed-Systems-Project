package org.ServerSide;
import java.io.IOException;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import org.Domain.Shop;
import org.ManagerSide.ManagerRequestHandler;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ServerSide.ClientRequests.ClientRequestHandler;
import org.Workers.WorkerClient;
import org.Workers.WorkerCommandType;
import org.Workers.WorkerHandler;

public class MasterServer {
    public static final int SERVER_CLIENT_PORT = 7777;
    public static final String SERVER_LOCAL_HOST = "127.0.0.1";

    private ServerSocket connection = null;
    private Socket server_socket = null;
    private static boolean has_manager_connected = false;

    private static ServerConfigInfo config_info;

    public static HashMap<Integer, ReplicationHandler> replicated_worker_handlers = new HashMap<>();
    public static HashMap<Integer, Integer> shop_id_hash = new HashMap<>();

    public static final Object CONNECTION_ACCEPT_LOCK = new Object();

    MasterServer() throws IOException, URISyntaxException, ClassNotFoundException {

        config_info = ServerFileLoader.load_config();

        System.out.println(config_info);
    }

    static int hashStore(Shop shop){
        return shop.getId();
    }

    public static int getWorkerHashToLookFor(int chosen_shop_id){
        return shop_id_hash.get(chosen_shop_id);
    }

    public static ReplicationHandler getWorkerFromHashID(int worker_hash){
        return replicated_worker_handlers.get(worker_hash);
    }

    public static ReplicationHandler getWorkerForShop(int chosen_shop_id) {
        Integer worker_id = shop_id_hash.get(chosen_shop_id);
        System.out.println("[LOOKUP] shop " + chosen_shop_id + " → worker_id " + worker_id);

        ReplicationHandler h = replicated_worker_handlers.get(worker_id);
        System.out.println("[LOOKUP] found handler for key " + worker_id + ": " + h);
        return h;
    }


    void initializeWorkers() throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {

        ArrayList<Shop> database_shops = ServerFileLoader.load_shops();

        HashMap<Integer, ArrayList<Shop>> worker_shop_map = new HashMap<>();
        int worker_count = config_info.getWorker_count();

        for(int i = 0; i < worker_count; ++i)
            worker_shop_map.put(i, new ArrayList<>());

        for(Shop shop: database_shops) {
            int worker_hash = hashStore(shop) % worker_count;
            System.out.println("Assigning shop ID " + shop.getId() + " to worker " + worker_hash);
            shop_id_hash.put(shop.getId(), worker_hash);
            worker_shop_map.get(worker_hash).add(shop);
        }

        ArrayList<WorkerClient> worker_clients = new ArrayList<>();
        HashMap<Integer, WorkerHandler> worker_handlers = new HashMap<>();
        worker_shop_map.forEach((worker_id, shop_list) -> {

            WorkerClient worker_client = new WorkerClient(worker_id);
            worker_clients.add(worker_client);
            worker_client.start();

            Socket main_worker_socket;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            try {
                synchronized (CONNECTION_ACCEPT_LOCK) {
                    CONNECTION_ACCEPT_LOCK.wait();
                    main_worker_socket = connection.accept();
                }

                out = new ObjectOutputStream(main_worker_socket.getOutputStream());
                in = new ObjectInputStream(main_worker_socket.getInputStream());

                out.writeObject(shop_list);
                out.flush();
            } catch(InterruptedException | IOException e){
                e.printStackTrace();
            }

            WorkerHandler main_handler = new WorkerHandler(out, in);
            main_handler.setId(worker_id);
            System.out.println("For " + worker_id + " the worker handler has ID: " + main_handler.getHandlerId());
            worker_handlers.put(worker_id, main_handler);
            main_handler.start();
        });

        worker_shop_map.forEach((worker_id, shop_list) -> {
            try {
                ReplicationHandler replicated_worker = new ReplicationHandler();
                replicated_worker.setMain(worker_handlers.get(worker_id));
                System.out.println("For " + worker_id + " the replicated worker handler has ID: " + replicated_worker.getMain().getHandlerId());

                replicated_worker.getReplicas().clear();
                for (int j = 1; j <= config_info.get_number_of_replicas(); ++j) {
                    int fallback_index = (worker_id + j) % worker_count;

                    WorkerHandler fallback = worker_handlers.get(fallback_index);
                    ObjectOutputStream fallback_out = fallback.getWorker_out();

                    fallback_out.writeInt(WorkerCommandType.ADD_BACKUP.ordinal());
                    fallback_out.writeInt(worker_id);
                    fallback_out.writeObject(shop_list);
                    fallback_out.flush();

                    WorkerHandler fallback_handler = worker_handlers.get(fallback_index);
                    replicated_worker.add_replica(fallback_handler);
                }
                replicated_worker.setId(worker_id);
                replicated_worker_handlers.put(worker_id, replicated_worker);
            }catch (IOException e){
                e.printStackTrace();
            }
        });

        worker_handlers.values().forEach(handler -> {
            try {
                handler.getWorker_out().writeInt(WorkerCommandType.END_BACKUP_LIST.ordinal());
                handler.getWorker_out().flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        replicated_worker_handlers.forEach((id, repl) -> {
            System.out.println("Main ID: " + id + " Handler: " + repl);
        });

        System.out.println("All workers connected!");
    }

    void openServer() {
        try {
            connection = new ServerSocket(SERVER_CLIENT_PORT);

            initializeWorkers();

            ManagerRequestHandler.replicated_worker_handlers = replicated_worker_handlers;

            System.out.println("SERVER STARTED");
            while (true) {
                server_socket = connection.accept();
                System.out.println(server_socket);

                ObjectOutputStream out = null;
                ObjectInputStream in = null;

                try {
                    System.out.println("Trying to get streams from " + connection);
                    out = new ObjectOutputStream(server_socket.getOutputStream());
                    out.flush();
                    in = new ObjectInputStream(server_socket.getInputStream());
                    System.out.println("Got streams from " + connection);

                } catch (IOException e) {
                    System.out.println("Exception at: ");
                    System.out.println("MasterServer.openServer");
                    e.printStackTrace();
                    server_socket.close();
                }

                assert in != null;
                int connection_type_ord = in.readInt();
                ConnectionType connection_type = ConnectionType.values()[connection_type_ord];

                Thread t = null;

                if(connection_type == ConnectionType.CLIENT)
                    t = new ClientRequestHandler(server_socket, out, in);
                else if(connection_type == ConnectionType.MANAGER && !has_manager_connected) {
                    t = new ManagerRequestHandler(server_socket, out, in);
                    has_manager_connected = true;
                }

                t.start();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (ClassNotFoundException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                connection.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public static ServerConfigInfo getConfig_info(){
        return config_info;
    }

    public static void main(String[] args) throws IOException, URISyntaxException, ClassNotFoundException {
        new MasterServer().openServer();
    }
}

