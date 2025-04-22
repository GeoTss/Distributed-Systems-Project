package org.Workers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.Domain.Cart;
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

    public synchronized void checkout_cart(Shop shop, Cart cart){

        for(Map.Entry<Integer, Integer> entry: cart.getProducts().entrySet()){
            Product cart_product = shop.getProductById(entry.getKey());
            cart_product.sellProduct(entry.getValue());
        }
    }

    public synchronized boolean checkout(Shop shop, Cart cart) throws IOException, ClassNotFoundException {

        float balance = inputStream.readFloat();
        float total_cost = getCartCost(shop, cart);
        System.out.println("total_cost = " + total_cost);
        if (total_cost <= balance) {
            System.out.println("Transaction was successful.");
            checkout_cart(shop, cart);
            return true;
        } else {
            System.out.println("Transaction was not successful. Insufficient funds.");
            return false;
        }
    }

    private float getCartCost(Shop shop, Cart cart) {
        float cost = 0;
        for(Map.Entry<Integer, Integer> entry: cart.getProducts().entrySet()){
            Product cart_product = shop.getProductById(entry.getKey());
            cost += cart_product.getPrice() * entry.getValue();
        }
        return cost;
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

                long requestId = inputStream.readLong();
                int worker_id = inputStream.readInt();

                switch (command) {
                    case FILTER -> {
                        ArrayList<Shop> shops = applyFilters(worker_id);
                        // send to worker handler the results in order to update the request monitor
                        outputStream.writeLong(requestId);
                        outputStream.writeObject(shops);
                        outputStream.flush();
                    }
                    case CHOSE_SHOP -> {
                        int chosen_shop_id =  inputStream.readInt();
                        Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                        outputStream.writeLong(requestId);
                        outputStream.writeObject(corresponding_shop);
                        outputStream.flush();
                    }
                    case ADD_TO_CART -> {
                        int chosen_shop_id =  inputStream.readInt();
                        Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                        Integer added_to_cart = addToCart(corresponding_shop);
                        outputStream.writeLong(requestId);
                        outputStream.writeObject(added_to_cart);
                        outputStream.flush();
                    }
                    case REMOVE_FROM_CART -> {
                        int chosen_shop_id =  inputStream.readInt();
                        Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                        boolean removed = removeFromCart(corresponding_shop);
                        outputStream.writeLong(requestId);
                        outputStream.writeObject(removed);
                        outputStream.flush();
                    }
                    case CHECKOUT -> {
                        int chosen_shop_id =  inputStream.readInt();
                        Shop corresponding_shop = getShopFromId(worker_id, chosen_shop_id);

                        Cart cart = (Cart) inputStream.readObject();
                        boolean checked_out = checkout(corresponding_shop, cart);
                        outputStream.writeLong(requestId);
                        outputStream.writeObject(checked_out);
                        outputStream.flush();
                    }
                    default -> {
                        System.out.println("Command not implemented yet: " + command);
                    }
                }
                command = Command.CommandTypeClient.values()[inputStream.readInt()];
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public HashMap<Integer, List<Shop>> getBackup_shops() {
        return backup_shops;
    }

    public Shop getShopFromId(int worker_id, int shop_id){

        if(worker_id == -1)
            return managed_shops.get(shop_id - managed_shops.size()*id);

        return backup_shops.get(worker_id).get(shop_id - MasterServer.getConfig_info().getWorker_chunk()*worker_id);
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
