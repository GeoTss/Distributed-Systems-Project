package org.ServerSide.ClientRequests;

import org.Domain.*;
import org.Filters.*;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ServerSide.Command;
import org.ServerSide.RequestMonitor;
import org.Workers.WorkerHandler;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import org.ServerSide.MasterServer;

import javax.management.monitor.Monitor;

public class ClientRequestHandler extends Thread {

    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket connection;

    private Client client;
    private Shop current_shop;

    private ServerCart client_Server_cart;

    public static ArrayList<ReplicationHandler> replicated_worker_handlers;

    public ClientRequestHandler(Socket _connection) throws IOException {
        this.connection = _connection;

        try {
            System.out.println("ClientRequestHandler.ClientRequestHandler");
            System.out.println("Trying to get streams from " + _connection);
            out = new ObjectOutputStream(_connection.getOutputStream());
            out.flush();
            in = new ObjectInputStream(_connection.getInputStream());
            System.out.println("Got streams from " + _connection);

        } catch (IOException e) {
            System.out.println("Exception at: ");
            System.out.println("ClientRequestHandler.ClientRequestHandler");
            e.printStackTrace();
            _connection.close();
        }
    }

    boolean sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, long request_id, ThrowingBiConsumer<ObjectOutputStream, Integer> write_logic, RequestMonitor monitor) {
        WorkerHandler main_handler = replicatedWorker.getMain();
        try {

            synchronized (main_handler) {
                main_handler.registerMonitor(request_id, monitor);
                write_logic.accept(main_handler.getWorker_out(), -1);
            }
            return true;
        } catch (IOException e) {
            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (WorkerHandler replica : replicatedWorker.getReplicas()) {
                ObjectOutputStream replicaOut = replica.getWorker_out();
                try {

                    synchronized (replicaOut) {
                        replica.registerMonitor(request_id, monitor);
                        write_logic.accept(replicaOut, main_handler.getHandlerId());
                    }
                    return true;
                } catch (IOException ex) {
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }
        return false;
    }

    public void handleCommand(Command.CommandTypeClient _client_command) throws IOException, ClassNotFoundException, InterruptedException {
        long requestId = threadId();

        switch (_client_command) {
            case QUIT, DEFAULT, END -> {}
            case FILTER -> handleFilter(requestId);
            case CHOSE_SHOP -> handleChoseShop(requestId);
            case ADD_TO_CART -> handleAddToCart(requestId);
            case REMOVE_FROM_CART -> handleRemoveFromCart(requestId);
            case GET_CART -> handleGetCart(requestId);
            case CHECKOUT -> handleCheckout(requestId);
        }
    }

    private void handleFilter(long requestId) throws IOException, ClassNotFoundException, InterruptedException {
        ArrayList<Filter> filters = FilterReader.readFilters(in, client);
        System.out.println("Received Filters: ");
        for (Filter f : filters) {
            System.out.println(f.getClass().getName());
        }

        ArrayList<RequestMonitor> monitors = new ArrayList<>();

        ThrowingBiConsumer<ObjectOutputStream, Integer> filter_writer = (out, worker_id) -> {
            out.writeInt(Command.CommandTypeClient.FILTER.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeObject(filters);
            out.flush();
        };

        for (ReplicationHandler replicated_worker : replicated_worker_handlers) {
            RequestMonitor monitor = new RequestMonitor();
            sendToWorkerWithReplicas(replicated_worker, requestId, filter_writer, monitor);
            monitors.add(monitor);
        }

        ArrayList<Shop> resulting_shops = new ArrayList<>();
        for (RequestMonitor monitor : monitors) {
            @SuppressWarnings("unchecked") ArrayList<Shop> filtered_worker_shops = (ArrayList<Shop>) monitor.getResult();
            if (filtered_worker_shops != null) resulting_shops.addAll(filtered_worker_shops);
        }

        out.writeObject(resulting_shops);
        out.flush();
    }

    private void handleChoseShop(long requestId) throws IOException, InterruptedException {
        int chosen_shop_id = in.readInt();
        ReplicationHandler replicated_worker = getWorkerForShop(chosen_shop_id);
        WorkerHandler responsibleWorker = replicated_worker.getMain();
        System.out.println("Worker " + responsibleWorker.getHandlerId() + " is responsible for shop with id " + chosen_shop_id);

        ThrowingBiConsumer<ObjectOutputStream, Integer> chose_shop_writer = (out, worker_id) -> {
            out.writeInt(Command.CommandTypeClient.CHOSE_SHOP.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(chosen_shop_id);
            out.flush();
        };
        RequestMonitor monitor = new RequestMonitor();
        sendToWorkerWithReplicas(replicated_worker, requestId, chose_shop_writer, monitor);

        Shop result = (Shop) monitor.getResult();
        current_shop = result;
        client_Server_cart.setShop_id(current_shop.getId());
        client_Server_cart.clear_cart();

        out.writeObject(result);
        out.flush();
    }

    private void handleAddToCart(long requestId) throws IOException, InterruptedException {
        int product_id = in.readInt();
        int quantity = in.readInt();

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop.getId());
        RequestMonitor monitor = new RequestMonitor();

        ThrowingBiConsumer<ObjectOutputStream, Integer> add_to_cart_writer = (out, worker_id) -> {
            out.writeInt(Command.CommandTypeClient.ADD_TO_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop.getId());
            out.writeInt(product_id);
            out.writeInt(quantity);
            out.flush();
        };

        boolean sent_successfully = sendToWorkerWithReplicas(replicated_worker, requestId, add_to_cart_writer, monitor);
        if (!sent_successfully) return;

        Integer product_to_be_added = (Integer) monitor.getResult();
        boolean added_to_cart = product_to_be_added != null;

        if (added_to_cart) {
            client_Server_cart.add_product(product_to_be_added, quantity);
            System.out.println("Added " + quantity + " " + product_to_be_added + " to " + client.getUsername() + "'s cart");
        } else {
            System.err.println("Not enough stock");
        }

        out.writeBoolean(added_to_cart);
        out.flush();
    }

    private void handleRemoveFromCart(long requestId) throws IOException, InterruptedException {
        int product_id = in.readInt();
        int quantity = in.readInt();

        RequestMonitor monitor = new RequestMonitor();
        ReplicationHandler replicated_worker = getWorkerForShop(current_shop.getId());

        ThrowingBiConsumer<ObjectOutputStream, Integer> remove_from_cart_writer = (out, worker_id) -> {
            out.writeInt(Command.CommandTypeClient.REMOVE_FROM_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop.getId());
            out.writeInt(product_id);
            out.writeInt(quantity);
            out.flush();
        };

        boolean successfully_sent = sendToWorkerWithReplicas(replicated_worker, requestId, remove_from_cart_writer, monitor);
        if (!successfully_sent) return;

        Boolean removed = (Boolean) monitor.getResult();
        if (removed) {
            System.out.println("Removed  " + quantity + " product id: " + product_id + " from " + client.getUsername() + "'s cart");
            client_Server_cart.remove_product(product_id, quantity);
        } else {
            System.out.println("Problem during removal.");
        }

        out.writeBoolean(removed);
        out.flush();
    }

    private void handleGetCart(long requestId) throws IOException, InterruptedException {
        RequestMonitor monitor = new RequestMonitor();
        ReplicationHandler replicated_worker = getWorkerForShop(current_shop.getId());

        ThrowingBiConsumer<ObjectOutputStream, Integer> get_cart_writer = (out, worker_id) -> {
            out.reset();
            out.writeInt(Command.CommandTypeClient.GET_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop.getId());
            out.writeObject(client_Server_cart);
            out.flush();
        };

        boolean successfully_sent = sendToWorkerWithReplicas(replicated_worker, requestId, get_cart_writer, monitor);
        if (!successfully_sent) return;

        ReadableCart actual_cart = (ReadableCart) monitor.getResult();
        out.writeObject(actual_cart);
        out.flush();
        System.out.println("Sent " + client.getUsername() + "'s cart");
    }

    private void handleCheckout(long requestId) throws IOException, InterruptedException {
        ReplicationHandler replicated_worker = getWorkerForShop(current_shop.getId());
        RequestMonitor monitor = new RequestMonitor();

        ThrowingBiConsumer<ObjectOutputStream, Integer> checkout_writer = (out, worker_id) -> {
            out.writeInt(Command.CommandTypeClient.CHECKOUT.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop.getId());
            out.writeObject(client_Server_cart);
            out.writeFloat(client.getBalance());
            out.flush();
        };

        boolean successfully_sent = sendToWorkerWithReplicas(replicated_worker, requestId, checkout_writer, monitor);
        if (!successfully_sent) return;

        boolean checked_out = (Boolean) monitor.getResult();
        if (checked_out) {
            System.out.println("Client " + client.getUsername() + " checked out.");
            client_Server_cart.clear_cart();
        } else {
            System.out.println("Couldn't checkout. Insufficient funds.");
        }

        out.writeBoolean(checked_out);
        out.flush();
    }


    private static ReplicationHandler getWorkerForShop(int chosenShopId) {
        return replicated_worker_handlers.get(chosenShopId / MasterServer.getConfig_info().getWorker_chunk());
    }

    @Override
    public void run() {
        try {
            System.out.println("New client connected: " + connection.getInetAddress());

            System.out.println("Getting client info...");
            client = (Client) in.readObject();
            System.out.println("Got client info.");

            client_Server_cart = new ServerCart();

            Command.CommandTypeClient client_command = Command.CommandTypeClient.DEFAULT;

            while (client_command != Command.CommandTypeClient.QUIT) {
                int client_cmd_ord = in.readInt();
                client_command = Command.CommandTypeClient.values()[client_cmd_ord];

                System.out.println("Received: " + client_command.toString());
                handleCommand(client_command);
            }

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Exception thrown or connection ended abruptly.");
        } finally {
            System.out.println("Closing connection safely...");
            try {
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
