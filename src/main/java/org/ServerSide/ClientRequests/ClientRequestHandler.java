package org.ServerSide.ClientRequests;

import org.Domain.Location;
import org.Domain.Shop;
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

public class ClientRequestHandler extends Thread {

    private ObjectInputStream in;
    private ObjectOutputStream out;
    private Socket connection;

    private Location client_location;

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

        } catch (IOException ioException) {
            System.out.println("Exception at: ");
            System.out.println("ClientRequestHandler.ClientRequestHandler");
            ioException.printStackTrace();
            _connection.close();
        }
    }

    private ArrayList<Filter> readFilterCommand() throws IOException, ClassNotFoundException {
        ArrayList<Filter> filters = new ArrayList<>();

        int ord_filter = in.readInt();
        Filter.Types specific_filters = Filter.Types.values()[ord_filter];

        while(specific_filters != Filter.Types.END){
            System.out.println("Received filter " + specific_filters.toString() + " with arguments: ");
            Filter filter = switch (specific_filters){
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

    public void handleCommand(Command.CommandTypeClient _client_command) throws IOException, ClassNotFoundException, InterruptedException {

        long requestId = threadId();

        switch (_client_command){
            case QUIT, DEFAULT, END: break;
            case FILTER:
                ArrayList<Filter> filters = readFilterCommand();
                System.out.println("Received Filters: ");
                for(Filter f: filters){
                    System.out.println(f.getClass().getName());
                }

                ArrayList<RequestMonitor> monitors = new ArrayList<>();

                for(ReplicationHandler replicated_worker: replicated_worker_handlers){
                    WorkerHandler main_handler = replicated_worker.getMain();

                    RequestMonitor monitor = new RequestMonitor();
                    ObjectOutputStream worker_out = main_handler.getWorker_out();
                    boolean successfully_sent = false;

                    try {
                        main_handler.registerMonitor(requestId, monitor);

                        synchronized (worker_out) {
                            worker_out.writeInt(Command.CommandTypeClient.FILTER.ordinal());
                            worker_out.writeLong(requestId);
                            worker_out.writeObject(filters);
                            worker_out.flush();
                        }
                        successfully_sent = true;

                    } catch (IOException e) {
                        System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

                        for(WorkerHandler rep_handler: replicated_worker.getReplicas()){

                            ObjectOutputStream rep_worker_out = rep_handler.getWorker_out();

                            try {
                                rep_handler.registerMonitor(requestId, monitor);
                                synchronized (rep_worker_out) {
                                    rep_worker_out.writeInt(Command.CommandTypeClient.FILTER.ordinal());
                                    rep_worker_out.writeLong(requestId);
                                    rep_worker_out.writeObject(filters);
                                    rep_worker_out.flush();
                                }
                                replicated_worker.add_replica(main_handler);
                                replicated_worker.setMain(rep_handler);
                                replicated_worker.getMain().start();
                                successfully_sent = true;
                                break;
                            } catch (IOException ex) {
                                System.err.println("Replica failed.");
                            }
                        }
                    }

                    if(!successfully_sent){
                        System.err.println("Main worker and all replicas failed sending command and arguments for this chunk.");
                    }
                    monitors.add(monitor);
                }

                ArrayList<Shop> resulting_shops = new ArrayList<>();

                for(RequestMonitor monitor: monitors){
                    @SuppressWarnings("unchecked")
                    ArrayList<Shop> filtered_worker_shops = (ArrayList<Shop>) monitor.getResult();

                    resulting_shops.addAll(filtered_worker_shops);
                }
                out.writeObject(resulting_shops);
                out.flush();

                break;
            case ADD_TO_CART: break;
            case REMOVE_FROM_CART:break;
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("New client connected: " + connection.getInetAddress());

            client_location = (Location) in.readObject();
            System.out.println("client_location = " + client_location);

            Command.CommandTypeClient client_command = Command.CommandTypeClient.DEFAULT;

            while(client_command != Command.CommandTypeClient.QUIT){
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
