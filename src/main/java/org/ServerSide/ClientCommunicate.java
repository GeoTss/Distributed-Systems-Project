package org.ServerSide;

import org.Domain.ServerCart;
import org.Domain.Client;
import org.Domain.Location;
import org.Domain.Shop;
import org.Filters.Filter;
import org.Filters.PriceCategoryEnum;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public class ClientCommunicate extends Thread {

    Socket request_socket = null;
    ObjectOutputStream out;
    ObjectInputStream in;

    public ClientCommunicate() throws IOException {
        request_socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);
        out = new ObjectOutputStream(request_socket.getOutputStream());
        in = new ObjectInputStream(request_socket.getInputStream());
    }

    @Override
    public void run() {
        try {

            Location loc = new Location(45, 45);
//            out.writeObject(loc);
            Client cl = new Client("BigTso", loc);
            cl.updateBalance(500.f);
            out.writeObject(cl);
            out.flush();

            out.writeInt(Command.CommandTypeClient.FILTER.ordinal());

            out.writeInt(Filter.Types.FILTER_CATEGORY.ordinal());
            Set<String> categories = new TreeSet<>();
            categories.add("sushi");
            categories.add("coffee");
            out.writeObject(categories);

            out.writeInt(Filter.Types.FILTER_PRICE.ordinal());
            PriceCategoryEnum pr_cat = PriceCategoryEnum.MEDIUM;
            out.writeInt(pr_cat.ordinal());

            // out.writeInt(Filter.Types.FILTER_RADIUS.ordinal());
            // double max_radius = 500.0;
            // out.writeDouble(max_radius);

            out.writeInt(Filter.Types.END.ordinal());
            out.flush();

            @SuppressWarnings("unchecked")
            ArrayList<Shop> filtered_shops = (ArrayList<Shop>) in.readObject();

            for (Shop shop : filtered_shops) {
                System.out.println(shop + "\n");
            }

            out.writeInt(Command.CommandTypeClient.CHOSE_SHOP.ordinal());
            out.writeInt(filtered_shops.getFirst().getId());
            out.flush();

            Shop resulting_shop = (Shop) in.readObject();
            System.out.println("Got shop: " + resulting_shop);

            out.writeInt(Command.CommandTypeClient.ADD_TO_CART.ordinal());
            Integer product_id = (Integer) filtered_shops.getFirst().getProducts().keySet().toArray()[0];
            System.out.println("Trying to add product with id " + product_id);
            out.writeInt(product_id);
            out.writeInt(400);
            out.flush();

            boolean added_to_cart = in.readBoolean();
            if(added_to_cart)
                System.out.println("Added product to cart successfully");
            else
                System.out.println("Product wasn't added to cart successfully.");

            {
                out.writeInt(Command.CommandTypeClient.GET_CART.ordinal());
                out.flush();
                ServerCart tempServerCart = (ServerCart) in.readObject();

                tempServerCart.getProducts().forEach((key, value) -> System.out.println("{\n" + key.toString() + "\nQuantity: " + value + "}"));
            }
            out.writeInt(Command.CommandTypeClient.REMOVE_FROM_CART.ordinal());
            out.writeInt(product_id);
            out.writeInt(10);
            out.flush();

            Boolean removed = in.readBoolean();
            if(removed){
                System.out.println("Removal was successful.");
            }

            {
                out.writeInt(Command.CommandTypeClient.GET_CART.ordinal());
                out.flush();
                ServerCart serverCart = (ServerCart) in.readObject();

                serverCart.getProducts().forEach((key, value) -> System.out.println("{\n" + key.toString() + "\nQuantity: " + value + "}"));
            }
            System.out.println("Sending checkout command...");
            out.writeInt(Command.CommandTypeClient.CHECKOUT.ordinal());
            out.flush();

            out.writeInt(Command.CommandTypeClient.QUIT.ordinal());
            out.flush();

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                in.close();
                out.close();
                request_socket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }
}
