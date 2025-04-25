package org.ServerSide.ClientRequests;

import org.Domain.*;
import org.Domain.Cart.CartStatus;
import org.Domain.Cart.ReadableCart;
import org.Domain.Cart.ServerCart;
import org.Filters.*;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ServerSide.Command;
import org.ServerSide.RequestMonitor;
import org.Workers.WorkerCommandType;
import org.Workers.WorkerHandler;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;

import org.ServerSide.MasterServer;

public class ClientRequestHandler extends Thread {

    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket connection;

    private Client client;
    private Integer current_shop_id;

    private ServerCart client_Server_cart;

    public static ArrayList<ReplicationHandler> replicated_worker_handlers;

    public ClientRequestHandler(Socket _connection, ObjectOutputStream _out, ObjectInputStream _in) throws IOException {
        this.connection = _connection;
        out = _out;
        in = _in;
    }

    boolean sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, long request_id, ThrowingBiConsumer<ObjectOutputStream, Integer> write_logic, RequestMonitor monitor) {
        WorkerHandler main_handler = replicatedWorker.getMain();
        try {
            ObjectOutputStream main_out = main_handler.getWorker_out();
            synchronized (main_handler) {
                main_handler.registerMonitor(request_id, monitor);
                write_logic.accept(main_out, -1);
                main_out.flush();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (WorkerHandler replica : replicatedWorker.getReplicas()) {
                ObjectOutputStream replica_out = replica.getWorker_out();
                try {

                    synchronized (replica) {
                        replica.registerMonitor(request_id, monitor);
                        write_logic.accept(replica_out, main_handler.getHandlerId());
                        replica_out.flush();
                    }
                    replicatedWorker.setMain(replica);
                    replicatedWorker.add_replica(main_handler);
                    return true;
                } catch (IOException ex) {
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }
        return false;
    }

    boolean sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, long request_id, ThrowingBiConsumer<ObjectOutputStream, Integer> write_logic) {
        WorkerHandler main_handler = replicatedWorker.getMain();
        try {
            ObjectOutputStream main_out = main_handler.getWorker_out();
            synchronized (main_handler) {
                write_logic.accept(main_out, -1);
                main_out.flush();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (WorkerHandler replica : replicatedWorker.getReplicas()) {
                ObjectOutputStream replica_out = replica.getWorker_out();
                try {
                    synchronized (replica) {
                        write_logic.accept(replica_out, main_handler.getHandlerId());
                        replica_out.flush();
                    }
                    return true;
                } catch (IOException ex) {
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }
        return false;
    }

    void syncReplicas(ReplicationHandler replicated_worker, int main_worker_id, ThrowingBiConsumer<ObjectOutputStream, Integer> write_logic) throws IOException {
        for(WorkerHandler rep_handler: replicated_worker.getReplicas()){
            write_logic.accept(rep_handler.getWorker_out(), main_worker_id);
            rep_handler.getWorker_out().flush();
        }
    }

    void sendToResponsibleWorkers(ReplicationHandler replicated_worker, long request_id, ThrowingBiConsumer<ObjectOutputStream, Integer> write_logic) {
        WorkerHandler main_handler = replicated_worker.getMain();

        try{
            write_logic.accept(main_handler.getWorker_out(), -1);
            main_handler.getWorker_out().flush();

            for(WorkerHandler rep_handler: replicated_worker.getReplicas()){
                write_logic.accept(rep_handler.getWorker_out(), main_handler.getHandlerId());
                rep_handler.getWorker_out().flush();
            }

        }catch (IOException e){
            e.printStackTrace();
        }
    }

    boolean sendToResponsibleWorkers(ReplicationHandler replicated_worker, long request_id, ThrowingBiConsumer<ObjectOutputStream, Integer> write_logic, RequestMonitor monitor) {
        WorkerHandler main_handler = replicated_worker.getMain();

        try{
            main_handler.registerMonitor(request_id, monitor);
            write_logic.accept(main_handler.getWorker_out(), -1);
            main_handler.getWorker_out().flush();

            for(WorkerHandler rep_handler: replicated_worker.getReplicas()){
                rep_handler.registerMonitor(request_id, monitor);
                write_logic.accept(rep_handler.getWorker_out(), main_handler.getHandlerId());
                rep_handler.getWorker_out().flush();
            }
        }catch (IOException e){
            e.printStackTrace();
            return false;
        }
        return true;
    }


    public void handleCommand(Command.CommandTypeClient _client_command) throws IOException, ClassNotFoundException, InterruptedException {
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

        ThrowingBiConsumer<ObjectOutputStream, Integer> get_cart_writer = (out, worker_id) -> {
            out.reset();
            out.writeInt(WorkerCommandType.GET_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop_id);
            out.writeObject(client_Server_cart);
        };

        boolean successfully_sent = sendToWorkerWithReplicas(replicated_worker, requestId, get_cart_writer, monitor);
        if (!successfully_sent) return null;

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

        ArrayList<RequestMonitor> monitors = new ArrayList<>();

        ThrowingBiConsumer<ObjectOutputStream, Integer> filter_writer = (out, worker_id) -> {
            out.writeInt(WorkerCommandType.FILTER.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeObject(filters);
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

    private void handleClearCart(long requestId){
        if(client_Server_cart.getProducts().isEmpty())
            return;

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);

        ThrowingBiConsumer<ObjectOutputStream, Integer> clear_cart_writer = (out, worker_id) -> {
            out.writeInt(WorkerCommandType.CLEAR_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop_id);
            out.writeObject(client_Server_cart);
        };

        sendToResponsibleWorkers(replicated_worker, requestId, clear_cart_writer);

        client_Server_cart.clear_cart();
    }

    private void handleChoseShop(long requestId) throws IOException, InterruptedException {

        int chosen_shop_id = in.readInt();
        ReplicationHandler replicated_worker = getWorkerForShop(chosen_shop_id);
        WorkerHandler responsibleWorker = replicated_worker.getMain();
        System.out.println("Worker " + responsibleWorker.getHandlerId() + " is responsible for shop with id " + chosen_shop_id);

        ThrowingBiConsumer<ObjectOutputStream, Integer> chose_shop_writer = (out, worker_id) -> {
            out.writeInt(WorkerCommandType.CHOSE_SHOP.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(chosen_shop_id);
        };
        RequestMonitor monitor = new RequestMonitor();
        sendToWorkerWithReplicas(replicated_worker, requestId, chose_shop_writer, monitor);

        Shop result = (Shop) monitor.getResult();
        current_shop_id = result.getId();

        client_Server_cart.setShop_id(current_shop_id);

        out.writeObject(result);
        out.flush();
    }

    private void handleAddToCart(long requestId) throws IOException, InterruptedException {
        int product_id = in.readInt();
        int quantity = in.readInt();

        ReplicationHandler replicated_worker = getWorkerForShop(current_shop_id);
        RequestMonitor monitor = new RequestMonitor();

        ThrowingBiConsumer<ObjectOutputStream, Integer> add_to_cart_writer = (out, worker_id) -> {
            out.writeInt(WorkerCommandType.ADD_TO_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop_id);
            out.writeInt(product_id);
            out.writeInt(quantity);
        };

        boolean sent_successfully = sendToWorkerWithReplicas(replicated_worker, requestId, add_to_cart_writer, monitor);
        if (!sent_successfully) return;

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

        ThrowingBiConsumer<ObjectOutputStream, Integer> checkout_writer = (out, worker_id) -> {
            out.writeInt(WorkerCommandType.CHECKOUT_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop_id);
            out.writeObject(client_Server_cart);
            out.writeFloat(client.getBalance());
        };

        sendToWorkerWithReplicas(replicated_worker, requestId, checkout_writer, monitor);

        CheckoutResultWrapper result = (CheckoutResultWrapper) monitor.getResult();

        if (result.in_sync_status == CartStatus.OUT_OF_SYNC)
            System.out.println("Couldn't checkout. Cart was out sync.");
        else if (!result.checked_out)
            System.out.println("Couldn't checkout. Insufficient funds.");
        else{
            System.out.println("Client " + client.getUsername() + " checked out.");
            client_Server_cart.clear_cart();
        }

        ThrowingBiConsumer<ObjectOutputStream, Integer> sync_checkout_writer = (out, worker_id) -> {
            out.writeInt(WorkerCommandType.SYNC_CHECKOUT_CART.ordinal());
            out.writeLong(requestId);
            out.writeInt(worker_id);
            out.writeInt(current_shop_id);
            out.writeObject(client_Server_cart);
        };

        syncReplicas(replicated_worker, replicated_worker.getMain().getHandlerId(), sync_checkout_writer);

        out.writeObject(result);
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
            System.out.println("Closing connection safely and clearing remaining items in cart if needed...");
            try {
                handleCommand(Command.CommandTypeClient.CLEAR_CART);

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
