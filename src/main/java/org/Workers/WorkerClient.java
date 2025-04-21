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
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class WorkerClient extends Thread {

    private static int gl_id = 0;

    private int id;
    private Socket socket;
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;
    private List<Shop> managed_shops;
    private HashMap<Integer, List<Shop>> backup_shops;

    public WorkerClient(List<Shop> shops) throws IOException, ClassNotFoundException {
        id = gl_id++;
        managed_shops = shops;
        backup_shops = new HashMap<>();
    }

    public void add_backup(Integer id, List<Shop> shops){
        backup_shops.put(id, shops);
    }

    public ArrayList<Shop> applyFilters(int worker_id) throws IOException, ClassNotFoundException {

        List<Shop> shops_to_work_on;

        if(worker_id == -1){
            shops_to_work_on = managed_shops;
        }
        else {
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
                int worker_id = inputStream.readInt();

                if(command == Command.CommandTypeClient.FILTER){
                    ArrayList<Shop> shops = applyFilters(worker_id);
                    outputStream.writeLong(requestId);
                    outputStream.writeObject(shops);
                    outputStream.flush();
                }
                command = Command.CommandTypeClient.values()[inputStream.readInt()];
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public HashMap<Integer, List<Shop>> getBackup_shops() { return backup_shops; }

    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();

        str.append("Backups for workers with ID: [\n");
        for(Integer key: backup_shops.keySet()){
            str.append("\t").append(key).append("\n");
        }
        str.append("]");

        return str.toString();
    }
}
