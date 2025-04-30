package org.ReducerSide;

import org.Domain.Shop;
import org.Domain.Utils;
import org.ServerSide.MasterServer;
import org.ServerSide.RequestMonitor;
import org.Workers.Listeners.ReplicationListener;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class Reducer{

    private int id;
    private int managing_workers_count = 0;
    public static final int REDUCER_WORKER_PORT = 7555;
    public static final String REDUCER_HOST = "127.0.0.1";

    HashMap<Integer, ReplicationListener> worker_listeners = new HashMap<>();

    ObjectOutputStream server_output_stream;
    ObjectInputStream server_input_stream;

    private void connectToServer() {
        try {
            Socket request_socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);

            server_output_stream = new ObjectOutputStream(request_socket.getOutputStream());
            server_input_stream = new ObjectInputStream(request_socket.getInputStream());

            id = server_input_stream.readInt();
            managing_workers_count = server_input_stream.readInt();
            System.out.println("Reducer's id: " + id);
            System.out.println("Reducer manages " + managing_workers_count + " workers.");
            System.out.println("Reducer connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void connectWithWorkers() throws IOException {
        ServerSocket red_server_socket = new ServerSocket(REDUCER_WORKER_PORT);

        int worker_count = 0;
        int command_ord = server_input_stream.readInt();
        ReducerPreparationType command = ReducerPreparationType.values()[command_ord];

        while (command != ReducerPreparationType.REDUCER_END_OF_WORKERS && worker_count < managing_workers_count) {
            Socket worker_socket = red_server_socket.accept();

            ObjectOutputStream worker_out = new ObjectOutputStream(worker_socket.getOutputStream());
            ObjectInputStream worker_in = new ObjectInputStream(worker_socket.getInputStream());

            int listener_id = worker_in.readInt();
            System.out.println("Worker with id " + listener_id + " connected.");
            ReplicationListener worker_listener = new ReplicationListener(worker_in);
            worker_listener.setId(listener_id);

            worker_listeners.put(listener_id, worker_listener);

            worker_listener.start();

            command_ord = server_input_stream.readInt();
            command = ReducerPreparationType.values()[command_ord];

            if(!(command == ReducerPreparationType.REDUCER_ADD_WORKER_CONNECTION))
                break;
        }
    }

    private void sendToServer(long request_id, Object result) throws IOException {
        server_output_stream.writeInt(id);
        server_output_stream.writeLong(request_id);
        server_output_stream.writeObject(result);
        server_output_stream.flush();
    }

    private void handlePreparation(long request_id, ReducerPreparationType preparation, Object extra_args) throws InterruptedException, IOException {
        switch (preparation){
            case REDUCER_PREPARE_FILTER -> {

                @SuppressWarnings("unchecked")
                ArrayList<Utils.Pair<Integer, Integer>> workers_sent_to = (ArrayList<Utils.Pair<Integer, Integer>>) extra_args;
                System.out.println(workers_sent_to);

                ArrayList<RequestMonitor> result_monitors = new ArrayList<>();
                for(Utils.Pair<Integer, Integer> worker_sent_to: workers_sent_to){

                    synchronized (System.out) {
                        System.out.println("Handling " + worker_sent_to);
                        System.out.println("Registering monitor in listener " + worker_sent_to.second + " for worker " + worker_sent_to.first);
                    }

                    RequestMonitor monitor = new RequestMonitor();
                    ReplicationListener listener = worker_listeners.get(worker_sent_to.second);
                    synchronized (listener) {
                        monitor = listener.registerMonitor(request_id, worker_sent_to.first, monitor);
                    }
                    result_monitors.add(monitor);
                }
                System.out.println("Added listeners");

                ArrayList<Shop> resulting_shops = new ArrayList<>();
                for(RequestMonitor monitor: result_monitors){
                    @SuppressWarnings("unchecked")
                    ArrayList<Shop> shops = (ArrayList<Shop>) monitor.getResult();
                    resulting_shops.addAll(shops);
                }

                sendToServer(request_id, resulting_shops);
            }
        }
    }

    private void close() throws IOException {
        server_output_stream.close();
        server_input_stream.close();
    }

    public void openReducer(){

        connectToServer();

        try {
            connectWithWorkers();

            while (true) {
                long request_id;
                int preparation_ord;
                Object extra_args;

                synchronized (server_input_stream) {
                    request_id = server_input_stream.readLong();
                    preparation_ord = server_input_stream.readInt();
                    extra_args = server_input_stream.readObject();
                }

                ReducerPreparationType preparation = ReducerPreparationType.values()[preparation_ord];

                handlePreparation(request_id, preparation, extra_args);
            }
        }catch (IOException | InterruptedException e){
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

    public static void main(String[] args) {
        new Reducer().openReducer();
    }
}
