package org.Workers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

import org.Domain.Cart.CartStatus;
import org.Domain.Cart.ReadableCart;
import org.Domain.Cart.ServerCart;
import org.Domain.CheckoutResultWrapper;
import org.Domain.Product;
import org.Domain.Shop;
import org.Filters.Filter;
import org.ReducerSide.Reducer;
import org.ReducerSide.ReducerPreparationType;
import org.ServerSide.MasterServer;

public class WorkerClient extends Thread {

    private int id;
    private Socket socket;
    private ObjectInputStream server_input_stream;
    private ObjectOutputStream server_output_stream;

    private ObjectInputStream reducer_input_stream;
    private ObjectOutputStream reducer_output_stream;

    private HashMap<Integer, ArrayList<Shop>> managed_shops = new HashMap<>();

    public ArrayList<Shop> applyFilters(int worker_id) throws IOException, ClassNotFoundException {

        ArrayList<Shop> shops_to_work_on = getShopListFromId(worker_id);

        @SuppressWarnings("unchecked")
        ArrayList<Filter> received_filters = (ArrayList<Filter>) server_input_stream.readObject();

        return shops_to_work_on.stream()
                .filter(shop -> received_filters.stream().allMatch(filter -> filter.satisfy(shop)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Integer addToCart(Shop shop) throws IOException {

        Integer product_id = server_input_stream.readInt();
        int quantity = server_input_stream.readInt();

        Product product = shop.getProductById(product_id);
        if (product.getAvailableAmount() >= quantity) {
            return product.getId();
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

    public CheckoutResultWrapper checkout(Shop shop, ServerCart serverCart) throws IOException {

        float balance = server_input_stream.readFloat();

        ReadableCart in_sync_cart = getActualCart(shop, serverCart);
        CheckoutResultWrapper result = new CheckoutResultWrapper();

        result.in_sync_status = in_sync_cart.getServer_sync_status();

        if (in_sync_cart.getServer_sync_status() == CartStatus.OUT_OF_SYNC) {
            System.out.println("Cart was out of sync.");
            result.checked_out = false;
            return result;
        }

        float total_cost = getCartCost(in_sync_cart);
        System.out.println("total_cost = " + total_cost);
        if (total_cost <= balance) {
            System.out.println("Transaction was successful.");
            checkout_cart(shop, serverCart);
            result.checked_out = true;
        } else {
            System.out.println("Transaction was not successful. Insufficient funds.");
            result.checked_out = false;
        }
        return result;
    }

    private float getCartCost(Shop shop, ServerCart serverCart) {
        float cost = 0;
        for (Map.Entry<Integer, Integer> entry : serverCart.getProducts().entrySet()) {
            Product cart_product = shop.getProductById(entry.getKey());
            cost += cart_product.getPrice() * entry.getValue();
        }
        return cost;
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

        resulting_cart.setServer_sync_status(CartStatus.IN_SYNC);
        cart.getProducts().forEach((product_id, quantity) -> {
                    Product product = correspondingShop.getProductById(product_id);
                    if (product.getAvailableAmount() >= quantity && !product.is_removed())
                        resulting_cart.getProduct_quantity_map().put(product, quantity);
                    else
                        resulting_cart.setServer_sync_status(CartStatus.OUT_OF_SYNC);
                }
        );
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
        out.writeInt(worker_id);
        out.writeLong(request_id);
        out.writeObject(result);
        out.flush();
    }

    private void handleCommand(long request_id, int worker_id, WorkerCommandType command) throws IOException, ClassNotFoundException {
        switch (command) {

            case ADD_SHOP -> {
                Shop new_shop = (Shop) server_input_stream.readObject();

                ArrayList<Shop> shop_list = getShopListFromId(worker_id);
                shop_list.add(new_shop);
            }

            case ADD_PRODUCT_TO_SHOP -> {
                int shop_id = server_input_stream.readInt();
                Product new_product = (Product) server_input_stream.readObject();

                Shop shop = getShopFromId(worker_id, shop_id);
                shop.addProduct(new_product);
            }

            case REMOVE_PRODUCT_FROM_SHOP -> {
                int shop_id = server_input_stream.readInt();
                int product_id = server_input_stream.readInt();

                Shop shop = getShopFromId(worker_id, shop_id);
                Product product = shop.getProductById(product_id);

                product.set_removed_status(true);
            }

            case FILTER -> {
                ArrayList<Shop> shops = applyFilters(worker_id);

                send(reducer_output_stream, request_id, worker_id, shops);
            }
            case CHOSE_SHOP -> {
                int chosen_shop_id = server_input_stream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                System.out.println("Sending shop back " + corresponding_shop);
                send(server_output_stream, request_id, worker_id, corresponding_shop);
            }
            case ADD_TO_CART -> {
                int chosen_shop_id = server_input_stream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                Integer added_to_cart = addToCart(corresponding_shop);
                send(server_output_stream, request_id, worker_id, added_to_cart);
            }
            case GET_CART -> {
                int chosen_shop_id = server_input_stream.readInt();
                ServerCart cart = (ServerCart) server_input_stream.readObject();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ReadableCart result_cart = getActualCart(corresponding_shop, cart);

                Float total_cost = getCartCost(result_cart);
                result_cart.setTotal_cost(total_cost);

                send(server_output_stream, request_id, worker_id, result_cart);
            }
            case CHECKOUT_CART -> {
                int chosen_shop_id = server_input_stream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ServerCart serverCart = (ServerCart) server_input_stream.readObject();
                CheckoutResultWrapper checked_out = checkout(corresponding_shop, serverCart);

                send(server_output_stream, request_id, worker_id, checked_out);
            }
            case SYNC_CHECKOUT_CART -> {
                int chosen_shop_id = server_input_stream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ServerCart serverCart = (ServerCart) server_input_stream.readObject();

                checkout_cart(corresponding_shop, serverCart);
            }
            default -> {
                System.out.println("Command not implemented yet: " + command);
            }
        }
    }

    void connectToServer() {
        try {

            socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);

            server_output_stream = new ObjectOutputStream(socket.getOutputStream());
            server_input_stream = new ObjectInputStream(socket.getInputStream());

            System.out.println("Worker connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);

        } catch (IOException socket_exception) {
            socket_exception.printStackTrace();
        }
    }

    void connectToReducer(){
        try {

            socket = new Socket(Reducer.REDUCER_HOST, Reducer.REDUCER_WORKER_PORT);

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
            connectToServer();

            id = server_input_stream.readInt();
            @SuppressWarnings("unchecked")
            ArrayList<Shop> main_shop_list = (ArrayList<Shop>) server_input_stream.readObject();
            managed_shops.put(id, main_shop_list);

            connectToReducer();

            int worker_command_ord = server_input_stream.readInt();
            WorkerCommandType worker_command = WorkerCommandType.values()[worker_command_ord];

            while (worker_command != WorkerCommandType.END_BACKUP_LIST) {

                int worker_backup_id = server_input_stream.readInt();

                @SuppressWarnings("unchecked")
                ArrayList<Shop> worker_managed_shops = (ArrayList<Shop>) server_input_stream.readObject();
                System.out.println("Worker id: " + id + " Received " + worker_backup_id + " and list with " + worker_managed_shops.size() + " shops");

                managed_shops.put(worker_backup_id, new ArrayList<>(worker_managed_shops));
                System.out.println("Worker " + id + " in map: " + managed_shops.get(worker_backup_id).size());

                worker_command_ord = server_input_stream.readInt();
                worker_command = WorkerCommandType.values()[worker_command_ord];
            }

            worker_command = WorkerCommandType.values()[server_input_stream.readInt()];
            while (worker_command != WorkerCommandType.QUIT) {

                long request_id = server_input_stream.readLong();
                int worker_id = server_input_stream.readInt();

                handleCommand(request_id, worker_id, worker_command);
                worker_command = WorkerCommandType.values()[server_input_stream.readInt()];
            }
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
