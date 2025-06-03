package com.example.client_efood.Workers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

import com.example.client_efood.Domain.Cart.CartStatus;
import com.example.client_efood.Domain.Cart.ReadableCart;
import com.example.client_efood.Domain.Cart.ServerCart;
import com.example.client_efood.Domain.CheckoutResultWrapper;
import com.example.client_efood.Domain.Product;
import com.example.client_efood.Domain.Shop;
import com.example.client_efood.Domain.Utils.Pair;
import com.example.client_efood.Filters.Filter;
import com.example.client_efood.MessagePKG.Message;
import com.example.client_efood.MessagePKG.MessageArgCast;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.ReducerSide.Reducer;
import com.example.client_efood.ServerSide.ConnectionType;
import com.example.client_efood.ServerSide.MasterServer;

public class WorkerClient {

    private int id;
    private ObjectInputStream server_input_stream;
    private ObjectOutputStream server_output_stream;

    private ObjectInputStream reducer_input_stream;
    private ObjectOutputStream reducer_output_stream;

    private HashMap<Integer, ArrayList<Shop>> managed_shops = new HashMap<>();

    private HashMap<Integer, ChangeLog> worker_change_log = new HashMap<>();

    private int listening_port = 8888;

    public ArrayList<Shop> applyFilters(int worker_id, ArrayList<Filter> received_filters) throws IOException, ClassNotFoundException {

        ArrayList<Shop> shops_to_work_on = getShopListFromId(worker_id);

        if(received_filters.isEmpty())
            return shops_to_work_on;

        return shops_to_work_on.stream()
                .filter(shop -> received_filters.stream().allMatch(filter -> filter.satisfy(shop)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Integer addToCart(Shop shop, int product_id, int quantity) throws IOException {

        Product product = shop.getProductById(product_id);
        synchronized (product) {
            if (!product.is_removed() && product.getAvailableAmount() >= quantity) {
                return product.getId();
            }
        }

        return -1;
    }

    public void checkout_cart(Shop shop, ServerCart serverCart) {
        synchronized (shop) {
            serverCart.getProducts().forEach((product_id, quantity) -> {
                Product cart_product = shop.getProductById(product_id);
                cart_product.removeAvailableAmount(quantity);
                cart_product.sellProduct(quantity);
            });
        }
    }

    public CheckoutResultWrapper checkout(Shop shop, ServerCart serverCart, float balance) {

        synchronized (shop) {
            ReadableCart in_sync_cart = getActualCart(shop, serverCart);
            CheckoutResultWrapper result = new CheckoutResultWrapper();

            if (in_sync_cart.getServer_sync_status() == CartStatus.OUT_OF_SYNC) {
                System.out.println("Cart was out of sync.");
                result.checked_out = false;
                result.in_sync_status = CartStatus.OUT_OF_SYNC;
                return result;
            }

            float total_cost = getCartCost(in_sync_cart);
            System.out.println("total_cost = " + total_cost);
            if (total_cost <= balance) {
                checkout_cart(shop, serverCart);

                System.out.println("Transaction was successful.");
                result.checked_out = true;
            } else {
                System.out.println("Transaction was not successful. Insufficient funds.");
                result.checked_out = false;
            }
            return result;
        }
    }

    private float getCartCost(ReadableCart cart) {
        float cost = 0;
        for (Map.Entry<Product, Integer> entry : cart.getProduct_quantity_map().entrySet()) {
            cost += entry.getKey().getPrice() * entry.getValue();
        }
        return cost;
    }

    private ReadableCart getActualCart(Shop correspondingShop, ServerCart cart) {
        ReadableCart resulting_cart = new ReadableCart();

        synchronized (correspondingShop) {
            resulting_cart.setServer_sync_status(CartStatus.IN_SYNC);
            cart.getProducts().forEach((product_id, quantity) -> {
                        Product product = correspondingShop.getProductById(product_id);
                        if (product != null && !product.is_removed() && product.getAvailableAmount() >= quantity)
                            resulting_cart.getProduct_quantity_map().put(product, quantity);
                        else
                            resulting_cart.setServer_sync_status(CartStatus.OUT_OF_SYNC);
                    }
            );
        }
        return resulting_cart;
    }

    private void rateShop(Shop corresponding_shop, float rating) {
        synchronized (corresponding_shop){
            corresponding_shop.updateRating(rating);
        }
    }

    public ArrayList<Shop> getShopListFromId(int worker_id) {
        return managed_shops.get(worker_id);
    }

    public Shop getShopFromId(int worker_id, int shop_id) {
        ArrayList<Shop> shop_list = getShopListFromId(worker_id);
        if(shop_list == null){
            System.out.println("shop_list for shop with id " + shop_id + " doesn't exist in worker with id " + worker_id);
            return null;
        }
        else if(shop_list.isEmpty()){
            System.out.println("shop_list for shop with id " + shop_id + " is empty in worker with id " + worker_id);
            return null;
        }

        for(Shop s_shop: shop_list){
            if(shop_id == s_shop.getId()) {
                return s_shop;
            }
        }
        System.out.println("Didn't find shop...");
        return null;
    }

    private void send(ObjectOutputStream out, long request_id, int worker_id, Object result) throws IOException {
        synchronized (out) {
            out.reset();
            out.writeInt(worker_id);
            out.writeLong(request_id);
            out.writeObject(result);
            out.flush();
        }
    }

    private void handleAddShop(Message message, int worker_id){
        Shop new_shop = message.getArgument("new_shop");
        Shop new_shop_cp = new Shop(new_shop);

        ArrayList<Shop> shop_list = getShopListFromId(worker_id);
        synchronized (shop_list) {
            shop_list.add(new_shop_cp);
        }
    }

    private void handleAddOldProduct(Message message, int worker_id){
        int shop_id = message.getArgument("shop_id");
        int product_id = message.getArgument("product_id");

        Shop shop = getShopFromId(worker_id, shop_id);
        Product product = shop.getProductById(product_id);

        synchronized (product) {
            product.set_removed_status(false);
        }
    }

    private void handleAddProduct(Message message, int worker_id){
        int shop_id = message.getArgument("shop_id");
        Product new_product = message.getArgument("new_product");
        Product new_product_cp = new Product(new_product);

        Shop shop = getShopFromId(worker_id, shop_id);
        shop.addProduct(new_product_cp);
    }

    private void handleRemoveProduct(Message message, int worker_id){
        int shop_id = message.getArgument("shop_id");
        int product_id = message.getArgument("product_id");

        Shop shop = getShopFromId(worker_id, shop_id);
        Product product = shop.getProductById(product_id);

        synchronized (product) {
            product.set_removed_status(true);
        }
    }

    private void handleAddProductStock(Message message, int worker_id){
        int shop_id = message.getArgument("shop_id");
        int product_id = message.getArgument("product_id");
        int quantity = message.getArgument("quantity");

        Shop shop = getShopFromId(worker_id, shop_id);
        Product product = shop.getProductById(product_id);

        System.out.println("Product: " + product);

        synchronized (shop) {
            System.out.println("Previous: " + product);
            product.addAvailableAmount(quantity);
            System.out.println("Updated: " + product);
        }
    }

    private void handleRemoveProductStock(Message message, int worker_id){
        int shop_id = message.getArgument("shop_id");
        int product_id = message.getArgument("product_id");
        int quantity = message.getArgument("quantity");

        Shop shop = getShopFromId(worker_id, shop_id);
        Product product = shop.getProductById(product_id);

        synchronized (shop) {
            System.out.println("Product: " + product);

            System.out.println("Previous stock: " + product.getAvailableAmount());
            product.removeAvailableAmount(quantity);
            System.out.println("New stock: " + product.getAvailableAmount());
        }
    }

    private void handleGiveRating(Message message, int worker_id){
        int chosen_shop_id = message.getArgument("shop_id");
        Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);
        float rating = message.getArgument("rating");

        rateShop(corresponding_shop, rating);
    }

    private CheckoutResultWrapper handleCheckout(Message message, int worker_id) {
        int chosen_shop_id = message.getArgument("shop_id");
        Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

        ServerCart serverCart = message.getArgument("cart");
        float balance = message.getArgument("balance");
        CheckoutResultWrapper checked_out = checkout(corresponding_shop, serverCart, balance);

        return checked_out;
    }

    private void handleCommand(Message message, ObjectOutputStream out) throws IOException, ClassNotFoundException {

        System.out.println("Got message: " + message);

        long request_id = message.getArgument("request_id");
        int worker_id = message.getArgument("worker_id");

        int command_ord = message.getArgument("command_ord");
        MessageType command = MessageType.values()[command_ord];

        ChangeLog change_log = worker_change_log.get(worker_id);

        switch (command) {

            case IS_WORKER_ALIVE -> {
                Boolean result = true;
                send(out, request_id, worker_id, result);
            }

            case ADD_BACKUP -> {
                int worker_backup_id = message.getArgument("worker_backup_id");

                ArrayList<Shop> worker_managed_shops = message.getArgument("shop_list");
                System.out.println("Worker id: " + id + " Received " + worker_backup_id + " and list with " + worker_managed_shops.size() + " shops");

                System.out.println(System.identityHashCode(managed_shops));

                synchronized (managed_shops) {
                    if(managed_shops.get(worker_backup_id) == null) {
                        managed_shops.put(worker_backup_id, worker_managed_shops);
                        worker_change_log.put(worker_backup_id, new ChangeLog());
                    }
                }
                System.out.println("Worker " + worker_backup_id + " in map with shop list size: " + managed_shops.get(worker_backup_id).size());
            }

            case ADD_SHOP -> {
                handleAddShop(message, worker_id);

                Shop added_shop = message.getArgument("new_shop");
                Shop shop_cp = new Shop(added_shop);

                System.out.println("Added shop: " + System.identityHashCode(added_shop));
                System.out.println("Copy of shop: " + System.identityHashCode(shop_cp));

                message.replaceArgument("new_shop", new Pair<>(MessageArgCast.SHOP_ARG, shop_cp));

                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }
            }

            case SYNC_ADD_SHOP -> {
                handleAddShop(message, worker_id);
            }

            case ADD_OLD_PRODUCT_TO_SHOP -> {
                handleAddOldProduct(message, worker_id);

                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }
            }

            case SYNC_ADD_OLD_PRODUCT_TO_SHOP -> {
                handleAddOldProduct(message, worker_id);
            }

            case ADD_PRODUCT_TO_SHOP -> {
                handleAddProduct(message, worker_id);

                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }
            }

            case SYNC_ADD_PRODUCT_TO_SHOP -> {
                handleAddProduct(message, worker_id);
            }

            case REMOVE_PRODUCT_FROM_SHOP -> {
                handleRemoveProduct(message, worker_id);

                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }
            }

            case SYNC_REMOVE_PRODUCT_FROM_SHOP -> {
                handleRemoveProduct(message, worker_id);
            }

            case ADD_PRODUCT_STOCK -> {
                handleAddProductStock(message, worker_id);

                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }
            }

            case SYNC_ADD_PRODUCT_STOCK -> {
                handleAddProductStock(message, worker_id);
            }

            case REMOVE_PRODUCT_STOCK -> {
                handleRemoveProductStock(message, worker_id);

                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }
            }

            case SYNC_REMOVE_PRODUCT_STOCK -> {
                handleRemoveProductStock(message, worker_id);
            }

            case GET_SHOP_CATEGORY_SALES -> {
                String category = message.getArgument("category");

                ArrayList<Shop> shops = getShopListFromId(worker_id);
                ArrayList<Pair<String, Integer>> shop_sales;

                synchronized (shops) {
                    shop_sales = getShopCategorySales(shops, category);
                }

                if(id == 0)
                    System.out.println("Total sales for worker " + id + ": " + shop_sales);

                send(reducer_output_stream, request_id, id, shop_sales);
            }

            case GET_PRODUCT_CATEGORY_SALES -> {
                String category = message.getArgument("category");

                ArrayList<Shop> shops = getShopListFromId(worker_id);
                ArrayList<Pair<String, Integer>> shop_product_sales;

                shop_product_sales = getProductCategorySales(shops, category);

                send(reducer_output_stream, request_id, id, shop_product_sales);
            }

            case FILTER -> {

                ArrayList<Filter> filters = message.getArgument("filter_list");

                ArrayList<Shop> shops = applyFilters(worker_id, filters);

                send(reducer_output_stream, request_id, worker_id, shops);
            }
            case CHOSE_SHOP -> {
                int chosen_shop_id = message.getArgument("shop_id");
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                System.out.println("Sending shop back " + corresponding_shop);

                send(out, request_id, id, corresponding_shop);
            }
            case ADD_TO_CART -> {
                int chosen_shop_id = message.getArgument("shop_id");
                int product_id = message.getArgument("product_id");
                int quantity = message.getArgument("quantity");

                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                Integer added_to_cart = addToCart(corresponding_shop, product_id, quantity);
                send(out, request_id, id, added_to_cart);
            }
            case GET_CART -> {
                int chosen_shop_id = message.getArgument("shop_id");
                ServerCart cart = message.getArgument("cart");
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ReadableCart result_cart = getActualCart(corresponding_shop, cart);

                Float total_cost = getCartCost(result_cart);
                result_cart.setTotal_cost(total_cost);

                send(out, request_id, id, result_cart);
            }
            case CHECKOUT_CART -> {

                CheckoutResultWrapper checked_out = handleCheckout(message, worker_id);

                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }

                send(out, request_id, id, checked_out);
            }
            case SYNC_CHECKOUT_CART -> {
                int chosen_shop_id = message.getArgument("shop_id");
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ServerCart serverCart = message.getArgument("cart");

                checkout_cart(corresponding_shop, serverCart);
            }
            default -> {
                System.out.println("Command not implemented yet: " + command);
            }

            case GIVE_RATING -> {

                handleGiveRating(message, worker_id);
                synchronized (change_log) {
                    change_log.addChange(new DataChange(message));
                }

                Boolean gave_rating = true;
                send(out, request_id, id, gave_rating);
            }

            case SYNC_GIVE_RATING -> {
                handleGiveRating(message, worker_id);
            }

            case SYNC_CHANGES -> {

                String replica_host = message.getArgument("replica_host");
                int replica_port = message.getArgument("replica_port");

                ObjectInputStream sync_in = null;
                ObjectOutputStream sync_out = null;
                Socket sync_socket = null;
                try {
                    sync_socket = new Socket(replica_host, replica_port);

                    sync_out = new ObjectOutputStream(sync_socket.getOutputStream());
                    sync_in = new ObjectInputStream(sync_socket.getInputStream());

                    sync_out.writeInt(ConnectionType.WORKER.ordinal());
                    sync_out.flush();

                    Message log_message = new Message();
                    log_message.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.SYNC_CHANGES.ordinal()));
                    if(change_log == null)
                        log_message.addArgument("change_log", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, new ArrayList<>()));
                    else
                        log_message.addArgument("change_log", new Pair<>(MessageArgCast.ARRAY_LIST_ARG, change_log.getChanges()));
                    log_message.addArgument("worker_id", new Pair<>(MessageArgCast.INT_ARG, worker_id));

                    System.out.println("Worker " + id +  " Before syncing: " + change_log);

                    sync_out.writeObject(log_message);
                    sync_out.flush();

                    @SuppressWarnings("unchecked")
                    ArrayList<DataChange> other_changes = (ArrayList<DataChange>) sync_in.readObject();

                    System.out.println("Worker " + id + ": Got changes: " + other_changes);

                    for (DataChange dt_change : other_changes) {
                        if (!change_log.contains(dt_change)) {
                            applyChange(dt_change);
                            synchronized (change_log) {
                                change_log.addChange(dt_change);
                            }
                        }
                    }

                    Message quit_msg = new Message();
                    quit_msg.addArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, MessageType.QUIT.ordinal()));

                    sync_out.reset();
                    sync_out.writeObject(quit_msg);
                    sync_out.flush();

                    sync_in.close();
                    sync_out.close();
                    sync_socket.close();

                } catch (IOException e) {
                    e.printStackTrace();

                    if(sync_in != null)
                        sync_in.close();
                    if(sync_out != null)
                        sync_out.close();
                    if(sync_socket != null)
                        sync_socket.close();
                }

                System.out.println("Worker " + id +  " Change log now: " + change_log);
            }
        }
    }

    private void applyChange(DataChange dt_change) throws IOException, ClassNotFoundException {
        System.out.println("Worker " + id + ": Applying change: " + dt_change);

        Message change = dt_change.getMessage();

        int command_ord = change.getArgument("command_ord");
        MessageType command = MessageType.values()[command_ord];

        final String sync_prefix = "SYNC_";

        System.out.println(command + " -> " + sync_prefix + command);
        MessageType sync_command = MessageType.valueOf(sync_prefix + command);

        final Message change_cp = new Message(change);

        change_cp.replaceArgument("command_ord", new Pair<>(MessageArgCast.INT_ARG, sync_command.ordinal()));

        handleCommand(change_cp, null);
    }

    private ArrayList<Pair<String, Integer>> getProductCategorySales(ArrayList<Shop> shops, String category) {
        synchronized (shops) {
            return shops.stream()
                    .map(shop -> new Pair<>(shop.getName(), shop.getTotalSalesForProductType(category)))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private ArrayList<Pair<String, Integer>> getShopCategorySales(ArrayList<Shop> shops, String category) {
        synchronized (shops) {
            return shops.stream()
                    .filter(shop -> shop.getCategory().equals(category))
                    .map(shop -> new Pair<>(shop.getName(), shop.getTotalSales()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    void connectToServer() {
        try {

            Socket socket = new Socket(MasterServer.SERVER_HOST, MasterServer.SERVER_CLIENT_PORT);
            server_output_stream = new ObjectOutputStream(socket.getOutputStream());
            server_input_stream = new ObjectInputStream(socket.getInputStream());

            server_output_stream.writeInt(ConnectionType.WORKER.ordinal());
            server_output_stream.flush();

            System.out.println("Worker connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);

        } catch (IOException socket_exception) {
            socket_exception.printStackTrace();
        }
    }

    void connectToReducer(){
        try {

            System.out.println("Trying to connect with reducer...");
            Socket socket = new Socket(Reducer.REDUCER_HOST, Reducer.REDUCER_WORKER_PORT);

            reducer_output_stream = new ObjectOutputStream(socket.getOutputStream());
            reducer_input_stream = new ObjectInputStream(socket.getInputStream());
            System.out.println("Got reducer's streams.");

            Message msg = new Message();
            msg.addArgument("connection_type", new Pair<>(MessageArgCast.INT_ARG, ConnectionType.WORKER.ordinal()));
            msg.addArgument("id", new Pair<>(MessageArgCast.INT_ARG, id));

            reducer_output_stream.writeObject(msg);
            reducer_output_stream.flush();

            System.out.println("Worker connected with reducer at port: " + Reducer.REDUCER_WORKER_PORT);

        } catch (IOException socket_exception) {
            socket_exception.printStackTrace();
        }
    }

    void manageClientCommands(ObjectOutputStream out, ObjectInputStream in){
        try {

            MessageType worker_command = null;
            do {

                Message message = (Message) in.readObject();

                try {
                    int command_ord = message.getArgument("command_ord");
                    worker_command = MessageType.values()[command_ord];
                }catch (NullPointerException e){
                    e.printStackTrace();
                    continue;
                }

                new Thread(() -> {
                    try {
                        handleCommand(message, out);
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }).start();

            }while(worker_command != MessageType.QUIT);

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void manageWorkerCommands(ObjectOutputStream out, ObjectInputStream in) throws IOException {
        try {

            MessageType worker_command;
            do {

                Message message = (Message) in.readObject();
                System.out.println("Received: " + message);

                int command_ord = message.getArgument("command_ord");
                worker_command = MessageType.values()[command_ord];

                if(worker_command == MessageType.QUIT)
                    break;

                MessageType finalWorker_command = worker_command;
                new Thread(() -> {
                    try {
                        if (finalWorker_command == MessageType.SYNC_CHANGES) {
                            ArrayList<DataChange> changes = message.getArgument("change_log");
                            int worker_id = message.getArgument("worker_id");

                            ChangeLog change_log = worker_change_log.get(worker_id);
                            if(change_log == null) {
                                worker_change_log.put(worker_id, new ChangeLog());
                                change_log = worker_change_log.get(worker_id);
                            }

                            System.out.println("Worker " + id + " Before syncing: " + change_log);

                            for (DataChange dt_change : changes) {
                                if (!change_log.contains(dt_change)) {
                                    applyChange(dt_change);
                                    synchronized (change_log) {
                                        change_log.addChange(dt_change);
                                    }
                                }
                            }

                            System.out.println("Worker " + id + " Change log now: " + change_log);

                            out.reset();
                            out.writeObject(change_log.getChanges());
                            out.flush();
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }).start();

            }while(worker_command != MessageType.QUIT);

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();

            out.close();
            in.close();
        }
    }

    void handleConnection(Socket socket){
        try{
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            int con_type_ord = in.readInt();
            ConnectionType connection_type = ConnectionType.values()[con_type_ord];

            if(connection_type == ConnectionType.CLIENT)
                manageClientCommands(out, in);
            else if(connection_type == ConnectionType.WORKER)
                manageWorkerCommands(out, in);


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void connectionHandler() {
        ServerSocket connection = null;
        try{
            connection = new ServerSocket(listening_port);

            synchronized (server_output_stream){
                server_output_stream.writeBoolean(true);
                server_output_stream.flush();
            }

            while(true){
                Socket connection_socket = connection.accept();

                (new Thread(() -> handleConnection(connection_socket))).start();
            }

        } catch (IOException e) {
            if(connection != null) {
                try {
                    connection.close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
            throw new RuntimeException(e);
        }
    }

    public void start() {
        try {

            connectToServer();

            id = server_input_stream.readInt();
            listening_port = server_input_stream.readInt();
            System.out.println("Got ID: " + id);
            System.out.println("Got port: " + listening_port);

            worker_change_log.put(id, new ChangeLog());

            @SuppressWarnings("unchecked")
            ArrayList<Shop> main_shop_list = (ArrayList<Shop>) server_input_stream.readObject();
            System.out.println("Got shop list with size: " + main_shop_list.size());
            managed_shops.put(id, main_shop_list);

            (new Thread(this::connectionHandler)).start();

            server_input_stream.readBoolean();

            (new Thread(this::connectToReducer)).start();

            MessageType worker_command;
            do {

                Message message = (Message) server_input_stream.readObject();

                int command_ord = message.getArgument("command_ord");
                worker_command = MessageType.values()[command_ord];

                new Thread(() -> {
                    try {
                        handleCommand(message, server_output_stream);
                    } catch (IOException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }).start();

            }while(worker_command != MessageType.QUIT);

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        WorkerClient worker = new WorkerClient();
        worker.start();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("Managed shops for worker with ID: ").append(id).append(" [\n");

        managed_shops.forEach((worker_id, shop_list) -> {
            str.append("ID: ").append(worker_id).append(" Shops: ").append(shop_list).append("\n");
        });

        str.append("]");

        return str.toString();
    }
}
