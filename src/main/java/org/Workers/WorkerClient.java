package org.Workers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

import org.Domain.Cart.CartStatus;
import org.Domain.Cart.ReadableCart;
import org.Domain.Cart.ServerCart;
import org.Domain.CheckoutResultWrapper;
import org.Domain.Product;
import org.Domain.Shop;
import org.Domain.Utils.Pair;
import org.Filters.Filter;
import org.MessagePKG.Message;
import org.MessagePKG.MessageType;
import org.ReducerSide.Reducer;
import org.ServerSide.MasterServer;

import static org.ServerSide.MasterServer.getWifiInetAddress;

public class WorkerClient extends Thread {

    private int id;
    private Socket socket;
    private ObjectInputStream server_input_stream;
    private ObjectOutputStream server_output_stream;
    InetAddress wifiAddress;

    private ObjectInputStream reducer_input_stream;
    private ObjectOutputStream reducer_output_stream;

    private HashMap<Integer, ArrayList<Shop>> managed_shops = new HashMap<>();

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

    public CheckoutResultWrapper checkout(Shop shop, ServerCart serverCart, float balance) throws IOException {

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
                        if (product.getAvailableAmount() >= quantity && !product.is_removed())
                            resulting_cart.getProduct_quantity_map().put(product, quantity);
                        else
                            resulting_cart.setServer_sync_status(CartStatus.OUT_OF_SYNC);
                    }
            );
        }
        return resulting_cart;
    }

    public ArrayList<Shop> getShopListFromId(int worker_id) {
        return managed_shops.get(worker_id);
    }

    public Shop getShopFromId(int worker_id, int shop_id) {
        ArrayList<Shop> shop_list = getShopListFromId(worker_id);

        return shop_list.stream().filter(shop -> shop_id == shop.getId()).findFirst().orElse(null);
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

    private void handleCommand(long request_id, int worker_id, MessageType command, Message message) throws IOException, ClassNotFoundException {
        switch (command) {

            case ADD_SHOP, SYNC_ADD_SHOP -> {
                Shop new_shop = message.getArgument("new_shop");

                ArrayList<Shop> shop_list = getShopListFromId(worker_id);
                shop_list.add(new_shop);
            }

            case ADD_PRODUCT_TO_SHOP, SYNC_ADD_PRODUCT_TO_SHOP -> {
                int shop_id = message.getArgument("shop_id");
                Product new_product = message.getArgument("new_product");

                Shop shop = getShopFromId(worker_id, shop_id);
                shop.addProduct(new_product);
            }

            case REMOVE_PRODUCT_FROM_SHOP, SYNC_REMOVE_PRODUCT_FROM_SHOP -> {
                int shop_id = message.getArgument("shop_id");
                int product_id = message.getArgument("product_id");

                Shop shop = getShopFromId(worker_id, shop_id);
                Product product = shop.getProductById(product_id);

                product.set_removed_status(true);
            }

            case ADD_PRODUCT_STOCK, SYNC_ADD_PRODUCT_STOCK -> {
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

            case REMOVE_PRODUCT_STOCK, SYNC_REMOVE_PRODUCT_STOCK -> {
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

            case GET_SHOP_CATEGORY_SALES -> {
                String category = message.getArgument("category");

                ArrayList<Shop> shops = getShopListFromId(worker_id);
                ArrayList<Pair<String, Integer>> shop_sales;

                synchronized (shops) {
                    shop_sales = getShopCategorySales(shops, category);
                }

                if(id == 0)
                    System.out.println("Total sales for worker " + id + ": " + shop_sales);



                send(reducer_output_stream, request_id, worker_id, shop_sales);
            }

            case GET_PRODUCT_CATEGORY_SALES -> {
                String category = message.getArgument("category");

                ArrayList<Shop> shops = getShopListFromId(worker_id);
                ArrayList<Pair<String, Integer>> shop_product_sales;

                synchronized (shops) {
                    shop_product_sales = getProductCategorySales(shops, category);
                }

                send(reducer_output_stream, request_id, worker_id, shop_product_sales);
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

                send(server_output_stream, request_id, worker_id, corresponding_shop);
            }
            case ADD_TO_CART -> {
                int chosen_shop_id = message.getArgument("shop_id");
                int product_id = message.getArgument("product_id");
                int quantity = message.getArgument("quantity");

                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                Integer added_to_cart = addToCart(corresponding_shop, product_id, quantity);
                send(server_output_stream, request_id, worker_id, added_to_cart);
            }
            case GET_CART -> {
                int chosen_shop_id = message.getArgument("shop_id");
                ServerCart cart = message.getArgument("cart");
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ReadableCart result_cart = getActualCart(corresponding_shop, cart);

                Float total_cost = getCartCost(result_cart);
                result_cart.setTotal_cost(total_cost);

                send(server_output_stream, request_id, worker_id, result_cart);
            }
            case CHECKOUT_CART -> {
                int chosen_shop_id = message.getArgument("shop_id");
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ServerCart serverCart = message.getArgument("cart");
                float balance = message.getArgument("balance");
                CheckoutResultWrapper checked_out = checkout(corresponding_shop, serverCart, balance);

                send(server_output_stream, request_id, worker_id, checked_out);
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
        }
    }

    private ArrayList<Pair<String, Integer>> getProductCategorySales(ArrayList<Shop> shops, String category) {
        synchronized (shops) {
            return shops.stream()
                    .filter(shop -> shop.getCategory().equals(category))
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

            socket = new Socket(wifiAddress, MasterServer.SERVER_CLIENT_PORT);
            server_output_stream = new ObjectOutputStream(socket.getOutputStream());
            server_input_stream = new ObjectInputStream(socket.getInputStream());

            System.out.println("Worker connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);

        } catch (IOException socket_exception) {
            socket_exception.printStackTrace();
        }
    }

    void connectToReducer(){
        try {

            socket = new Socket(wifiAddress, Reducer.REDUCER_WORKER_PORT);

            reducer_output_stream = new ObjectOutputStream(socket.getOutputStream());
            reducer_input_stream = new ObjectInputStream(socket.getInputStream());

            reducer_output_stream.writeInt(id);
            reducer_output_stream.flush();

            System.out.println("Worker connected with reducer at port: " + Reducer.REDUCER_WORKER_PORT);

        } catch (IOException socket_exception) {
            socket_exception.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            wifiAddress = getWifiInetAddress();
            connectToServer();

            id = server_input_stream.readInt();
            @SuppressWarnings("unchecked")
            ArrayList<Shop> main_shop_list = (ArrayList<Shop>) server_input_stream.readObject();
            managed_shops.put(id, main_shop_list);

            connectToReducer();

            int worker_command_ord = server_input_stream.readInt();
            MessageType worker_command = MessageType.values()[worker_command_ord];

            while (worker_command != MessageType.END_BACKUP_LIST) {

                int worker_backup_id = server_input_stream.readInt();

                @SuppressWarnings("unchecked")
                ArrayList<Shop> worker_managed_shops = (ArrayList<Shop>) server_input_stream.readObject();
                System.out.println("Worker id: " + id + " Received " + worker_backup_id + " and list with " + worker_managed_shops.size() + " shops");

                managed_shops.put(worker_backup_id, new ArrayList<>(worker_managed_shops));
                System.out.println("Worker " + id + " in map: " + managed_shops.get(worker_backup_id).size());

                worker_command_ord = server_input_stream.readInt();
                worker_command = MessageType.values()[worker_command_ord];
            }

            MessageType worker_command_c;
            do {
                synchronized (server_input_stream) {
                    worker_command_c = MessageType.values()[server_input_stream.readInt()];
                    final MessageType worker_command_fc = worker_command_c;
                    final long request_id = server_input_stream.readLong();
                    final int worker_id = server_input_stream.readInt();
                    final Message message = (Message) server_input_stream.readObject();


                    System.out.println("Got " + worker_command_fc);
                    System.out.println("With arguments: " + message);

                    new Thread(() -> {
                        try {
                            handleCommand(request_id, worker_id, worker_command_fc, message);
                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }

            }while(worker_command_c != MessageType.QUIT);

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
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
