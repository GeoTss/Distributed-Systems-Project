package com.example.client_efood.ClientSide;

import com.example.client_efood.Domain.*;
import com.example.client_efood.Filters.Filter;
import com.example.client_efood.Filters.FilterReader;
import com.example.client_efood.Domain.Cart.CartStatus;
import com.example.client_efood.Domain.Cart.ReadableCart;
import com.example.client_efood.Domain.Cart.ServerCart;
import com.example.client_efood.Domain.Utils.Pair;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.ReducerSide.ReducerPreparationType;
import com.example.client_efood.ServerSide.ActiveReplication.ReplicationHandler;
import com.example.client_efood.ServerSide.ClientBatch;
import com.example.client_efood.ServerSide.RequestMonitor;
import com.example.client_efood.ServerSide.ThrowingConsumer;
import com.example.client_efood.Workers.Listeners.ReplicationListener;
import com.example.client_efood.MessagePKG.Message;
import com.example.client_efood.MessagePKG.MessageArgCast;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;

import static com.example.client_efood.ServerSide.MasterServer.*;

public class ClientRequestHandler extends Thread {

    private long id;

    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket connection;

    private Client client;
    private Integer current_shop_id;

    private ServerCart client_Server_cart;

    private ClientBatch client_batch;

    public ClientRequestHandler(Socket _connection, ObjectOutputStream _out, ObjectInputStream _in, long _id) throws IOException {
        id = _id;

        this.connection = _connection;
        out = _out;
        in = _in;
    }

    public void setClientBatch(ClientBatch batch) {
        client_batch = batch;
    }

    int sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic, RequestMonitor monitor, long request_id, int worker_id) {

        try {
            ObjectOutputStream main_worker_writer = client_batch.workerOutput(replicatedWorker.getId());
            if (main_worker_writer == null)
                throw new NullPointerException();

            monitor = client_batch.registerMonitorForWorkerListener(request_id, worker_id, monitor);
            if(monitor == null)
                throw new NullPointerException();

            synchronized (main_worker_writer) {
                write_logic.accept(main_worker_writer);
                main_worker_writer.flush();
            }

            return worker_id;
        } catch (IOException | NullPointerException e) {

            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                try {
                    ObjectOutputStream replica_writer = client_batch.workerOutput(replica_id);

                    if (replica_writer == null) {
                        System.err.println("There isn't a connection for worker with id: " + replica_id + ". Trying other replicas...");
                        continue;
                    }

                    monitor = client_batch.registerMonitorForWorkerListener(request_id, replica_id, monitor);
                    if(monitor == null) {
                        System.err.println("No valid monitor registration. Trying other replicas...");
                        continue;
                    }
                    System.out.println("Set monitor for request_id: " + request_id + " expected from worker " + replica_id);
                    synchronized (replica_writer) {
                        write_logic.accept(replica_writer);
                        replica_writer.flush();
                    }

                    return replica_id;
                } catch (IOException ex) {
                    client_batch.unregisterMonitorForWorkerListener(replica_id, request_id);
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }

        System.out.println("All sockets failed us...");

        return -1;
    }

    Pair<Integer, Integer> sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic) {

        try {
            ObjectOutputStream main_worker_writer = client_batch.workerOutput(replicatedWorker.getId());

            if (main_worker_writer == null)
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
                    ObjectOutputStream replica_writer = client_batch.workerOutput(replica_id);
//                    ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);
                    if (replica_writer == null) {
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

    void syncReplicas(ReplicationHandler replicated_worker, ThrowingConsumer<ObjectOutputStream> write_logic) throws IOException {
        synchronized (replicated_worker) {
            for (Integer replica_id : replicated_worker.getReplicaIds()) {
                ObjectOutputStream replica_writer = client_batch.workerOutput(replica_id);
                if(replica_writer == null)
                    continue;
                write_logic.accept(replica_writer);
                replica_writer.flush();
            }
        }
    }

    public void handleCommand(Message msg) throws IOException, ClassNotFoundException, InterruptedException {
        long request_id = id;

        int command_ord = msg.getArgument("command_ord");

        MessageType client_command = MessageType.values()[command_ord];

        switch (client_command) {
            case QUIT, DEFAULT, END -> {
            }
            case SET_INFO -> handleSetInfo(request_id, msg);
            case FILTER -> handleFilter(request_id, msg);
            case CHOSE_SHOP -> handleChoseShop(request_id, msg);
            case CLEAR_CART -> handleClearCart(request_id);
            case ADD_TO_CART -> handleAddToCart(request_id, msg);
            case REMOVE_FROM_CART -> handleRemoveFromCart(msg);
            case GET_CART -> handleGetCart(request_id, msg);
            case CHECKOUT -> handleCheckout(request_id, msg);
            case GIVE_RATING -> handleRating(request_id, msg);
        }
    }

    private void handleRating(long requestId, Message msg) throws InterruptedException, IOException {

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        Message message = new Message();

        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.GIVE_RATING.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));

        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));

        float rating = msg.getArgument("rating");
        message.addArgument("rating", new Pair<>(MessageArgCast.FLOAT_ARG, rating));

        ThrowingConsumer<ObjectOutputStream> rating_writer = (out) -> {
            out.reset();
            out.writeObject(message);
        };

        RequestMonitor monitor = new RequestMonitor();
        int success = sendToWorkerWithReplicas(replicated_worker, rating_writer, monitor, requestId, replicated_worker.getId());

        Boolean successful_rating = false;
        if (success != -1) {
            successful_rating = monitor.getResult();

            if (successful_rating) {

                Message sync_msg = new Message();

                message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_RATING.ordinal()));
                message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
                message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));
                message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
                message.addArgument("rating", new Pair<>(MessageArgCast.FLOAT_ARG, rating));

                ThrowingConsumer<ObjectOutputStream> sync_writer = (out) -> {
                    out.reset();
                    out.writeObject(sync_msg);
                };

                syncReplicas(replicated_worker, sync_writer);
            }
        }
        out.writeBoolean(successful_rating);
        out.flush();
    }

    private void handleSetInfo(long requestId, Message client_info) {
        System.out.println("Getting client info...");
        double longitude = client_info.getArgument("longitude");
        double latitude = client_info.getArgument("latitude");
        Location location = new Location(latitude, longitude);

        float balance = client_info.getArgument("balance");

        client = new Client(client_info.getArgument("username"), location);
        client.updateBalance(balance);
        System.out.println("Got client info.");
    }

    ReadableCart getCart(long requestId) throws InterruptedException {
        RequestMonitor monitor = new RequestMonitor();

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        Message message = new Message();

        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.GET_CART.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));

        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("cart", new Pair<>(MessageArgCast.SERVER_CART_ARG, client_Server_cart));

        ThrowingConsumer<ObjectOutputStream> get_cart_writer = (out) -> {
            out.reset();
            out.writeObject(message);
        };

        int successfully_sent = sendToWorkerWithReplicas(replicated_worker, get_cart_writer, monitor, requestId, replicated_worker.getId());
        if (successfully_sent == -1) return null;

        ReadableCart actual_cart = (ReadableCart) monitor.getResult();
        actual_cart.setServer_sync_status(CartStatus.IN_SYNC);

        if (actual_cart.getProduct_quantity_map().size() != client_Server_cart.getProducts().size()) {
            actual_cart.setServer_sync_status(CartStatus.OUT_OF_SYNC);

            ArrayList<Integer> non_valid_products = new ArrayList<>();

            Set<Product> actual_products = actual_cart.getProduct_quantity_map().keySet();

            client_Server_cart.getProducts().keySet().forEach(product_id -> {
                Product found_product = actual_products.stream()
                        .filter(product -> product.getId().equals(product_id))
                        .findFirst()
                        .orElse(null);

                if (found_product == null)
                    non_valid_products.add(product_id);
            });

            for (int non_valid_id : non_valid_products)
                client_Server_cart.getProducts().remove(non_valid_id);
        }
        System.out.println(actual_cart);
        return actual_cart;
    }

    private void handleFilter(long requestId, Message filter_message) throws IOException, ClassNotFoundException, InterruptedException {
        ArrayList<Filter> filters = FilterReader.readFilters(client, filter_message);
        System.out.println("Received Filters: ");
        for (Filter f : filters) {
            System.out.println(f.getClass().getName());
        }

        ArrayList<Pair<Integer, Integer>> workers_sent_to = new ArrayList<>();

        for (ReplicationHandler replicated_worker : replicated_worker_handlers.values()) {
            Message message = new Message();

            message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.FILTER.ordinal()));
            message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
            message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));
            message.addArgument("filter_list", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, filters));

            ThrowingConsumer<ObjectOutputStream> filter_writer = (out) -> {
                out.reset();
                out.writeObject(message);
            };

            Pair<Integer, Integer> worker_sent_to = sendToWorkerWithReplicas(replicated_worker, filter_writer);
            if (worker_sent_to != null)
                workers_sent_to.add(worker_sent_to);
        }
        System.out.println(workers_sent_to);
        RequestMonitor reducer_monitor = new RequestMonitor();
        client_batch.setMonitorForReducer(requestId, reducer_monitor);
//        synchronized (reducer_listener) {
//            reducer_monitor = reducer_listener.registerMonitor(requestId, REDUCER_ID, reducer_monitor);
//        }

        Message msg = new Message();
        msg.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        msg.addArgument("prep_ord", new Pair<>(MessageArgCast.INT_ARG, ReducerPreparationType.REDUCER_PREPARE_FILTER.ordinal()));
        msg.addArgument("workers_sent_to", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, workers_sent_to));

        client_batch.sendToReducer(msg);

//        synchronized (reducer_writer) {
//            reducer_writer.writeObject(msg);
//            reducer_writer.flush();
//        }

        System.out.println("Waiting for filtered shops from reducer for " + requestId + "...");

        ArrayList<Shop> resulting_shops = reducer_monitor.getResult();
        System.out.println("Received filtered shops for " + requestId);

        out.writeObject(resulting_shops);
        out.flush();
    }

    private void handleClearCart(long requestId) {
        if (client_Server_cart.getProducts().isEmpty())
            return;

        client_Server_cart.clear_cart();
    }

    private void handleChoseShop(long requestId, Message msg) throws IOException, InterruptedException {
        System.out.println("ClientRequestHandler.handleChoseShop");
        int chosen_shop_id = msg.getArgument("shop_id");
        System.out.println("shop id: " + chosen_shop_id);

        ReplicationHandler replicated_worker = getWorkerForShop(chosen_shop_id);

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.CHOSE_SHOP.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, chosen_shop_id));

        ThrowingConsumer<ObjectOutputStream> chose_shop_writer = (out) -> {
            out.reset();
            out.writeObject(message);
        };
        RequestMonitor monitor = new RequestMonitor();
        int sent_to = sendToWorkerWithReplicas(replicated_worker, chose_shop_writer, monitor, requestId, replicated_worker.getId());

        System.out.println("Waiting for shop to return...");
        Shop result = monitor.getResult();
        current_shop_id = result.getId();

        client_Server_cart.setShop_id(current_shop_id);

        System.out.println("Sending shop to client." + result);
        out.writeObject(result);
        out.flush();
    }

    private void handleAddToCart(long requestId, Message msg) throws IOException, InterruptedException {
        System.out.println("ClientRequestHandler.handleAddToCart");
        int product_id = msg.getArgument("product_id");
        System.out.println("shop id: " + product_id);
        int quantity = msg.getArgument("quantity");

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        RequestMonitor monitor = new RequestMonitor();

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.ADD_TO_CART.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("product_id", new Pair<>(MessageArgCast.INT_ARG, product_id));
        message.addArgument("quantity", new Pair<>(MessageArgCast.INT_ARG, quantity));

        ThrowingConsumer<ObjectOutputStream> add_to_cart_writer = (out) -> {
            out.reset();
            out.writeObject(message);
        };

        int sent_successfully = sendToWorkerWithReplicas(replicated_worker, add_to_cart_writer, monitor, requestId, replicated_worker.getId());
        if (sent_successfully == -1) return;

        Integer product_to_be_added = (Integer) monitor.getResult();
        boolean added_to_cart = product_to_be_added != -1;

        if (added_to_cart) {
            client_Server_cart.add_product(product_to_be_added, quantity);
            System.out.println("Added " + quantity + " " + product_to_be_added + " to " + client.getUsername() + "'s cart");
        } else {
            System.err.println("Not enough stock");
        }

        out.writeBoolean(added_to_cart);
        out.flush();
    }

    private void handleRemoveFromCart(Message msg) throws IOException, InterruptedException {
        int product_id = msg.getArgument("product_id");
        int quantity = msg.getArgument("quantity");

        System.out.println("Removed  " + quantity + " product id: " + product_id + " from " + client.getUsername() + "'s cart");
        client_Server_cart.remove_product(product_id, quantity);
    }

    private void handleGetCart(long requestId, Message msg) throws IOException, InterruptedException {
        ReadableCart actual_cart = getCart(requestId);

        out.writeObject(actual_cart);
        out.flush();
        System.out.println("Sent " + client.getUsername() + "'s cart");
    }

    private void handleCheckout(long requestId, Message msg) throws IOException, InterruptedException {

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        RequestMonitor monitor = new RequestMonitor();

        Message message = new Message();
        message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.CHECKOUT_CART.ordinal()));
        message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("cart", new Pair<>(MessageArgCast.SERVER_CART_ARG, client_Server_cart));
        message.addArgument("balance", new Pair<>(MessageArgCast.FLOAT_ARG, client.getBalance()));

        ThrowingConsumer<ObjectOutputStream> checkout_writer = (out) -> {
            out.reset();
            out.writeObject(message);
        };

        sendToWorkerWithReplicas(replicated_worker, checkout_writer, monitor, requestId, replicated_worker.getId());

        CheckoutResultWrapper result = (CheckoutResultWrapper) monitor.getResult();

        if (result.in_sync_status == CartStatus.OUT_OF_SYNC)
            System.out.println("Couldn't checkout. Cart was out sync.");
        else if (!result.checked_out)
            System.out.println("Couldn't checkout. Insufficient funds.");
        else {
            System.out.println("Client " + client.getUsername() + " checked out.");
            client_Server_cart.clear_cart();
        }

        out.writeObject(result);
        out.flush();

        Message sync_message = new Message();
        sync_message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_CHECKOUT_CART.ordinal()));
        sync_message.addArgument("request_id", new Pair<>(MessageArgCast.LONG_ARG, requestId));
        sync_message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, replicated_worker.getId()));
        sync_message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        sync_message.addArgument("cart", new Pair<>(MessageArgCast.SERVER_CART_ARG, client_Server_cart));

        ThrowingConsumer<ObjectOutputStream> sync_checkout_writer = (out) -> {
            out.reset();
            out.writeObject(sync_message);
        };

        syncReplicas(replicated_worker, sync_checkout_writer);
    }

    @Override
    public void run() {
        try {
            System.out.println("New client connected: " + connection.getInetAddress());

            client_Server_cart = new ServerCart();

            //MessageType client_command = MessageType.DEFAULT;

            while (true) {
//                int client_cmd_ord = in.readInt();
//                client_command = MessageType.values()[client_cmd_ord];
                Message msg = (Message) in.readObject();

                System.out.println("Received: " + msg);
                handleCommand(msg);
            }

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Exception thrown or connection ended abruptly.");
        } finally {
            System.out.println("Closing connection safely and clearing remaining items in cart if needed...");
            try {

                //handleCommand(MessageType.CLEAR_CART, null);

                out.close();
                in.close();
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Connection closed successfully.");
        }
    }
}
