package org.ServerSide;
import java.io.IOException;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.Domain.Shop;
import org.Domain.Utils;
import org.ManagerSide.ManagerRequestHandler;
import org.MessagePKG.MessageType;
import org.ReducerSide.ReducerPreparationType;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ClientSide.ClientRequestHandler;
import org.Workers.WorkerClient;
import org.Workers.WorkerCommandType;
import org.Workers.Listeners.ReplicationListener;
import org.Workers.WorkerManagerCommandType;

public class MasterServer {
    public static final int SERVER_CLIENT_PORT = 7777;
    public static String SERVER_HOST = "127.0.0.1";

    private ServerSocket connection = null;
    private Socket server_socket = null;
    public static boolean has_manager_connected = false;

    private static ServerConfigInfo config_info;

    public static HashMap<Integer, ReplicationHandler> replicated_worker_handlers = new HashMap<>();
    public static HashMap<Integer, Integer> shop_id_hash = new HashMap<>();

    public static HashMap<Integer, ReplicationListener> worker_listeners = new HashMap<>();

    public static ReplicationListener reducer_listener = null;
    public static final int REDUCER_ID = Integer.MAX_VALUE;
    public static ObjectOutputStream reducer_writer = null;

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

    public static void addHashShop(Shop shop){
        int hashed = hashStore(shop);
        int worker_id = hashed % config_info.getWorker_count();
        shop_id_hash.put(shop.getId(), worker_id);
    }

    public static ReplicationHandler getWorkerForShop(int chosen_shop_id) {
        Integer worker_id = shop_id_hash.get(chosen_shop_id);
        System.out.println("[LOOKUP] shop " + chosen_shop_id + " → worker_id " + worker_id);

        ReplicationHandler h = replicated_worker_handlers.get(worker_id);
        System.out.println("[LOOKUP] found handler for key " + worker_id + ": " + h);
        return h;
    }

    Utils.Pair<ObjectOutputStream, ObjectInputStream> connectWorkerManager() {
        try{

            Socket worker_manager_sock = connection.accept();
            ObjectOutputStream out = new ObjectOutputStream(worker_manager_sock.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(worker_manager_sock.getInputStream());

            return new Utils.Pair<>(out, in);

        }catch (IOException e){
            e.printStackTrace();
        }
        return null;
    }

    void connectReducer(){
        ObjectInputStream reducer_in = null;
        try {

            Socket reducer_socket = connection.accept();

            reducer_writer = new ObjectOutputStream(reducer_socket.getOutputStream());
            reducer_in = new ObjectInputStream(reducer_socket.getInputStream());

            reducer_writer.writeInt(REDUCER_ID);
            reducer_writer.writeInt(config_info.getWorker_count());
            reducer_writer.flush();

            reducer_listener = new ReplicationListener(reducer_in);
            reducer_listener.setId(REDUCER_ID);
            reducer_listener.start();

        } catch(IOException e){
            e.printStackTrace();
        }
    }

    void initializeWorkers(ObjectOutputStream worker_manager_writer, ObjectInputStream worker_manager_input) throws IOException, ClassNotFoundException, InterruptedException, URISyntaxException {

        ArrayList<Shop> database_shops = ServerFileLoader.load_shops();

        HashMap<Integer, ArrayList<Shop>> worker_shop_map = new HashMap<>();
        int worker_count = config_info.getWorker_count();

        for(int i = 0; i < worker_count; ++i)
            worker_shop_map.put(i, new ArrayList<>());

        for(Shop shop: database_shops) {
            int worker_hash = hashStore(shop) % worker_count;
            shop_id_hash.put(shop.getId(), worker_hash);
            worker_shop_map.get(worker_hash).add(shop);
        }

        ArrayList<WorkerClient> worker_clients = new ArrayList<>();

        HashMap<Integer, ObjectOutputStream> worker_out_streams = new HashMap<>();

        worker_shop_map.forEach((worker_id, shop_list) -> {

            try {
                worker_manager_writer.writeInt(MessageType.INITIAZE_WORKER.ordinal());
                worker_manager_writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            Socket main_worker_socket;
            ObjectOutputStream out = null;
            ObjectInputStream in = null;
            try {

                main_worker_socket = connection.accept();

                out = new ObjectOutputStream(main_worker_socket.getOutputStream());
                in = new ObjectInputStream(main_worker_socket.getInputStream());

                worker_out_streams.put(worker_id, out);

                out.writeInt(worker_id);
                out.writeObject(shop_list);
                out.flush();

                reducer_writer.writeInt(ReducerPreparationType.REDUCER_ADD_WORKER_CONNECTION.ordinal());
                reducer_writer.flush();

            } catch(IOException e){
                e.printStackTrace();
            }

            ReplicationListener main_handler = new ReplicationListener(in);
            main_handler.setId(worker_id);

            System.out.println("For " + worker_id + " the listener has ID: " + main_handler.getHandlerId());
            worker_listeners.put(worker_id, main_handler);
            main_handler.start();
        });

        reducer_writer.writeInt(ReducerPreparationType.REDUCER_END_OF_WORKERS.ordinal());
        reducer_writer.flush();

        worker_manager_writer.writeInt(MessageType.END_OF_INITIALIZATION.ordinal());
        worker_manager_writer.flush();

        worker_shop_map.forEach((worker_id, shop_list) -> {
            try {
                ReplicationHandler replicated_worker = new ReplicationHandler();
                replicated_worker.clearData();

                replicated_worker.setId(worker_id);
                replicated_worker.setMainId(worker_id);
                replicated_worker.setMain(worker_out_streams.get(worker_id));

                System.out.println("For " + worker_id + " the replicated worker handler has ID: " + replicated_worker.getId());

                for (int j = 1; j <= config_info.get_number_of_replicas(); ++j) {
                    int fallback_index = (worker_id + j) % worker_count;

                    ObjectOutputStream fallback_out = worker_out_streams.get(fallback_index);

                    fallback_out.writeInt(MessageType.ADD_BACKUP.ordinal());
                    fallback_out.writeInt(worker_id);
                    fallback_out.writeObject(shop_list);
                    fallback_out.flush();

                    replicated_worker.add_replica(fallback_index, fallback_out);
                }

                replicated_worker_handlers.put(worker_id, replicated_worker);
            }catch (IOException e){
                e.printStackTrace();
            }
        });

        worker_out_streams.values().forEach(worker_writer -> {
            try {
                worker_writer.writeInt(MessageType.END_BACKUP_LIST.ordinal());
                worker_writer.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        replicated_worker_handlers.forEach((id, repl) -> {
            System.out.println("Main ID: " + id + " Handler: " + repl);
        });

        System.out.println("All workers connected!");
    }

    public static InetAddress getWifiInetAddress() throws SocketException {
        for (Iterator<NetworkInterface> it = NetworkInterface.getNetworkInterfaces().asIterator(); it.hasNext(); ) {
            NetworkInterface netIf = it.next();
            if (netIf.isUp() && !netIf.isLoopback() && !netIf.isVirtual()) {
                for (InetAddress addr : java.util.Collections.list(netIf.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr;
                    }
                }
            }
        }
        return null;
    }

    void openServer() {
        try {
            InetAddress wifiAddress = getWifiInetAddress();
            System.out.println("Inet Address: " + wifiAddress);
            connection = new ServerSocket(SERVER_CLIENT_PORT, 0, wifiAddress);

            connectReducer();
            System.out.println("Reducer Connected!");
            Utils.Pair<ObjectOutputStream, ObjectInputStream> socket_streams = connectWorkerManager();
            System.out.println("Worker manager connected!");

            initializeWorkers(socket_streams.first, socket_streams.second);
            System.out.println("Workers Initialized!");

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