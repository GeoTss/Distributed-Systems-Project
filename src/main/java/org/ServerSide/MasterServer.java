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

    private int numberOfWorkers;

    private ArrayList<WorkerHandler> workerHandlers = new ArrayList<>();
    private ArrayList<ReplicationHandler> replicated_worker_handlers = new ArrayList<>();

    public static final Object CONNECTION_ACCEPT_LOCK = new Object();

    MasterServer() throws IOException, URISyntaxException, ClassNotFoundException {
        database_shops = new ArrayList<>();

        config_info = ServerFileLoader.load_config();
        database_shops = ServerFileLoader.load_shops();

        System.out.println(config_info);
    }

    void initializeWorkers() throws IOException, ClassNotFoundException, InterruptedException {

        int worker_chunk_size = config_info.getWorker_chunk();

        for(int i = 0; i < database_shops.size(); i += worker_chunk_size){
            List<Shop> shop_chunk = database_shops.subList(i, Math.min(i+worker_chunk_size, database_shops.size()));

            ReplicationHandler handler = new ReplicationHandler();

            Thread t = new WorkerClient(shop_chunk);
            t.start();

            Socket main_worker_socket;
            synchronized (CONNECTION_ACCEPT_LOCK) {
                CONNECTION_ACCEPT_LOCK.wait();
                main_worker_socket = connection.accept();
            }


            ObjectOutputStream out = new ObjectOutputStream(main_worker_socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(main_worker_socket.getInputStream());

            WorkerHandler main_handler = new WorkerHandler(out, in);
            handler.setMain(main_handler);

            for (int j = 0; j < config_info.getNumber_of_replicas(); ++j) {
                Thread t_rep = new WorkerClient(shop_chunk);
                t_rep.start();

                Socket rep_worker;
                synchronized (CONNECTION_ACCEPT_LOCK) {
                    CONNECTION_ACCEPT_LOCK.wait();
                    rep_worker = connection.accept();
                }
                ObjectOutputStream rep_out = new ObjectOutputStream(rep_worker.getOutputStream());
                ObjectInputStream rep_in = new ObjectInputStream(rep_worker.getInputStream());

                WorkerHandler rep_handler = new WorkerHandler(rep_out, rep_in);
                handler.add_replica(rep_handler);
            }

            replicated_worker_handlers.add(handler);
            handler.getMain().start();
        }
        System.out.println("All workers connected!");
    }

    void acceptWorkers(int numberOfWorkers) throws IOException {
        System.out.println("Waiting for " + numberOfWorkers + " workers to connect...");

        for (int i = 0; i < numberOfWorkers; i++) {
            Socket workerSocket = connection.accept();
            System.out.println("Worker connected: " + workerSocket);

            ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream());

            WorkerHandler handler = new WorkerHandler(out, in);
            workerHandlers.add(handler);
            handler.start();
        }

        System.out.println("All workers connected!");
    }


    void openServer() {
        try {
            connection = new ServerSocket(SERVER_CLIENT_PORT);
            System.out.println("SERVER STARTED");

            initializeWorkers();
//            acceptWorkers(numberOfWorkers);

//            ClientRequestHandler.worker_handlers = workerHandlers;
            ClientRequestHandler.replicated_worker_handlers = replicated_worker_handlers;

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

