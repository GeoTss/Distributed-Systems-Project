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

import org.ServerSide.MasterServer;

public class ClientRequestHandler extends Thread {

    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket connection;

    private Location client_location;
    private Client client;
    private Shop current_shop;

    private Cart client_cart;

    public static ArrayList<WorkerHandler> worker_handlers;
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

//            client_location = (Location) in.readObject();

        } catch (IOException e) {
            System.out.println("Exception at: ");
            System.out.println("ClientRequestHandler.ClientRequestHandler");
            e.printStackTrace();
            _connection.close();
        }
    }

    private ArrayList<Filter> readFilterCommand() throws IOException, ClassNotFoundException {
        ArrayList<Filter> filters = new ArrayList<>();

        int ord_filter = in.readInt();
        Filter.Types specific_filters = Filter.Types.values()[ord_filter];

        while (specific_filters != Filter.Types.END) {
            System.out.println("Received filter " + specific_filters.toString() + " with arguments: ");
            Filter filter = switch (specific_filters) {
                case FILTER_STARS -> {
                    float min_rating = in.readFloat();
                    System.out.println("Float: " + min_rating);
                    yield new RateFilter<Shop>(min_rating);
                }
                case FILTER_PRICE -> {
                    int ord_pr_cat = in.readInt();
                    PriceCategoryEnum pr_cat = PriceCategoryEnum.values()[ord_pr_cat];
                    System.out.println("PriceCategory: " + pr_cat.toString());
                    yield new PriceCategoryFilter<Shop>(pr_cat);
                }
                case FILTER_CATEGORY -> {
                    @SuppressWarnings("unchecked")
                    Set<String> shop_categories = (TreeSet<String>) in.readObject();
                    System.out.println("Set<String> categories: [ ");
                    shop_categories.forEach(System.out::println);
                    System.out.println("]");

                    yield new SameCategory<Shop>(shop_categories);
                }
                case FILTER_RADIUS -> {
                    double max_radius = in.readDouble();
                    System.out.println("max_radius = " + max_radius);

                    yield new InRangeFilter<Shop>(client_location, max_radius);
                }
                case END -> null;
            };
            filters.add(filter);

            ord_filter = in.readInt();
            specific_filters = Filter.Types.values()[ord_filter];
        }

        return filters;
    }

    public void handleCommand(Command.CommandTypeClient _client_command)
            throws IOException, ClassNotFoundException, InterruptedException {

        long requestId = threadId();

        switch (_client_command) {
            case QUIT, DEFAULT, END -> {
                break;
            }
            case FILTER -> {
                ArrayList<Filter> filters = readFilterCommand();
                System.out.println("Received Filters: ");
                for (Filter f : filters) {
                    System.out.println(f.getClass().getName());
                }

                ArrayList<RequestMonitor> monitors = new ArrayList<>();

                // get the main from each worker and if it fails go for replicas
                for (ReplicationHandler replicated_worker : replicated_worker_handlers) {

                    WorkerHandler main_handler = replicated_worker.getMain();

                    // monitor for the current request on the current worker
                    RequestMonitor monitor = new RequestMonitor();
                    ObjectOutputStream worker_out = main_handler.getWorker_out();
                    boolean successfully_sent = false;

                    try {

                        // register monitor before sending to worker?
                        // What if it fails the monitor never gets result?
                        main_handler.registerMonitor(requestId, monitor);

                        synchronized (worker_out) {
                            worker_out.writeInt(Command.CommandTypeClient.FILTER.ordinal());
                            worker_out.writeLong(requestId);
                            worker_out.writeInt(-1);
                            worker_out.writeObject(filters);
                            worker_out.flush();
                        }
                        successfully_sent = true;

                    } catch (IOException e) {
                        System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

                        for (WorkerHandler rep_handler : replicated_worker.getReplicas()) {

                            ObjectOutputStream rep_worker_out = rep_handler.getWorker_out();

                            try {
                                rep_handler.registerMonitor(requestId, monitor);

                                synchronized (rep_worker_out) {
                                    rep_worker_out.writeInt(Command.CommandTypeClient.FILTER.ordinal());
                                    rep_worker_out.writeLong(requestId);
                                    rep_worker_out.writeInt(main_handler.getHandlerId());
                                    rep_worker_out.writeObject(filters);
                                    rep_worker_out.flush();
                                }

                                successfully_sent = true;
                                break;
                            } catch (IOException ex) {
                                System.err.println("Replica failed. Trying another one...");
                            }
                        }
                    }

                    if (!successfully_sent) {
                        System.err.println(
                                "Main worker and all replicas failed sending command and arguments for this chunk.");
                    }
                    monitors.add(monitor);
                }

                ArrayList<Shop> resulting_shops = new ArrayList<>();

                for (RequestMonitor monitor : monitors) {
                    @SuppressWarnings("unchecked")
                    ArrayList<Shop> filtered_worker_shops = (ArrayList<Shop>) monitor.getResult();

                    // if filtered_worker_shops is null the addAll thorws exception
                    if (filtered_worker_shops != null)
                        resulting_shops.addAll(filtered_worker_shops);
                }
                out.writeObject(resulting_shops);
                out.flush();
            }
            case CHOSE_SHOP -> {
                // gets chosen shop from client and sends back the chosen shop with extra steps?
                int chosen_shop_id = in.readInt();

                ReplicationHandler replicated_worker = getWorkerForShop(chosen_shop_id);
                WorkerHandler responsibleWorker = replicated_worker.getMain();
                System.out.println("Worker " + responsibleWorker.getHandlerId() + " is responsible for shop with id " + chosen_shop_id);

                ObjectOutputStream worker_out = responsibleWorker.getWorker_out();
                synchronized (worker_out) {
                    worker_out.writeInt(Command.CommandTypeClient.CHOSE_SHOP.ordinal());
                    worker_out.writeLong(requestId);
                    worker_out.writeInt(-1);
                    worker_out.writeInt(chosen_shop_id);
                    worker_out.flush();
                }

                RequestMonitor monitor = responsibleWorker.registerRequest(requestId);
                Shop result = (Shop) monitor.getResult();
                System.out.println("Resulting shop: " + result);
                current_shop = result;

                client_cart.setShop_id(current_shop.getId());
                client_cart.clear_cart();

                out.writeObject(result);
                out.flush();
            }
            case ADD_TO_CART -> {

                Integer product_id = in.readInt();
//                Product product = current_shop.getProductByName(product_name);
                int quantity = in.readInt();
                System.out.println(product_id);
                System.out.println("For " + quantity);

                ReplicationHandler replicated_worker = getWorkerForShop(current_shop.getId());
                WorkerHandler responsibleWorker = replicated_worker.getMain();

                RequestMonitor monitor = new RequestMonitor();
                ObjectOutputStream worker_out = responsibleWorker.getWorker_out();
                boolean successfully_sent = false;

                try {

                    responsibleWorker.registerMonitor(requestId, monitor);

                    synchronized (worker_out) {
                        worker_out.writeInt(Command.CommandTypeClient.ADD_TO_CART.ordinal());
                        worker_out.writeLong(requestId);
                        worker_out.writeInt(-1);
                        worker_out.writeInt(current_shop.getId());
                        worker_out.writeInt(product_id);
                        worker_out.writeInt(quantity);
                        worker_out.flush();
                    }
                    successfully_sent = true;

                } catch (IOException e) {
                    System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

                    for (WorkerHandler rep_handler : replicated_worker.getReplicas()) {

                        ObjectOutputStream rep_worker_out = rep_handler.getWorker_out();

                        try {
                            rep_handler.registerMonitor(requestId, monitor);
                            synchronized (rep_worker_out) {
                                rep_worker_out.writeInt(Command.CommandTypeClient.ADD_TO_CART.ordinal());
                                rep_worker_out.writeLong(requestId);
                                rep_worker_out.writeInt(responsibleWorker.getHandlerId());
                                rep_worker_out.writeInt(current_shop.getId());
                                rep_worker_out.writeInt(product_id);
                                rep_worker_out.writeInt(quantity);
                                rep_worker_out.flush();
                            }
                            successfully_sent = true;
                        } catch (IOException ex) {
                            System.err.println("Replica failed. Trying another one...");
                        }
                    }
                }

                if (!successfully_sent) {
                    System.err.println(
                            "Main worker and all replicas failed sending command and arguments for this chunk.");
                    break;
                }

                Integer product_to_be_added = (Integer) monitor.getResult();
                boolean added_to_cart;
                if (product_to_be_added != null) {
                    added_to_cart = true;
                    client_cart.add_product(product_to_be_added, quantity);
                    System.out.println(
                            "Added " + quantity + " " + product_to_be_added + " to " + client.getUsername() + "'s cart");
                } else {
                    added_to_cart = false;
                    System.err.println("Not enough stock");
                }

                out.writeBoolean(added_to_cart);
                out.flush();
            }
            case REMOVE_FROM_CART -> {

                int product_id = in.readInt();
                int quantity = in.readInt();
                System.out.println("Got values for removal");

                RequestMonitor monitor = new RequestMonitor();
                ReplicationHandler replicated_worker = getWorkerForShop(current_shop.getId());
                WorkerHandler responsibleWorker = replicated_worker.getMain();

                ObjectOutputStream worker_out = responsibleWorker.getWorker_out();
                boolean successfully_sent = false;

                try {
                    responsibleWorker.registerMonitor(requestId, monitor);
                    synchronized (worker_out) {
                        worker_out.writeInt(Command.CommandTypeClient.REMOVE_FROM_CART.ordinal());
                        worker_out.writeLong(requestId);
                        worker_out.writeInt(-1);
                        worker_out.writeInt(current_shop.getId());
                        worker_out.writeInt(product_id);
                        worker_out.writeInt(quantity);
                        worker_out.flush();
                    }
                    successfully_sent = true;

                } catch (IOException e) {
                    System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

                    for (WorkerHandler rep_handler : replicated_worker.getReplicas()) {

                        ObjectOutputStream rep_worker_out = rep_handler.getWorker_out();

                        try {
                            rep_handler.registerMonitor(requestId, monitor);
                            synchronized (rep_worker_out) {
                                rep_worker_out.writeInt(Command.CommandTypeClient.REMOVE_FROM_CART.ordinal());
                                rep_worker_out.writeLong(requestId);
                                rep_worker_out.writeInt(responsibleWorker.getHandlerId());
                                rep_worker_out.writeInt(current_shop.getId());
                                rep_worker_out.writeInt(product_id);
                                rep_worker_out.writeInt(quantity);
                                rep_worker_out.flush();
                            }
                            successfully_sent = true;
                        } catch (IOException ex) {
                            System.err.println("Replica failed. Trying another one...");
                        }
                    }
                }

                if (!successfully_sent) {
                    System.err.println("Main worker and all replicas failed sending command and arguments for this chunk.");
                    break;
                }

                Boolean removed = (Boolean) monitor.getResult();
                System.out.println("Got result from monitor");
                if(removed)
                    System.out.println("Removed  " + quantity + " product id: " + product_id + " from " + client.getUsername() + "'s cart");
                else
                    System.out.println("Problem during removal.");

                client_cart.remove_product(product_id, quantity);

                out.writeBoolean(removed);
                out.flush();
            }
            case GET_CART -> {
                out.reset();
                out.writeObject(client_cart);
                out.flush();
                System.out.println("Sent " + client.getUsername() + "'s cart");
                client_cart.getProducts().forEach((key, value) -> System.out.println("{\n" + key.toString() + "\nQuantity: " + value + "}"));
            }
            case CHECKOUT -> {

                ReplicationHandler replicated_worker = getWorkerForShop(current_shop.getId());
                WorkerHandler responsibleWorker = replicated_worker.getMain();

                RequestMonitor monitor = new RequestMonitor();
                ObjectOutputStream worker_out = responsibleWorker.getWorker_out();
                boolean successfully_sent = false;

                try {

                    responsibleWorker.registerMonitor(requestId, monitor);

                    synchronized (worker_out) {
                        worker_out.writeInt(Command.CommandTypeClient.CHECKOUT.ordinal());
                        worker_out.writeLong(requestId);
                        worker_out.writeInt(-1);
                        worker_out.writeInt(current_shop.getId());
                        worker_out.writeObject(client_cart);
                        worker_out.writeFloat(client.getBalance());
                        worker_out.flush();
                    }
                    successfully_sent = true;

                } catch (IOException e) {
                    System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

                    for (WorkerHandler rep_handler : replicated_worker.getReplicas()) {

                        ObjectOutputStream rep_worker_out = rep_handler.getWorker_out();

                        try {
                            rep_handler.registerMonitor(requestId, monitor);
                            synchronized (rep_worker_out) {
                                rep_worker_out.writeInt(Command.CommandTypeClient.CHECKOUT.ordinal());
                                rep_worker_out.writeLong(requestId);
                                rep_worker_out.writeInt(responsibleWorker.getHandlerId());
                                worker_out.writeInt(current_shop.getId());
                                rep_worker_out.writeObject(client_cart);
                                rep_worker_out.writeFloat(client.getBalance());
                                rep_worker_out.flush();
                            }
                            successfully_sent = true;
                        } catch (IOException ex) {
                            System.err.println("Replica failed. Trying another one...");
                        }
                    }
                }

                if (!successfully_sent) {
                    System.err.println(
                            "Main worker and all replicas failed sending command and arguments for this chunk.");
                    break;
                }

                boolean checked_out = (Boolean) monitor.getResult();
                if (checked_out) {
                    System.out.println(
                            "Client " + client.getUsername() + " checked out.");
                } else {
                    System.err.println("Couldn't checkout");
                }

                break;
            }
        }
    }

    private static ReplicationHandler getWorkerForShop(int chosenShopId) {
        return replicated_worker_handlers.get(chosenShopId / MasterServer.getConfig_info().getWorker_chunk());
    }

    @Override
    public void run() {
        try {
            System.out.println("New client connected: " + connection.getInetAddress());

            System.out.println("Getting client info...");
//            client_location = (Location) in.readObject();
            client = (Client) in.readObject();
            System.out.println("Got client info.");

            client_cart = new Cart();

            Command.CommandTypeClient client_command = Command.CommandTypeClient.DEFAULT;

            while (client_command != Command.CommandTypeClient.QUIT) {
                int client_cmd_ord = in.readInt();
                client_command = Command.CommandTypeClient.values()[client_cmd_ord];

                System.out.println("Received: " + client_command.toString());
                handleCommand(client_command);
            }

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                out.close();
                in.close();
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
