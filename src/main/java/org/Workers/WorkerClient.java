package org.Workers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

import org.Domain.ReadableCart;
import org.Domain.ServerCart;
import org.Domain.Product;
import org.Domain.Shop;
import org.Filters.Filter;
import org.ServerSide.Command;
import org.ServerSide.MasterServer;

public class WorkerClient extends Thread {

    private static int gl_id = 0;

    private int id;
    private Socket socket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private List<Shop> managed_shops;
    private HashMap<Integer, List<Shop>> backup_shops;
    // private List<List<Shop>> backup_shops;

    public WorkerClient(List<Shop> shops) throws IOException, ClassNotFoundException {
        id = gl_id++;
        managed_shops = shops;
        backup_shops = new HashMap<>();
    }

    public void add_backup(Integer id, List<Shop> shops) {
        backup_shops.put(id, shops);
    }

    public ArrayList<Shop> applyFilters(int worker_id) throws IOException, ClassNotFoundException {

        List<Shop> shops_to_work_on;

        if (worker_id == -1) {
            shops_to_work_on = managed_shops;
        } else {
            System.out.println("Getting backup shop for " + worker_id + " at client " + id + "...");
            shops_to_work_on = backup_shops.get(worker_id);
            System.out.println(shops_to_work_on);
        }
        @SuppressWarnings("unchecked")
        ArrayList<Filter> received_filters = (ArrayList<Filter>) inputStream.readObject();

        return shops_to_work_on.stream()
                .filter(shop -> received_filters.stream().allMatch(filter -> filter.satisfy(shop)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public synchronized Integer addToCart(Shop shop) throws IOException, ClassNotFoundException {

        Integer product_id = inputStream.readInt();
        Product product = shop.getProductById(product_id);
        int quantity = inputStream.readInt();

        if(product.getAvailableAmount() >= quantity){
            product.removeAvailableAmount(quantity);
            return product.getId();
        }

        return null;
    }

    public synchronized boolean removeFromCart(Shop shop) throws IOException, ClassNotFoundException {
        Integer product_id = inputStream.readInt();
        int quantity = inputStream.readInt();

        System.out.println("Worker " + id + " got values for removal");

        Product product = shop.getProductById(product_id);

        product.addAvailableAmount(quantity);
        return true;
    }

    public synchronized void checkout_cart(Shop shop, ServerCart serverCart){

        for(Map.Entry<Integer, Integer> entry: serverCart.getProducts().entrySet()){
            Product cart_product = shop.getProductById(entry.getKey());
            cart_product.sellProduct(entry.getValue());
        }
    }

    public synchronized boolean checkout(Shop shop, ServerCart serverCart) throws IOException, ClassNotFoundException {

        float balance = inputStream.readFloat();
        float total_cost = getCartCost(shop, serverCart);
        System.out.println("total_cost = " + total_cost);
        if (total_cost <= balance) {
            System.out.println("Transaction was successful.");
            checkout_cart(shop, serverCart);
            return true;
        } else {
            System.out.println("Transaction was not successful. Insufficient funds.");
            return false;
        }
    }

    private float getCartCost(Shop shop, ServerCart serverCart) {
        float cost = 0;
        for(Map.Entry<Integer, Integer> entry: serverCart.getProducts().entrySet()){
            Product cart_product = shop.getProductById(entry.getKey());
            cost += cart_product.getPrice() * entry.getValue();
        }
        return cost;
    }

    private float getCartCost(ReadableCart cart) {
        float cost = 0;
        for(Map.Entry<Product, Integer> entry: cart.getProduct_quantity_map().entrySet()){
            cost += entry.getKey().getPrice() * entry.getValue();
        }
        return cost;
    }

    private ReadableCart getActualCart(Shop correspondingShop, ServerCart cart) {
        ReadableCart resulting_cart = new ReadableCart();

        cart.getProducts().forEach((product_id, quantity) -> resulting_cart.getProduct_quantity_map()
                .put(correspondingShop.getProductById(product_id),
                        quantity
                )
        );
        return resulting_cart;
    }

    public Shop getShopFromId(int worker_id, int shop_id){

        if(worker_id == -1)
            return managed_shops.get(shop_id - managed_shops.size()*id);

        return backup_shops.get(worker_id).get(shop_id - MasterServer.getConfig_info().getWorker_chunk()*worker_id);
    }

    private void sendBack(long request_id, Object result) throws IOException {
        outputStream.writeLong(request_id);
        outputStream.writeObject(result);
        outputStream.flush();
    }

    private void handleCommand(long request_id, int worker_id, Command.CommandTypeClient command) throws IOException, ClassNotFoundException {
        switch (command) {
            case FILTER -> {
                ArrayList<Shop> shops = applyFilters(worker_id);

                sendBack(request_id, shops);
            }
            case CHOSE_SHOP -> {
                int chosen_shop_id =  inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                sendBack(request_id, corresponding_shop);
            }
            case ADD_TO_CART -> {
                int chosen_shop_id =  inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                Integer added_to_cart = addToCart(corresponding_shop);
                sendBack(request_id, added_to_cart);
            }
            case REMOVE_FROM_CART -> {
                int chosen_shop_id =  inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                Boolean removed = removeFromCart(corresponding_shop);
                sendBack(request_id, removed);
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
            case CHECKOUT -> {
                int chosen_shop_id =  inputStream.readInt();
                Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                ServerCart serverCart = (ServerCart) inputStream.readObject();
                Boolean checked_out = checkout(corresponding_shop, serverCart);
                sendBack(request_id, checked_out);
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

            Command.CommandTypeClient command = Command.CommandTypeClient.values()[inputStream.readInt()];
            while (command != Command.CommandTypeClient.QUIT) {

                long request_id = inputStream.readLong();
                int worker_id = inputStream.readInt();

                handleCommand(request_id, worker_id, command);
                command = Command.CommandTypeClient.values()[inputStream.readInt()];
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
