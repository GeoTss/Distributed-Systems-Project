package com.example.client_efood.ManagerSide;

import com.example.client_efood.Domain.Manager;
import com.example.client_efood.Domain.Product;
import com.example.client_efood.Domain.Shop;
import com.example.client_efood.Filters.Filter;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.ReducerSide.ReducerPreparationType;
import com.example.client_efood.ServerSide.ActiveReplication.ReplicationHandler;
import com.example.client_efood.ServerSide.ThrowingConsumer;
import com.example.client_efood.ServerSide.RequestMonitor;
import com.example.client_efood.Workers.Listeners.ReplicationListener;
import com.example.client_efood.Domain.Utils.Pair;
import com.example.client_efood.MessagePKG.Message;
import com.example.client_efood.MessagePKG.MessageArgCast;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import static com.example.client_efood.ServerSide.MasterServer.worker_listeners;
import static com.example.client_efood.ServerSide.MasterServer.replicated_worker_handlers;
import static com.example.client_efood.ServerSide.MasterServer.REDUCER_ID;
import static com.example.client_efood.ServerSide.MasterServer.reducer_listener;
import static com.example.client_efood.ServerSide.MasterServer.reducer_writer;
import static com.example.client_efood.ServerSide.MasterServer.getWorkerForShop;
import static com.example.client_efood.ServerSide.MasterServer.addHashShop;
import static com.example.client_efood.ServerSide.MasterServer.has_manager_connected;


public class ManagerRequestHandler extends Thread {

    private final ObjectInputStream in;

    private final ObjectOutputStream out;
    private final Socket connection;

    private Manager manager;

    int current_shop_id;

    public ManagerRequestHandler(Socket connection, ObjectOutputStream out, ObjectInputStream in) throws IOException {
        this.connection = connection;
        this.out = out;
        this.in = in;
    }

    private int sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic, RequestMonitor monitor, long request_id, int worker_id) {
        ReplicationListener handler = null;
        int main_id = replicatedWorker.getId();
        try {
            ObjectOutputStream main_worker_writer = replicatedWorker.getMain();
            if(main_worker_writer == null)
                throw new NullPointerException();

            handler = worker_listeners.get(main_id);
            monitor = handler.registerMonitor(request_id, worker_id, monitor);

            synchronized (main_worker_writer) {
                write_logic.accept(main_worker_writer);
                main_worker_writer.flush();
            }

            return main_id;
        } catch (IOException | NullPointerException e) {
            if (handler != null)
                handler.unregisterMonitor(request_id, worker_id);

            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                try {
                    ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);
                    if(replica_writer == null)
                        continue;

                    handler = worker_listeners.get(replica_id);
                    monitor = handler.registerMonitor(request_id, worker_id, monitor);

                    synchronized (replica_writer) {
                        write_logic.accept(replica_writer);
                        replica_writer.flush();
                    }

                    return main_id;
                } catch (IOException ex) {

                    handler.unregisterMonitor(request_id, worker_id);
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }

        return -1;
    }

    private int sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic, long request_id, int worker_id) {

        int main_id = replicatedWorker.getMainId();
        try {
//            throw new IOException();
            ObjectOutputStream main_worker_writer = replicatedWorker.getMain();
            synchronized (main_worker_writer) {
                write_logic.accept(main_worker_writer);
                main_worker_writer.flush();
            }
            return main_id;
        } catch (IOException e) {

            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                try {

                    ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);
                    synchronized (replica_writer) {
                        write_logic.accept(replica_writer);
                        replica_writer.flush();
                    }

                    synchronized (replicatedWorker){
                        replicatedWorker.promoteToMain(replica_id);
                    }
                    return replica_id;
                } catch (IOException ex) {
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }
        return -1;
    }

    private Pair<Integer, Integer> sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic) {
        try {
            ObjectOutputStream main_worker_writer = replicatedWorker.getMain();
            if(main_worker_writer == null)
                throw new NullPointerException();

            synchronized (main_worker_writer) {
                write_logic.accept(main_worker_writer);
                main_worker_writer.flush();
            }

            return new Pair<>(replicatedWorker.getId(), replicatedWorker.getMainId());
        } catch (IOException | NullPointerException e) {
            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                try {
                    ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);
                    if(replica_writer == null){
                        System.out.println("There isn't a connection for worker with id: " + replica_id);
                        continue;
                    }

                    synchronized (replica_writer) {
                        write_logic.accept(replica_writer);
                        replica_writer.flush();
                    }

                    return new Pair<>(replicatedWorker.getId(), replica_id);
                } catch (IOException ex) {
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }

        return null;
    }

    void syncReplicas(ReplicationHandler replicated_worker, ThrowingConsumer<ObjectOutputStream> write_logic) throws IOException{
        for(ObjectOutputStream replica_writer: replicated_worker.getReplicasOutputs()){
            write_logic.accept(replica_writer);
            replica_writer.flush();
        }
    }

    public void handleCommand(MessageType cmd)
            throws IOException, ClassNotFoundException, InterruptedException {
        long requestId = threadId();
        switch (cmd) {
            case QUIT, DEFAULT, END -> {}
            case GET_SHOPS -> handleGetShops(requestId);
            case CHOSE_SHOP -> handleChooseShop(requestId);
            case ADD_SHOP -> handleAddShop(requestId);
            case ADD_OLD_PRODUCT_TO_SHOP -> handleAddOldProduct(requestId);
            case ADD_PRODUCT_TO_SHOP -> handleAddProduct(requestId);
            case REMOVE_PRODUCT_FROM_SHOP -> handleRemoveProduct(requestId);
            case ADD_PRODUCT_STOCK -> handleAddAvailableProduct(requestId);
            case REMOVE_PRODUCT_STOCK -> handleRemoveAvailableProduct(requestId);
            case GET_SHOP_CATEGORY_SALES -> handleGetShopCategorySales(requestId);
            case GET_PRODUCT_CATEGORY_SALES -> handleGetProductCategorySales(requestId);
        }
    }

    private void handleAddOldProduct(long requestId) throws IOException {

        int productId = in.readInt();

        ReplicationHandler handler = getWorkerForShop(current_shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_OLD_PRODUCT_TO_SHOP.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, handler.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("product_id", new Pair<>(MessageArgCast.INT_ARG, productId));

        RequestMonitor monitor = new RequestMonitor();

        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.reset();
            wout.writeObject(message);
        };

        int sent_to = sendToWorkerWithReplicas(handler, writer, requestId, handler.getId());

        boolean success = false;
        if(sent_to != -1){
            success = true;
            System.out.println("Product was removed successfully");

            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_ADD_OLD_PRODUCT_TO_SHOP.ordinal()));

            writer = (wout) -> {
                wout.reset();
                wout.writeObject(message);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(handler, writer);
            System.out.println("Replicas synced.");
        }
        out.writeBoolean(success);
        out.flush();
    }

    private void handleChooseShop(long requestId) throws IOException, InterruptedException {
        int shop_id = in.readInt();

        ReplicationHandler replicated_worker = getWorkerForShop(shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.CHOSE_SHOP.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, shop_id));

        ThrowingConsumer<ObjectOutputStream> chose_shop_writer = (out) -> {
            out.reset();
            out.writeObject(message);
        };

        RequestMonitor monitor = new RequestMonitor();
        sendToWorkerWithReplicas(replicated_worker, chose_shop_writer, monitor, requestId, replicated_worker.getId());

        System.out.println("Waiting for shop to return...");
        Shop result = (Shop) monitor.getResult();
        current_shop_id = result.getId();

        System.out.println("Sending shop to client." + result);
        out.writeObject(result);
        out.flush();
    }

    private void handleGetShops(long requestId) throws IOException, InterruptedException {

        ArrayList<Pair<Integer, Integer>> workers_sent_to = new ArrayList<>();

        ArrayList<Filter> empty_arr = new ArrayList<>();

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.FILTER.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("filter_list", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, empty_arr));

        for(ReplicationHandler handler: replicated_worker_handlers.values()) {

            message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, handler.getId()));

            ThrowingConsumer<ObjectOutputStream> empty_filter_writer = (out) -> {
                out.reset();
                out.writeObject(message);
            };

            Pair<Integer, Integer> worker_sent_to = sendToWorkerWithReplicas(handler, empty_filter_writer);

            if(worker_sent_to != null)
                workers_sent_to.add(worker_sent_to);
        }
        System.out.println(workers_sent_to);

        RequestMonitor reducer_monitor = new RequestMonitor();
        synchronized (reducer_listener){
            reducer_monitor = reducer_listener.registerMonitor(requestId, REDUCER_ID, reducer_monitor);
        }

        Message red_msg = new Message();
        red_msg.addArgument("prep_ord", new Pair<>(MessageArgCast.INT_ARG, ReducerPreparationType.REDUCER_PREPARE_FILTER.ordinal()));
        red_msg.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        red_msg.addArgument("workers_sent_to", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, workers_sent_to));

        synchronized (reducer_writer){
            reducer_writer.reset();
            reducer_writer.writeObject(red_msg);
            reducer_writer.flush();
        }

        System.out.println("Waiting for filtered shops from reducer for " + requestId + "...");
        @SuppressWarnings("unchecked")
        ArrayList<Shop> resulting_shops = (ArrayList<Shop>) reducer_monitor.getResult();
        System.out.println("Received filtered shops for " + requestId);

        out.writeObject(resulting_shops);
        out.flush();
    }

    private void handleAddShop(long requestId)
            throws IOException {
        String name = in.readUTF();
        double latitude = in.readDouble();
        double longitude = in.readDouble();
        String foodCategory = in.readUTF();
        float initialStars = in.readFloat();
        int initialVotes = in.readInt();
        String logoPath = in.readUTF();

        Shop new_shop = new Shop(name, latitude, longitude, foodCategory, initialStars, initialVotes, logoPath);
        int shop_id = new_shop.getId();

        addHashShop(new_shop);

        ReplicationHandler responsible_worker = getWorkerForShop(shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_SHOP.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, responsible_worker.getId()));
        message.addArgument("new_shop", new Pair<>(MessageArgCast.SHOP_ARG, new_shop));


        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.reset();
            wout.writeObject(message);
        };

        System.out.println("New shop ID: " + shop_id);

        int sent_to = sendToWorkerWithReplicas(responsible_worker, writer, requestId, responsible_worker.getId());

        boolean success = false;
        if(sent_to != -1){
            success = true;
            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_SHOP.ordinal()));
            message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
            message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, responsible_worker.getId()));
            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_ADD_SHOP.ordinal()));

            ThrowingConsumer<ObjectOutputStream> shop_sync_writer = (wout) -> {
                wout.reset();
                wout.writeObject(message);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(responsible_worker, shop_sync_writer);
            System.out.println("Replicas synced");
        }

        out.writeBoolean(success);
        out.flush();
    }

    private void handleAddProduct(long requestId)
            throws IOException {

        String name = in.readUTF();
        String type = in.readUTF();
        int availableAmount = in.readInt();
        float price = in.readFloat();

        Product new_product = new Product(name, type, availableAmount, price);


        ReplicationHandler responsible_worker = getWorkerForShop(current_shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_PRODUCT_TO_SHOP.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, responsible_worker.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("new_product", new Pair<>(MessageArgCast.PRODUCT_ARG, new_product));

        ThrowingConsumer<ObjectOutputStream> productWriter = (wout) -> {
            wout.reset();
            wout.writeObject(message);
        };

        int sent_to = sendToWorkerWithReplicas(responsible_worker, productWriter, requestId, responsible_worker.getId());

        boolean success = false;
        if(sent_to != -1){
            success = true;
            if(sent_to != responsible_worker.getId())
                System.out.println("Added new product " + new_product + " to backup worker " + sent_to);
            else
                System.out.println("Added new product " + new_product + " to worker " + sent_to);


            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_ADD_PRODUCT_TO_SHOP.ordinal()));
            ThrowingConsumer<ObjectOutputStream> product_sync_writer = (wout) -> {
                wout.reset();
                wout.writeInt(MessageType.SYNC_ADD_PRODUCT_TO_SHOP.ordinal());
                wout.writeLong(requestId);
                wout.writeInt(responsible_worker.getId());
                wout.writeObject(message);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(responsible_worker, product_sync_writer);
            System.out.println("Replicas synced");
        }

        out.writeBoolean(success);
        out.flush();
    }

    private void handleRemoveProduct(long requestId) throws IOException {

        int productId = in.readInt();

        ReplicationHandler handler = getWorkerForShop(current_shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.REMOVE_PRODUCT_FROM_SHOP.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, handler.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("product_id", new Pair<>(MessageArgCast.INT_ARG, productId));

        RequestMonitor monitor = new RequestMonitor();

        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.reset();
            wout.writeObject(message);
        };

        int sent_to = sendToWorkerWithReplicas(handler, writer, requestId, handler.getId());

        boolean success = false;
        if(sent_to != -1){
            success = true;
            System.out.println("Product was removed successfully");

            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_REMOVE_PRODUCT_FROM_SHOP.ordinal()));

            writer = (wout) -> {
                wout.reset();
                wout.writeObject(message);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(handler, writer);
            System.out.println("Replicas synced.");
        }
        out.writeBoolean(success);
        out.flush();
    }

    private void handleAddAvailableProduct(long requestId)
            throws IOException {

        int productId = in.readInt();
        int quantity = in.readInt();


        ReplicationHandler handler = getWorkerForShop(current_shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_PRODUCT_STOCK.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, handler.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("product_id", new Pair<>(MessageArgCast.INT_ARG, productId));
        message.addArgument("quantity", new Pair<>(MessageArgCast.INT_ARG, quantity));

        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.reset();
            wout.writeObject(message);
        };

        int sent_to = sendToWorkerWithReplicas(handler, writer, requestId, handler.getId());

        boolean success = false;
        if(sent_to != -1){
            success = true;
            System.out.println("Product was added successfully");

            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_ADD_PRODUCT_STOCK.ordinal()));

            writer = (wout) -> {
                wout.reset();
                wout.writeObject(message);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(handler, writer);
            System.out.println("Replicas synced.");
        }
        out.writeBoolean(success);
        out.flush();
    }

    private void handleRemoveAvailableProduct(long requestId)
            throws IOException {

        int productId = in.readInt();
        int quantity = in.readInt();


        ReplicationHandler handler = getWorkerForShop(current_shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.REMOVE_PRODUCT_STOCK.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, handler.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("product_id", new Pair<>(MessageArgCast.INT_ARG, productId));
        message.addArgument("quantity", new Pair<>(MessageArgCast.INT_ARG, quantity));

        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.reset();
            wout.writeObject(message);
        };

        int sent_to = sendToWorkerWithReplicas(handler, writer, requestId, handler.getId());

        boolean success = false;
        if(sent_to != -1){
            success = true;
            System.out.println("Product stock was removed successfully");

            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_REMOVE_PRODUCT_STOCK.ordinal()));

            writer = (wout) -> {
                wout.reset();
                wout.writeObject(message);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(handler, writer);
            System.out.println("Replicas synced.");
        }
        out.writeBoolean(success);
        out.flush();
    }

    public void handleCategoryQuery(long requestId, MessageType query, ReducerPreparationType query_prep_type) throws IOException, InterruptedException {
        String category = in.readUTF();
        System.out.println("Received category: " + category);

        ArrayList<Pair<Integer, Integer>> workers_sent_to = new ArrayList<>();

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, query.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("category", new Pair<>(MessageArgCast.STRING_CAST, category));

        for(ReplicationHandler handler: replicated_worker_handlers.values()) {
            message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, handler.getId()));

            ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
                wout.reset();
                wout.writeObject(message);
            };

            Pair<Integer, Integer> worker_sent_to = sendToWorkerWithReplicas(handler, writer);
            if(worker_sent_to != null)
                workers_sent_to.add(worker_sent_to);
        }
        System.out.println(workers_sent_to);

        RequestMonitor reducer_monitor = new RequestMonitor();
        synchronized (reducer_listener){
            reducer_monitor = reducer_listener.registerMonitor(requestId, REDUCER_ID, reducer_monitor);
        }

        Message red_msg = new Message();
        red_msg.addArgument("prep_ord", new Pair<>(MessageArgCast.INT_ARG, query_prep_type.ordinal()));
        red_msg.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        red_msg.addArgument("workers_sent_to", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, workers_sent_to));

        synchronized (reducer_writer){
            reducer_writer.reset();
            reducer_writer.writeObject(red_msg);
            reducer_writer.flush();
        }

        System.out.println("Waiting for filtered shops from reducer for " + requestId + "...");
        @SuppressWarnings("unchecked")
        Pair<ArrayList<Pair<String, Integer>>, Integer> resulting_shops = (Pair<ArrayList<Pair<String, Integer>>, Integer>) reducer_monitor.getResult();
        System.out.println("Handled query: " + query);

        out.writeObject(resulting_shops);
        out.flush();
    }

    public void handleGetShopCategorySales(long requestId) throws IOException, InterruptedException {
        handleCategoryQuery(requestId, MessageType.GET_SHOP_CATEGORY_SALES, ReducerPreparationType.REDUCER_PREPARE_SHOP_CATEGORY_SALES);
    }

    public void handleGetProductCategorySales(long requestId) throws IOException, InterruptedException {
        handleCategoryQuery(requestId, MessageType.GET_PRODUCT_CATEGORY_SALES, ReducerPreparationType.REDUCER_PREPARE_PRODUCT_CATEGORY_SALES);
    }

    @Override
    public void run() {

        try {
            System.out.println("Manager connected: " + connection.getInetAddress());

            MessageType cmd = MessageType.DEFAULT;
            while (cmd != MessageType.QUIT) {
                int ord = in.readInt();
                cmd = MessageType.values()[ord];
                System.out.println("Manager cmd: " + cmd);
                handleCommand(cmd);
            }
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Manager connection error.");
        } finally {
            try {
                has_manager_connected = false;
                out.close();
                in.close();
                connection.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            System.out.println("Manager session closed.");
        }
    }
}
