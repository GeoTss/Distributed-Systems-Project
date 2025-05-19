package org.ReducerSide;

import org.Domain.Shop;
import org.Domain.Utils.Pair;
import org.MessagePKG.Message;
import org.ServerSide.ConnectionType;
import org.ServerSide.MasterServer;
import org.ServerSide.RequestMonitor;
import org.Workers.Listeners.ReplicationListener;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;

public class Reducer{

    ServerSocket red_server_socket;

    public static String REDUCER_HOST = "127.0.0.1";

    private int id;
    private int managing_workers_count = 0;
    public static final int REDUCER_WORKER_PORT = 7555;

    HashMap<Integer, ReplicationListener> worker_listeners = new HashMap<>();

    ObjectOutputStream server_output_stream;
    ObjectInputStream server_input_stream;

    private void connectToServer() {
        try {
            System.out.println("Trying to connect with server...");
            Socket request_socket = new Socket(MasterServer.SERVER_HOST, MasterServer.SERVER_CLIENT_PORT);

            System.out.println("Server accepted the connection request.");

            server_output_stream = new ObjectOutputStream(request_socket.getOutputStream());
            server_input_stream = new ObjectInputStream(request_socket.getInputStream());

            server_output_stream.writeInt(ConnectionType.REDUCER.ordinal());
            server_output_stream.flush();

//            Message msg = (Message) server_input_stream.readObject();
//
//            id = msg.getArgument("reducer_id");
//            managing_workers_count = msg.getArgument("worker_count");
//
//            System.out.println("Reducer's id: " + id);
//            System.out.println("Reducer manages " + managing_workers_count + " workers.");
//            System.out.println("Reducer connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void sendToServer(long request_id, Object result) throws IOException {
        synchronized (server_output_stream) {
            server_output_stream.reset();
            server_output_stream.writeInt(id);
            server_output_stream.writeLong(request_id);
            server_output_stream.writeObject(result);
            server_output_stream.flush();
        }
    }

    private ArrayList<RequestMonitor> prepareListeners(long request_id, ArrayList<Pair<Integer, Integer>> workers_sent_to){

        System.out.println(workers_sent_to);

        ArrayList<RequestMonitor> result_monitors = new ArrayList<>();
        for(Pair<Integer, Integer> worker_sent_to: workers_sent_to){

            synchronized (System.out) {
                System.out.println("Handling " + worker_sent_to);
                System.out.println("Request: " + request_id + "Registering monitor in listener " + worker_sent_to.second + " for worker " + worker_sent_to.first);
            }

            RequestMonitor monitor = new RequestMonitor();
            ReplicationListener listener = worker_listeners.get(worker_sent_to.second);
            synchronized (listener) {
                monitor = listener.registerMonitor(request_id, worker_sent_to.first, monitor);
            }
            result_monitors.add(monitor);
        }
        System.out.println("Added listeners");

        return result_monitors;
    }

    private void handlePreparation(Message msg) throws InterruptedException, IOException {

        int preparation_ord = msg.getArgument("prep_ord");

        ReducerPreparationType preparation = ReducerPreparationType.values()[preparation_ord];
        System.out.println("Got " + preparation);

        switch (preparation){

            case SET_INFO -> {
                id = msg.getArgument("reducer_id");
                managing_workers_count = msg.getArgument("worker_count");

                System.out.println("Reducer's id: " + id);
                System.out.println("Reducer manages " + managing_workers_count + " workers.");
                System.out.println("Reducer connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);
            }

            case REDUCER_PREPARE_FILTER -> {

                long request_id = msg.getArgument("request_id");
                ArrayList<Pair<Integer, Integer>> workers_sent_to = msg.getArgument("workers_sent_to");

                ArrayList<RequestMonitor> result_monitors = prepareListeners(request_id, workers_sent_to);

                System.out.println("Waiting for all workers to send results...");
                ArrayList<Shop> resulting_shops = new ArrayList<>();
                for(RequestMonitor monitor: result_monitors){
                    @SuppressWarnings("unchecked")
                    ArrayList<Shop> shops = (ArrayList<Shop>) monitor.getResult();
                    resulting_shops.addAll(shops);
                }
                System.out.println("Received results from all shops.");

                sendToServer(request_id, resulting_shops);
            }

            case REDUCER_PREPARE_SHOP_CATEGORY_SALES, REDUCER_PREPARE_PRODUCT_CATEGORY_SALES -> {

                long request_id = msg.getArgument("request_id");
                ArrayList<Pair<Integer, Integer>> workers_sent_to = msg.getArgument("workers_sent_to");

                ArrayList<RequestMonitor> result_monitors = prepareListeners(request_id, workers_sent_to);

                Pair<ArrayList<Pair<String, Integer>>, Integer> result = new Pair<>(new ArrayList<>(), 0);

                for(RequestMonitor monitor: result_monitors){
                    @SuppressWarnings("unchecked")
                    ArrayList<Pair<String, Integer>> sales = (ArrayList<Pair<String, Integer>>) monitor.getResult();
                    result.first.addAll(sales);
                }
                System.out.println("Received results from all shops.");

                result.second = result.first.stream()
                                .map(Pair::getSecond)
                                .reduce(0, Integer::sum);

                sendToServer(request_id, result);
            }
        }
    }

    private void close() throws IOException {
        server_output_stream.close();
        server_input_stream.close();
    }

    private void acceptConnections() {
        try {
            final ServerSocket red_server_socket = new ServerSocket(REDUCER_WORKER_PORT);

            while(true){
                Socket worker_socket = red_server_socket.accept();

                ObjectOutputStream worker_out = new ObjectOutputStream(worker_socket.getOutputStream());
                ObjectInputStream worker_in = new ObjectInputStream(worker_socket.getInputStream());

                Message msg = (Message) worker_in.readObject();

                int listener_id = msg.getArgument("worker_id");
                System.out.println("Worker with id " + listener_id + " connected.");
                ReplicationListener worker_listener = new ReplicationListener(worker_in);
                worker_listener.setId(listener_id);

                worker_listeners.put(listener_id, worker_listener);

                worker_listener.start();
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void openReducer() throws SocketException {

        connectToServer();

        try {

            (new Thread(this::acceptConnections)).start();

            while (true) {

                Message msg = (Message) server_input_stream.readObject();

                new Thread(() -> {
                    try {
                        handlePreparation(msg);
                    }catch (IOException | InterruptedException e){
                        e.printStackTrace();
                    }
                }).start();
            }
        }catch (IOException e){
            e.printStackTrace();
            System.out.println("Trying to close the listening input stream...");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Streams closed.");
    }

    public static void main(String[] args) throws SocketException {
        System.out.println("Starting reducer program.");
        new Reducer().openReducer();
    }
}
