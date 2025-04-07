package org.Workers;

import org.Domain.Shop;
import org.Filters.Filter;
import org.ServerSide.Command;
import org.ServerSide.MasterServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WorkerClient extends Thread {

    private Socket socket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private List<Shop> managed_shops;

    public WorkerClient(List<Shop> shops) throws IOException, ClassNotFoundException {
        managed_shops = shops;
    }

    public ArrayList<Shop> applyFilters() throws IOException, ClassNotFoundException {

        @SuppressWarnings("unchecked")
        ArrayList<Filter> received_filters = (ArrayList<Filter>) inputStream.readObject();

        return managed_shops.stream()
                .filter(shop -> received_filters.stream().allMatch(filter -> filter.satisfy(shop)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    void connectServer(){
        try{
            synchronized (MasterServer.CONNECTION_ACCEPT_LOCK) {
                socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);

                MasterServer.CONNECTION_ACCEPT_LOCK.notify();
            }
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            inputStream = new ObjectInputStream(socket.getInputStream());

            System.out.println("Worker connected with server at port: " + MasterServer.SERVER_CLIENT_PORT);

        } catch(IOException socket_exception){
            socket_exception.printStackTrace();
        }
    }

    @Override
    public void run(){
        try {
            connectServer();

            Command.CommandTypeClient command = Command.CommandTypeClient.values()[inputStream.readInt()];
            while(command != Command.CommandTypeClient.QUIT){

                long requestId = inputStream.readLong();

                if(command == Command.CommandTypeClient.FILTER){
                    ArrayList<Shop> shops = applyFilters();
                    outputStream.writeLong(requestId);
                    outputStream.writeObject(shops);
                    outputStream.flush();
                }
                command = Command.CommandTypeClient.values()[inputStream.readInt()];
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }
}
