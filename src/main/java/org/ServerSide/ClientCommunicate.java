package org.ServerSide;

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

public class ClientCommunicate extends Thread{

    Socket request_socket = null;
    ObjectOutputStream out;
    ObjectInputStream in;

    public ClientCommunicate() throws IOException {
        request_socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);
        out = new ObjectOutputStream(request_socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(request_socket.getInputStream());
    }

    @Override
    public void run(){
        try {

            Location loc = new Location(45, 45);
            out.writeObject(loc);
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

//            out.writeInt(Filter.Types.FILTER_RADIUS.ordinal());
//            double max_radius = 500.0;
//            out.writeDouble(max_radius);

            out.writeInt(Filter.Types.END.ordinal());
            out.flush();

            @SuppressWarnings("unchecked")
            ArrayList<Shop> filtered_shops = (ArrayList<Shop>) in.readObject();

            for(Shop shop: filtered_shops){
                System.out.println(shop + "\n");
            }

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
