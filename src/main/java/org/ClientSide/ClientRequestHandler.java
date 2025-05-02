package org.ClientSide;

import org.Domain.*;
import org.Domain.Cart.CartStatus;
import org.Domain.Cart.ReadableCart;
import org.Domain.Cart.ServerCart;
import org.Domain.Utils.Pair;
import org.Filters.*;
import org.MessagePKG.MessageType;
import org.ReducerSide.ReducerPreparationType;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ServerSide.RequestMonitor;
import org.ServerSide.ThrowingConsumer;
import org.Workers.Listeners.ReplicationListener;
import org.MessagePKG.Message;
import org.MessagePKG.MessageArgCast;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;

import static org.ServerSide.MasterServer.*;

public class ClientRequestHandler extends Thread {

    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket connection;

    private Client client;
    private Integer current_shop_id;

    private ServerCart client_Server_cart;

    public ClientRequestHandler(Socket _connection, ObjectOutputStream _out, ObjectInputStream _in) throws IOException {
        this.connection = _connection;
        out = _out;
        in = _in;
    }

    int sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic, RequestMonitor monitor, long request_id, int worker_id) {
        ReplicationListener handler = null;
        synchronized (replicatedWorker) {
            int main_id = replicatedWorker.getId();
            try {
                throw new IOException();
//                handler = worker_listeners.get(main_id);
//                monitor = handler.registerMonitor(request_id, worker_id, monitor);
//
//                ObjectOutputStream main_worker_writer = replicatedWorker.getMain();
//                write_logic.accept(main_worker_writer);
//                main_worker_writer.flush();
//
//                return main_id;
            } catch (IOException e) {
                if (handler != null)
                    handler.unregisterMonitor(request_id, worker_id);

                System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

                for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                    try {
                        handler = worker_listeners.get(replica_id);
                        monitor = handler.registerMonitor(request_id, worker_id, monitor);

                        ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);
                        write_logic.accept(replica_writer);
                        replica_writer.flush();

                        replicatedWorker.promoteToMain(replica_id);

                        return main_id;
                    } catch (IOException ex) {

                        handler.unregisterMonitor(request_id, worker_id);
                        System.err.println("Replica failed. Trying another one...");
                    }
                }
            }
        }
        return -1;
    }

    Pair<Integer, Integer> sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic) {

        synchronized (replicatedWorker) {
            try {
                throw new IOException();
//                ObjectOutputStream main_worker_writer = replicatedWorker.getMain();
//
//                write_logic.accept(main_worker_writer);
//                main_worker_writer.flush();
//
//                return new Pair<>(replicatedWorker.getId(), replicatedWorker.getMainId());
            } catch (IOException e) {
                System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

                for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                    try {
                        ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);

                        write_logic.accept(replica_writer);
                        replica_writer.flush();

                        int prev_main_id;
                        prev_main_id = replicatedWorker.getId();
                        replicatedWorker.promoteToMain(replica_id);

                        return new Pair<>(prev_main_id, replica_id);
                    } catch (IOException ex) {
                        System.err.println("Replica failed. Trying another one...");
                    }
                }
            }
        }
        return null;
    }

    void syncReplicas(ReplicationHandler replicated_worker, ThrowingConsumer<ObjectOutputStream> write_logic) throws IOException{
        synchronized (replicated_worker) {
            for (ObjectOutputStream replica_writer : replicated_worker.getReplicasOutputs()) {
                write_logic.accept(replica_writer);
                replica_writer.flush();
            }
        }
    }

    public void handleCommand(MessageType _client_command) throws IOException, ClassNotFoundException, InterruptedException {
        long requestId = threadId();

        switch (_client_command) {
            case QUIT, DEFAULT, END -> {}
            case FILTER -> handleFilter(requestId);
            case CHOSE_SHOP -> handleChoseShop(requestId);
            case CLEAR_CART -> handleClearCart(requestId);
            case ADD_TO_CART -> handleAddToCart(requestId);
            case REMOVE_FROM_CART -> handleRemoveFromCart();
            case GET_CART -> handleGetCart(requestId);
            case CHECKOUT -> handleCheckout(requestId);
        }
    }

    ReadableCart getCart(long requestId) throws InterruptedException {
        RequestMonitor monitor = new RequestMonitor();

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        Message message = new Message();
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("cart", new Pair<>(MessageArgCast.SERVER_CART_ARG, client_Server_cart));

        ThrowingConsumer<ObjectOutputStream> get_cart_writer = (out) -> {
            out.reset();
            out.writeInt(MessageType.GET_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(replicated_worker.getId());
            out.writeObject(message);
        };

        int successfully_sent = sendToWorkerWithReplicas(replicated_worker, get_cart_writer, monitor, requestId, replicated_worker.getId());
        if (successfully_sent == -1) return null;

        ReadableCart actual_cart = (ReadableCart) monitor.getResult();
        actual_cart.setServer_sync_status(CartStatus.IN_SYNC);

        if(actual_cart.getProduct_quantity_map().size() != client_Server_cart.getProducts().size()) {
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

            for(int non_valid_id: non_valid_products)
                client_Server_cart.getProducts().remove(non_valid_id);
        }

        return actual_cart;
    }

    private void handleFilter(long requestId) throws IOException, ClassNotFoundException, InterruptedException {
        ArrayList<Filter> filters = FilterReader.readFilters(in, client);
        System.out.println("Received Filters: ");
        for (Filter f : filters) {
            System.out.println(f.getClass().getName());
        }

        ArrayList<Pair<Integer, Integer>> workers_sent_to = new ArrayList<>();

        Message message = new Message();
        message.addArgument("filter_list", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, filters));

        for (ReplicationHandler replicated_worker : replicated_worker_handlers.values()) {

            ThrowingConsumer<ObjectOutputStream> filter_writer = (out) -> {
                out.reset();
                out.writeInt(MessageType.FILTER.ordinal());
                out.writeLong(requestId);
                out.writeInt(replicated_worker.getId());
                out.writeObject(message);
            };

            Pair<Integer, Integer> worker_sent_to = sendToWorkerWithReplicas(replicated_worker, filter_writer);
            workers_sent_to.add(worker_sent_to);
        }
        System.out.println(workers_sent_to);
        RequestMonitor reducer_monitor = new RequestMonitor();
        synchronized (reducer_listener) {
            reducer_monitor = reducer_listener.registerMonitor(requestId, REDUCER_ID, reducer_monitor);
        }

        synchronized (reducer_writer) {
            reducer_writer.writeLong(requestId);
            reducer_writer.writeInt(ReducerPreparationType.REDUCER_PREPARE_FILTER.ordinal());
            reducer_writer.writeObject(workers_sent_to);
            reducer_writer.flush();
        }

        System.out.println("Waiting for filtered shops from reducer for " + requestId + "...");
        @SuppressWarnings("unchecked")
        ArrayList<Shop> resulting_shops = (ArrayList<Shop>) reducer_monitor.getResult();
        System.out.println("Received filtered shops for " + requestId);

        out.writeObject(resulting_shops);
        out.flush();
    }

    private void handleClearCart(long requestId){
        if(client_Server_cart.getProducts().isEmpty())
            return;

        client_Server_cart.clear_cart();
    }

    private void handleChoseShop(long requestId) throws IOException, InterruptedException {
        System.out.println("ClientRequestHandler.handleChoseShop");
        int chosen_shop_id = in.readInt();
        System.out.println("shop id: " + chosen_shop_id);

        ReplicationHandler replicated_worker = getWorkerForShop(chosen_shop_id);

        Message message = new Message();
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, chosen_shop_id));

        ThrowingConsumer<ObjectOutputStream> chose_shop_writer = (out) -> {
            out.writeInt(MessageType.CHOSE_SHOP.ordinal());
            out.writeLong(requestId);
            out.writeInt(replicated_worker.getId());
            out.writeObject(message);
        };
        RequestMonitor monitor = new RequestMonitor();
        sendToWorkerWithReplicas(replicated_worker, chose_shop_writer, monitor, requestId, replicated_worker.getId());

        System.out.println("Waiting for shop to return...");
        Shop result = (Shop) monitor.getResult();
        current_shop_id = result.getId();

        client_Server_cart.setShop_id(current_shop_id);

        System.out.println("Sending shop to client." + result);
        out.writeObject(result);
        out.flush();
    }

    private void handleAddToCart(long requestId) throws IOException, InterruptedException {
        System.out.println("ClientRequestHandler.handleAddToCart");
        int product_id = in.readInt();
        System.out.println("shop id: " + product_id);
        int quantity = in.readInt();

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        RequestMonitor monitor = new RequestMonitor();

        Message message = new Message();
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("product_id", new Pair<>(MessageArgCast.INT_ARG, product_id));
        message.addArgument("quantity", new Pair<>(MessageArgCast.INT_ARG, quantity));

        ThrowingConsumer<ObjectOutputStream> add_to_cart_writer = (out) -> {
            out.writeInt(MessageType.ADD_TO_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(replicated_worker.getId());
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

    private void handleRemoveFromCart() throws IOException, InterruptedException {
        int product_id = in.readInt();
        int quantity = in.readInt();

        System.out.println("Removed  " + quantity + " product id: " + product_id + " from " + client.getUsername() + "'s cart");
        client_Server_cart.remove_product(product_id, quantity);
    }

    private void handleGetCart(long requestId) throws IOException, InterruptedException {
        ReadableCart actual_cart = getCart(requestId);

        out.writeObject(actual_cart);
        out.flush();
        System.out.println("Sent " + client.getUsername() + "'s cart");
    }

    private void handleCheckout(long requestId) throws IOException, InterruptedException {

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        RequestMonitor monitor = new RequestMonitor();

        Message message = new Message();
        message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        message.addArgument("cart", new Pair<>(MessageArgCast.SERVER_CART_ARG, client_Server_cart));
        message.addArgument("balance", new Pair<>(MessageArgCast.FLOAT_ARG, client.getBalance()));

        ThrowingConsumer<ObjectOutputStream> checkout_writer = (out) -> {
            out.writeInt(MessageType.CHECKOUT_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(replicated_worker.getId());
            out.writeObject(message);
        };

        sendToWorkerWithReplicas(replicated_worker, checkout_writer, monitor, requestId, replicated_worker.getId());

        CheckoutResultWrapper result = (CheckoutResultWrapper) monitor.getResult();

        if (result.in_sync_status == CartStatus.OUT_OF_SYNC)
            System.out.println("Couldn't checkout. Cart was out sync.");
        else if (!result.checked_out)
            System.out.println("Couldn't checkout. Insufficient funds.");
        else{
            System.out.println("Client " + client.getUsername() + " checked out.");
            client_Server_cart.clear_cart();
        }

        out.writeObject(result);
        out.flush();

        Message sync_message = new Message();
        sync_message.addArgument("shop_id", new Pair<>(MessageArgCast.INT_ARG, current_shop_id));
        sync_message.addArgument("cart", new Pair<>(MessageArgCast.SERVER_CART_ARG, client_Server_cart));

        ThrowingConsumer<ObjectOutputStream> sync_checkout_writer = (out) -> {
            out.writeInt(MessageType.SYNC_CHECKOUT_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(replicated_worker.getId());
            out.writeObject(sync_message);
        };

        syncReplicas(replicated_worker, sync_checkout_writer);
    }

    @Override
    public void run() {
        try {
            System.out.println("New client connected: " + connection.getInetAddress());

            System.out.println("Getting client info...");
            client = (Client) in.readObject();
            System.out.println("Got client info.");

            client_Server_cart = new ServerCart();

            MessageType client_command = MessageType.DEFAULT;

            while (client_command != MessageType.QUIT) {
                int client_cmd_ord = in.readInt();
                client_command = MessageType.values()[client_cmd_ord];

                System.out.println("Received: " + client_command.toString());
                handleCommand(client_command);
            }

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Exception thrown or connection ended abruptly.");
        } finally {
            System.out.println("Closing connection safely and clearing remaining items in cart if needed...");
            try {
                handleCommand(MessageType.CLEAR_CART);

                out.close();
                in.close();
                connection.close();
            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Connection closed successfully.");
        }
    }
}
