package com.example.client_efood.ServerSide;

import java.io.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.TimeoutException;

import com.example.client_efood.Domain.Shop;
import com.example.client_efood.Domain.Utils.Pair;
import com.example.client_efood.ManagerSide.ManagerRequestHandler;
import com.example.client_efood.MessagePKG.Message;
import com.example.client_efood.MessagePKG.MessageArgCast;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.ReducerSide.ReducerPreparationType;
import com.example.client_efood.ServerSide.ActiveReplication.ReplicationHandler;
import com.example.client_efood.ClientSide.ClientRequestHandler;
import com.example.client_efood.Workers.Listeners.ReplicationListener;

public class MasterServer {
    public static final long SERVER_ID = Integer.MAX_VALUE - 1;
    public static final int SERVER_CLIENT_PORT = 7777;
    public static String SERVER_HOST = "192.168.1.9";

    private ServerSocket connection = null;
    public static boolean has_manager_connected = false;

    private static ServerConfigInfo config_info;

    public static HashMap<Integer, ReplicationHandler> replicated_worker_handlers = new HashMap<>();


//    public static HashMap<Integer, HashMap<Integer, ReplicationHandler>> batch_replicated_worker_handlers = new HashMap<>();
//    public static HashMap<Integer, HashMap<Integer, ReplicationListener>> batch_worker_listeners = new HashMap<>();

    HashMap<Integer, ArrayList<Shop>> worker_shop_map;

    public static HashMap<Integer, Pair<ObjectOutputStream, ObjectInputStream>> worker_streams = new HashMap<>();
    public static HashMap<Integer, Integer> worker_id_port = new HashMap<>();

    public static HashMap<Integer, Integer> shop_id_hash = new HashMap<>();

    public static HashMap<Integer, ReplicationListener> worker_listeners = new HashMap<>();

    public List<ClientBatch> client_stream_batches = new ArrayList<>();

    public static ReplicationListener reducer_listener = null;
    public static final int REDUCER_ID = Integer.MAX_VALUE;
    public static ObjectOutputStream reducer_writer = null;

    private long client_id = 0;

    MasterServer() throws IOException, URISyntaxException, ClassNotFoundException {

        config_info = ServerFileLoader.load_config();
        ClientBatch.batchSize = config_info.getClient_batch_size();
        System.out.println(config_info);
    }

    static int hashStore(Shop shop) {
        return shop.getId();
    }

    public static void addHashShop(Shop shop) {
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

    HashMap<Integer, ArrayList<Shop>> loadShops() throws IOException, URISyntaxException {
        ArrayList<Shop> database_shops = ServerFileLoader.load_shops();

        HashMap<Integer, ArrayList<Shop>> worker_shop_map = new HashMap<>();
        int worker_count = config_info.getWorker_count();

        for (int i = 0; i < worker_count; ++i)
            worker_shop_map.put(i, new ArrayList<>());

        for (Shop shop : database_shops) {
            int worker_hash = hashStore(shop) % worker_count;
            shop_id_hash.put(shop.getId(), worker_hash);
            worker_shop_map.get(worker_hash).add(shop);
        }

        return worker_shop_map;
    }

    void connectReducer(ObjectOutputStream out, ObjectInputStream in) {

        try {
            reducer_writer = out;

            Message msg = new Message();
            msg.addArgument("prep_ord", new Pair<>(MessageArgCast.INT_ARG, ReducerPreparationType.SET_INFO.ordinal()));
            msg.addArgument("reducer_id", new Pair<>(MessageArgCast.INT_ARG, REDUCER_ID));
            msg.addArgument("worker_count", new Pair<>(MessageArgCast.INT_ARG, config_info.getWorker_count()));

            reducer_writer.writeObject(msg);
            reducer_writer.flush();

            reducer_listener = new ReplicationListener(in);
            reducer_listener.setId(REDUCER_ID);
            reducer_listener.start();

            System.out.println("Reducer Connected!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void createWorkerSchema(){
        for(int i = 0; i < config_info.getWorker_count(); ++i){
            ReplicationHandler rh = new ReplicationHandler();
            rh.clearData();
            rh.setId(i);
            rh.setMainId(i);

            for(int j = 1; j <= config_info.get_number_of_replicas(); ++j){
                rh.add_replica((i + j) % config_info.getWorker_count());
            }
            replicated_worker_handlers.put(i, rh);
        }
    }

    private ClientBatch createOrGetBatch() throws IOException {
        for(ClientBatch cb: client_stream_batches){
            if(cb.canAcceptClient())
                return cb;
        }
        ClientBatch new_cb = new ClientBatch();
        new_cb.initializeBatch();
        client_stream_batches.add(new_cb);
        return new_cb;
    }

    private void connectWorker(ObjectOutputStream newOut, ObjectInputStream newIn) throws IOException, InterruptedException {
        int assignedId = -1;

        for (Map.Entry<Integer, Pair<ObjectOutputStream, ObjectInputStream>> e : worker_streams.entrySet()) {
            int workerId = e.getKey();
            ObjectOutputStream out = e.getValue().first;
            ObjectInputStream in = e.getValue().second;
            ReplicationListener oldListener = worker_listeners.get(workerId);
            RequestMonitor monitor = new RequestMonitor();

            oldListener.registerMonitor(SERVER_ID, workerId, monitor);

            Message ping = new Message();
            ping.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.IS_WORKER_ALIVE.ordinal()));
            ping.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, workerId));
            ping.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, SERVER_ID));

            try {
                synchronized (out) {
                    out.reset();
                    out.writeObject(ping);
                    out.flush();
                }

                Boolean alive = monitor.getResult(3_000);
                if (!alive) {
                    assignedId = workerId;
                    System.out.println("Reclaiming slot for dead worker ID " + workerId);
                    break;
                }
            } catch (IOException | TimeoutException | InterruptedException ex) {
                System.err.println("Worker " + workerId + " unresponsive: " + ex.getMessage());
                assignedId = workerId;
                break;
            }
        }

        if (assignedId < 0) {
            assignedId = worker_streams.isEmpty()
                    ? 0
                    : Collections.max(worker_streams.keySet()) + 1;
        } else {

            Pair<ObjectOutputStream, ObjectInputStream> oldStreams = worker_streams.remove(assignedId);
            safeClose(oldStreams.first);
            safeClose(oldStreams.second);

            ReplicationListener oldL = worker_listeners.remove(assignedId);
            oldL.shutdown();
        }

        int worker_port = config_info.getWorkerPort(assignedId);

        try {
            newOut.reset();
            newOut.writeInt(assignedId);
            newOut.writeInt(worker_port);
            ArrayList<Shop> shops = worker_shop_map.get(assignedId);
            System.out.println("To be sent: " + shops);
            newOut.writeObject(shops);
            newOut.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Pair<ObjectOutputStream, ObjectInputStream> new_streams = new Pair<>(newOut, newIn);
        worker_streams.put(assignedId, new_streams);
        worker_id_port.put(assignedId, worker_port);

        newIn.readBoolean();

        newOut.writeBoolean(true);
        newOut.flush();

        ReplicationListener listener = new ReplicationListener(newIn);
        listener.setId(assignedId);
        worker_listeners.put(assignedId, listener);
        listener.start();

        for(ClientBatch cb: client_stream_batches){
            cb.setNewWorkerStreams(assignedId, worker_port);
        }

        System.out.println("Worker connected under ID " + assignedId);

        int workerCount = config_info.getWorker_count();
        int numReplicas = config_info.get_number_of_replicas();

        ArrayList<Shop> shopList = worker_shop_map.get(assignedId);

        for (int j = 1; j <= numReplicas; ++j) {

            int fallbackId = (assignedId + j) % workerCount;
            if (fallbackId == assignedId)
                break;

            Pair<ObjectOutputStream, ObjectInputStream> fallbackStreams =
                    worker_streams.get(fallbackId);
            if (fallbackStreams == null)
                continue;

            ObjectOutputStream fallbackOut = fallbackStreams.first;
            ObjectInputStream fallbackIn = fallbackStreams.second;
            if (fallbackOut == null || fallbackIn == null)
                continue;

            Message m = new Message();
            m.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_BACKUP.ordinal()));
            m.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, fallbackId));
            m.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, SERVER_ID));
            m.addArgument("worker_backup_id", new Pair<>(MessageArgCast.INT_ARG, assignedId));
            m.addArgument("shop_list", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, shopList));

            synchronized (fallbackOut) {
                fallbackOut.reset();
                fallbackOut.writeObject(m);
                fallbackOut.flush();
            }
        }

        Pair<ObjectOutputStream, ObjectInputStream> assigned_streams =
                worker_streams.get(assignedId);

        ObjectOutputStream assigned_out = assigned_streams.first;
        ObjectInputStream assigned_in = assigned_streams.second;

        for (int j = 1; j <= numReplicas; ++j) {

            int fallbackId = (assignedId - j + workerCount) % workerCount;
            if (fallbackId == assignedId)
                break;

            if (assigned_out == null || assigned_in == null)
                continue;

            ArrayList<Shop> shop_list = worker_shop_map.get(fallbackId);

            Message m = new Message();
            m.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_BACKUP.ordinal()));
            m.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, assignedId));
            m.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, SERVER_ID));
            m.addArgument("worker_backup_id", new Pair<>(MessageArgCast.INT_ARG, fallbackId));
            m.addArgument("shop_list", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, shop_list));

            synchronized (assigned_out) {
                assigned_out.reset();
                assigned_out.writeObject(m);
                assigned_out.flush();
            }
        }

        for (int j = 1; j <= numReplicas; ++j) {

            int fallbackId = (assignedId + j) % workerCount;
            if (fallbackId == assignedId)
                break;

            Pair<ObjectOutputStream, ObjectInputStream> fallbackStreams =
                    worker_streams.get(fallbackId);
            if (fallbackStreams == null)
                continue;

            ObjectOutputStream fallbackOut = fallbackStreams.first;
            ObjectInputStream fallbackIn = fallbackStreams.second;
            if (fallbackOut == null || fallbackIn == null)
                continue;

            Message sync_message = new Message();
            sync_message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_CHANGES.ordinal()));
            sync_message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, assignedId));
            sync_message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, SERVER_ID));
            sync_message.addArgument("replica_port", new Pair<>(MessageArgCast.INT_ARG, worker_id_port.get(fallbackId)));

            synchronized (assigned_out) {
                assigned_out.reset();
                assigned_out.writeObject(sync_message);
                assigned_out.flush();
            }
        }


        System.out.println("New worker schema: {");
        replicated_worker_handlers.values().forEach(System.out::println);
        System.out.println("}");
    }

    private void safeClose(Closeable c) {
        try {
            c.close();
        } catch (IOException ignored) {
        }
    }

    void acceptConnection(Socket server_socket){
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
            try {
                server_socket.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        assert in != null;
        int connection_type_ord;
        try {
            connection_type_ord = in.readInt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ConnectionType connection_type = ConnectionType.values()[connection_type_ord];
        System.out.println("New connection with type: " + connection_type);

        Thread t = null;

        final ObjectOutputStream f_out = out;
        final ObjectInputStream f_in = in;

        if (connection_type == ConnectionType.CLIENT) {
            try {
                ClientRequestHandler client_handler = new ClientRequestHandler(server_socket, f_out, f_in, client_id++);
                ClientBatch cb = createOrGetBatch();

                cb.addClient(client_handler);

                t = client_handler;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else if (connection_type == ConnectionType.MANAGER && !has_manager_connected) {
            try {
                t = new ManagerRequestHandler(server_socket, f_out, f_in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            has_manager_connected = true;
        } else if (connection_type == ConnectionType.WORKER) {

            t = new Thread(() -> {
                try {
                    connectWorker(f_out, f_in);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        } else if (connection_type == ConnectionType.REDUCER) {

            t = new Thread(() -> connectReducer(f_out, f_in));
        }

        assert t != null;
        t.start();
    }

    void openServer() {
        try {
            createWorkerSchema();
            worker_shop_map = loadShops();
            connection = new ServerSocket(SERVER_CLIENT_PORT);

            System.out.println("Server started at: " + connection);

            while (true) {
                Socket server_socket = connection.accept();
                System.out.println(server_socket);

                (new Thread(() -> acceptConnection(server_socket))).start();
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        } catch (URISyntaxException e) {
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