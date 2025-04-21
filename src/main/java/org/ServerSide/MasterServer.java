package org.ServerSide;

import java.io.IOException;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.Domain.Shop;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ServerSide.ClientRequests.ClientRequestHandler;
import org.Workers.WorkerClient;
import org.Workers.WorkerHandler;

public class MasterServer {
    public static final int SERVER_CLIENT_PORT = 7777;
    public static final String SERVER_LOCAL_HOST = "127.0.0.1";

    private ServerSocket connection = null;
    private Socket server_socket = null;

    private ArrayList<Shop> database_shops;
    private ServerConfigInfo config_info;

    private ArrayList<ReplicationHandler> replicated_worker_handlers = new ArrayList<>();

    public static final Object CONNECTION_ACCEPT_LOCK = new Object();

    MasterServer() throws IOException, URISyntaxException, ClassNotFoundException {
        database_shops = new ArrayList<>();

        config_info = ServerFileLoader.load_config();
        database_shops = ServerFileLoader.load_shops();

        System.out.println(config_info);
    }

    void initializeWorkers() throws IOException, ClassNotFoundException, InterruptedException {

        ArrayList<List<Shop>> shop_chunks = new ArrayList<>();
        ArrayList<WorkerClient> worker_clients = new ArrayList<>();
        ArrayList<WorkerHandler> worker_handlers = new ArrayList<>();

        int worker_chunk_size = config_info.getWorker_chunk();

        for(int i = 0; i < database_shops.size(); i += worker_chunk_size) {
            List<Shop> chunk = database_shops.subList(i, Math.min(i + worker_chunk_size, database_shops.size()));
            shop_chunks.add(chunk);
        }

        for (List<Shop> shopChunk : shop_chunks) {

            WorkerClient cl = new WorkerClient(shopChunk);
            worker_clients.add(cl);
            cl.start();

            Socket main_worker_socket;
            synchronized (CONNECTION_ACCEPT_LOCK) {
                CONNECTION_ACCEPT_LOCK.wait();
                main_worker_socket = connection.accept();
            }

            ObjectOutputStream out = new ObjectOutputStream(main_worker_socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(main_worker_socket.getInputStream());

            WorkerHandler main_handler = new WorkerHandler(out, in);
            worker_handlers.add(main_handler);
        }

        int n = worker_clients.size();

        for (int i = 0; i < worker_clients.size(); i++) {
            ReplicationHandler handler = new ReplicationHandler();
            handler.setMain(worker_handlers.get(i));

            for (int j = 1; j <= config_info.getNumber_of_replicas(); j++) {
                int fallback_index = (i+j) % n;

                worker_clients.get(fallback_index).add_backup(i, shop_chunks.get(i));

                WorkerHandler fall_worker = worker_handlers.get(fallback_index);
                System.out.println("At worker client " + i + " put: (" + fall_worker.getHandlerId() + ", " + fallback_index + " chunk)");

                handler.getReplicas().add(fall_worker);
            }
            worker_handlers.get(i).start();
            replicated_worker_handlers.add(handler);
        }

        for(WorkerClient client: worker_clients){
            System.out.println(client);
        }

        for(ReplicationHandler repl_worker: replicated_worker_handlers){
            System.out.println(repl_worker);
        }
        System.out.println("All workers connected!");
    }

    void openServer() {
        try {
            connection = new ServerSocket(SERVER_CLIENT_PORT);

            initializeWorkers();

            ClientRequestHandler.replicated_worker_handlers = replicated_worker_handlers;

            System.out.println("SERVER STARTED");
            while (true) {
                server_socket = connection.accept();
                System.out.println(server_socket);

                Thread t = new ClientRequestHandler(server_socket);
                t.start();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (ClassNotFoundException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                connection.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException, URISyntaxException, ClassNotFoundException {
        new MasterServer().openServer();
    }
}

