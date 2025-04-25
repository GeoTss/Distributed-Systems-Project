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
import org.ServerSide.MasterServer;

public class WorkerClient extends Thread {

    private static int gl_id = 0;

    private int id;
    private Socket socket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private ArrayList<Shop> managed_shops;
    private HashMap<Integer, ArrayList<Shop>> backup_shops;

    public WorkerClient() {
        id = gl_id++;
        backup_shops = new HashMap<>();
    }

    public ArrayList<Shop> applyFilters(int worker_id) throws IOException, ClassNotFoundException {

        ArrayList<Shop> shops_to_work_on = getShopListFromId(worker_id);

        @SuppressWarnings("unchecked")
        ArrayList<Filter> received_filters = (ArrayList<Filter>) inputStream.readObject();

        return shops_to_work_on.stream()
                .filter(shop -> received_filters.stream().allMatch(filter -> filter.satisfy(shop)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Integer addToCart(Shop shop) throws IOException {

        Integer product_id = inputStream.readInt();
        int quantity = inputStream.readInt();

        Product product = shop.getProductById(product_id);
        if (product.getAvailableAmount() >= quantity) {
            return product.getId();
        }

        return -1;
    }

    public void clearCart(Shop shop, ServerCart cart) {
        cart.getProducts().forEach((product_id, quantity) -> {
            Product product = shop.getProductById(product_id);
            synchronized (product) {
                product.addAvailableAmount(quantity);
            }
        });
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

        float balance = inputStream.readFloat();

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
        return (worker_id == -1 || worker_id == id) ? managed_shops : backup_shops.get(worker_id);
    }

    public Shop getShopFromId(int worker_id, int shop_id) {
        ArrayList<Shop> shop_list = getShopListFromId(worker_id);

        if (worker_id == -1 || worker_id == id)
            return shop_list.get(shop_id - managed_shops.size() * id);

        return shop_list.get(shop_id - MasterServer.getConfig_info().getWorker_chunk() * worker_id);
    }

    private void sendBack(long request_id, Object result) throws IOException {
        outputStream.writeLong(request_id);
        outputStream.writeObject(result);
        outputStream.flush();
    }

    private void handleCommand(long request_id, int worker_id, WorkerCommandType command) throws IOException, ClassNotFoundException {
        switch (command) {

            case IS_FULL -> {
                boolean is_full = managed_shops.size() >= MasterServer.getConfig_info().getWorker_chunk();
                sendBack(request_id, is_full);
            }

            case ADD_SHOP -> {
                Shop new_shop = (Shop) inputStream.readObject();

                ArrayList<Shop> shop_list = getShopListFromId(worker_id);
                shop_list.add(new_shop);
            }

            case ADD_PRODUCT_TO_SHOP -> {
                int shop_id = inputStream.readInt();
                Product new_product = (Product) inputStream.readObject();

                Shop shop = getShopFromId(worker_id, shop_id);
                shop.addProduct(new_product);
            }

            case REMOVE_PRODUCT_FROM_SHOP -> {
                int shop_id = inputStream.readInt();
                int product_id = inputStream.readInt();

                Shop shop = getShopFromId(worker_id, shop_id);
                Product product = shop.getProductById(product_id);

                product.set_removed_status(true);
            }

            case FILTER -> {
                ArrayList<Shop> shops = applyFilters(worker_id);

                sendBack(request_id, shops);
            }
            case CHOSE_SHOP -> {
                int chosen_shop_id = inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                sendBack(request_id, corresponding_shop);
            }
            case ADD_TO_CART -> {
                int chosen_shop_id = inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                Integer added_to_cart = addToCart(corresponding_shop);
                sendBack(request_id, added_to_cart);
            }
            case GET_CART -> {
                int chosen_shop_id = inputStream.readInt();
                ServerCart cart = (ServerCart) inputStream.readObject();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ReadableCart result_cart = getActualCart(corresponding_shop, cart);

                Float total_cost = getCartCost(result_cart);
                result_cart.setTotal_cost(total_cost);

                sendBack(request_id, result_cart);
            }
            case CLEAR_CART -> {
                int chosen_shop_id = inputStream.readInt();
                ServerCart cart = (ServerCart) inputStream.readObject();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                clearCart(corresponding_shop, cart);
            }
            case CHECKOUT_CART -> {
                int chosen_shop_id = inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ServerCart serverCart = (ServerCart) inputStream.readObject();
                CheckoutResultWrapper checked_out = checkout(corresponding_shop, serverCart);

                sendBack(request_id, checked_out);
            }
            case SYNC_CHECKOUT_CART -> {
                int chosen_shop_id = inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ServerCart serverCart = (ServerCart) inputStream.readObject();

                checkout_cart(corresponding_shop, serverCart);
            }
            default -> {
                System.out.println("Command not implemented yet: " + command);
            }
        }
    }

    void connectServer() {
        try {
            synchronized (MasterServer.CONNECTION_ACCEPT_LOCK) {
                socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);

                MasterServer.CONNECTION_ACCEPT_LOCK.notify();
            }
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());

            System.out.println("Worker connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);

        } catch (IOException socket_exception) {
            socket_exception.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            connectServer();

            managed_shops = (ArrayList<Shop>) inputStream.readObject();

            int worker_command_ord = inputStream.readInt();
            WorkerCommandType worker_command = WorkerCommandType.values()[worker_command_ord];

            while (worker_command != WorkerCommandType.END_BACKUP_LIST) {

                int worker_backup_id = inputStream.readInt();
                ArrayList<Shop> worker_backup_shops = (ArrayList<Shop>) inputStream.readObject();
                System.out.println("Worker id: " + id + " Received " + worker_backup_id + " and list with " + worker_backup_shops.size() + " shops");

                backup_shops.put(worker_backup_id, new ArrayList<>(worker_backup_shops));
                System.out.println("Worker " + id + " in map: " + backup_shops.get(worker_backup_id).size());

                worker_command_ord = inputStream.readInt();
                worker_command = WorkerCommandType.values()[worker_command_ord];
            }

            worker_command = WorkerCommandType.values()[inputStream.readInt()];
            while (worker_command != WorkerCommandType.QUIT) {

                long request_id = inputStream.readLong();
                int worker_id = inputStream.readInt();

                handleCommand(request_id, worker_id, worker_command);
                worker_command = WorkerCommandType.values()[inputStream.readInt()];
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("Backups for workers with ID: [\n");
        for (Integer key : backup_shops.keySet()) {
            str.append("\t").append(key).append("\n");
        }
        str.append("]");

        return str.toString();
    }
}
