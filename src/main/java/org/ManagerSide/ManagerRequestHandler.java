package org.ManagerSide;

import org.Domain.*;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ServerSide.ClientRequests.ThrowingConsumer;
import org.ServerSide.Command;
import org.ServerSide.RequestMonitor;
import org.Workers.Listeners.ReplicationListener;
import org.Workers.WorkerCommandType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import static org.ServerSide.MasterServer.getWorkerForShop;
import static org.ServerSide.MasterServer.worker_handlers;

public class ManagerRequestHandler extends Thread {

    private final ObjectInputStream in;

    private final ObjectOutputStream out;
    private final Socket connection;

    private Manager manager;

    public static HashMap<Integer, ReplicationHandler> replicated_worker_handlers;

    public ManagerRequestHandler(Socket connection, ObjectOutputStream out, ObjectInputStream in) throws IOException {
        this.connection = connection;
        this.out = out;
        this.in = in;
    }

    private int sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic, RequestMonitor monitor, long request_id, int worker_id) {
        ReplicationListener handler = null;
        int main_id = replicatedWorker.getMainId();
        try {
            handler = worker_handlers.get(main_id);
            synchronized (handler){
                monitor = handler.registerMonitor(request_id, worker_id, monitor);
            }

            ObjectOutputStream main_worker_writer = replicatedWorker.getMain();
            synchronized (main_worker_writer) {
                write_logic.accept(main_worker_writer);
                main_worker_writer.flush();
            }
            return main_id;
        } catch (IOException e) {
            if(handler != null) {
                synchronized (handler) {
                    handler.unregisterMonitor(request_id, worker_id);
                }
            }
            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                try {
                    handler = worker_handlers.get(replica_id);
                    synchronized (handler){
                        monitor = handler.registerMonitor(request_id, worker_id, monitor);
                    }

                    ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);
                    synchronized (replica_writer) {
                        write_logic.accept(replica_writer);
                        replica_writer.flush();
                    }

                    synchronized (replicatedWorker){
                        replicatedWorker.promoteToMain(replica_id);
                    }

                    return replica_id;
                } catch (IOException ex) {
                    synchronized (handler){
                        handler.unregisterMonitor(request_id, worker_id);
                    }
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }
        return -1;
    }

    private Utils.Pair<Integer, Integer> sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, ThrowingConsumer<ObjectOutputStream> write_logic) {

        try {
            ObjectOutputStream main_worker_writer = replicatedWorker.getMain();
            synchronized (main_worker_writer) {
                write_logic.accept(main_worker_writer);
                main_worker_writer.flush();
            }
            return new Utils.Pair<>(replicatedWorker.getId(), replicatedWorker.getMainId());
        } catch (IOException e) {
            System.err.println("Main worker failed. Going for replicas... " + e.getMessage());

            for (Integer replica_id : replicatedWorker.getReplicaIds()) {
                try {
                    ObjectOutputStream replica_writer = replicatedWorker.getReplicaOutput(replica_id);
                    synchronized (replica_writer) {
                        write_logic.accept(replica_writer);
                        replica_writer.flush();
                    }
                    int prev_main_id;
                    synchronized (replicatedWorker){
                        prev_main_id = replicatedWorker.getId();
                        replicatedWorker.promoteToMain(replica_id);
                    }

                    return new Utils.Pair<>(prev_main_id, replica_id);
                } catch (IOException ex) {
                    System.err.println("Replica failed. Trying another one...");
                }
            }
        }
        return null;
    }

    void syncReplicas(ReplicationHandler replicated_worker, ThrowingConsumer<ObjectOutputStream> write_logic) throws IOException{
        for(ObjectOutputStream replica_writer: replicated_worker.getReplicasOutputs()){
            write_logic.accept(replica_writer);
            replica_writer.flush();
        }
    }

    public void handleCommand(Command.CommandTypeManager cmd)
            throws IOException, ClassNotFoundException, InterruptedException {
        long requestId = threadId();
        switch (cmd) {
            case QUIT, DEFAULT, END -> { /* no‐ops for now */ }
            case ADD_SHOP -> handleAddShop(requestId);
            case ADD_PRODUCT -> handleAddProduct(requestId);
            case ADD_AVAILABLE_PRODUCT -> handleAddAvailableProduct(requestId);
            case REMOVE_AVAILABLE_PRODUCT -> handleRemoveAvailableProduct(requestId);
        }
    }

    private void handleAddShop(long requestId)
            throws IOException, InterruptedException {
        String name = in.readUTF();
        double latitude = in.readDouble();
        double longitude = in.readDouble();
        String foodCategory = in.readUTF();
        float initialStars = in.readFloat();
        int initialVotes = in.readInt();
        String logoPath = in.readUTF();

        Shop new_shop = new Shop(name, latitude, longitude, foodCategory, initialStars, initialVotes, logoPath);
        int shop_id = new_shop.getId();
        ReplicationHandler responsible_worker = getWorkerForShop(shop_id);

        assert responsible_worker != null;

        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.writeInt(Command.CommandTypeManager.ADD_SHOP.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(responsible_worker.getId());
            wout.writeObject(new_shop);
        };

        System.out.println("New shop ID: " + shop_id);
        RequestMonitor add_shop_monitor = new RequestMonitor();
        sendToWorkerWithReplicas(responsible_worker, writer, add_shop_monitor, requestId, responsible_worker.getId());

    }


    private void handleAddProduct(long requestId)
            throws IOException, ClassNotFoundException, InterruptedException {

        int shop_id = in.readInt();

        String name = in.readUTF();
        String type = in.readUTF();
        int availableAmount = in.readInt();
        float price = in.readFloat();

        Product new_product = new Product(name, type, availableAmount, price);

        ReplicationHandler responsible_worker = getWorkerForShop(shop_id);

        ThrowingConsumer<ObjectOutputStream> productWriter = (wout) -> {
            wout.writeInt(Command.CommandTypeManager.ADD_PRODUCT.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(responsible_worker.getId());
            wout.writeInt(shop_id);
            wout.writeObject(new_product);
        };

        RequestMonitor monitor = new RequestMonitor();
        int sent_to = sendToWorkerWithReplicas(responsible_worker, productWriter, monitor, requestId, responsible_worker.getId());

        boolean success = false;
        if(sent_to != -1){
            success = true;
            if(sent_to != responsible_worker.getId())
                System.out.println("Added new product " + new_product + " to backup worker " + sent_to);
            else
                System.out.println("Added new product " + new_product + " to worker " + sent_to);

            ThrowingConsumer<ObjectOutputStream> product_sync_writer = (wout) -> {
                wout.writeInt(WorkerCommandType.SYNC_ADD_PRODUCT_TO_SHOP.ordinal());
                wout.writeLong(requestId);
                wout.writeInt(responsible_worker.getId());
                wout.writeInt(shop_id);
                wout.writeObject(new_product);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(responsible_worker, product_sync_writer);
            System.out.println("Replicas synced");
        }

        out.writeBoolean(success);
        out.flush();
    }

    private void handleRemoveProduct(long requestId) throws IOException, InterruptedException {
        int shopId = in.readInt();
        int productId = in.readInt();

        ReplicationHandler handler = getWorkerForShop(shopId);
        assert handler != null;
        RequestMonitor monitor = new RequestMonitor();
        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.writeInt(WorkerCommandType.REMOVE_PRODUCT_FROM_SHOP.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(handler.getId());
            wout.writeInt(shopId);
            wout.writeInt(productId);
        };

        int sent_to = sendToWorkerWithReplicas(handler, writer, monitor, requestId, handler.getId());
        Boolean result = (Boolean) monitor.getResult();

        if(sent_to != -1 && result){
            System.out.println("Product was added successfully");

            writer = (wout) -> {
                wout.writeInt(WorkerCommandType.SYNC_REMOVE_PRODUCT_FROM_SHOP.ordinal());
                wout.writeLong(requestId);
                wout.writeInt(handler.getId());
                wout.writeInt(shopId);
                wout.writeInt(productId);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(handler, writer);
            System.out.println("Replicas synced.");
        }
        out.writeBoolean(result != null && result);
        out.flush();
    }

    private void handleAddAvailableProduct(long requestId)
            throws IOException, InterruptedException {
        int shopId = in.readInt();
        int productId = in.readInt();
        int quantity = in.readInt();

        ReplicationHandler handler = getWorkerForShop(shopId);
        assert handler != null;
        RequestMonitor monitor = new RequestMonitor();
        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.writeInt(WorkerCommandType.ADD_PRODUCT_STOCK.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(handler.getId());
            wout.writeInt(shopId);
            wout.writeInt(productId);
            wout.writeInt(quantity);
        };

        int sent_to = sendToWorkerWithReplicas(handler, writer, monitor, requestId, handler.getId());
        Boolean result = (Boolean) monitor.getResult();

        if(sent_to != -1 && result){
            System.out.println("Product was added successfully");

            writer = (wout) -> {
                wout.writeInt(WorkerCommandType.SYNC_ADD_PRODUCT_STOCK.ordinal());
                wout.writeLong(requestId);
                wout.writeInt(handler.getId());
                wout.writeInt(shopId);
                wout.writeInt(productId);
                wout.writeInt(quantity);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(handler, writer);
            System.out.println("Replicas synced.");
        }
        out.writeBoolean(result != null && result);
        out.flush();
    }

    private void handleRemoveAvailableProduct(long requestId)
            throws IOException, InterruptedException {
        int shopId = in.readInt();
        int productId = in.readInt();
        int quantity = in.readInt();

        ReplicationHandler handler = getWorkerForShop(shopId);
        assert handler != null;
        RequestMonitor monitor = new RequestMonitor();
        ThrowingConsumer<ObjectOutputStream> writer = (wout) -> {
            wout.writeInt(WorkerCommandType.REMOVE_PRODUCT_STOCK.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(handler.getId());
            wout.writeInt(shopId);
            wout.writeInt(productId);
            wout.writeInt(quantity);
        };

        int sent_to = sendToWorkerWithReplicas(handler, writer, monitor, requestId, handler.getId());
        Boolean result = (Boolean) monitor.getResult();

        if(sent_to != -1 && result){
            System.out.println("Product was added successfully");

            writer = (wout) -> {
                wout.writeInt(WorkerCommandType.SYNC_REMOVE_PRODUCT_STOCK.ordinal());
                wout.writeLong(requestId);
                wout.writeInt(handler.getId());
                wout.writeInt(shopId);
                wout.writeInt(productId);
                wout.writeInt(quantity);
            };

            System.out.println("Syncing replicas...");
            syncReplicas(handler, writer);
            System.out.println("Replicas synced.");
        }
        out.writeBoolean(result != null && result);
        out.flush();
    }

    @Override
    public void run() {

        try {
            System.out.println("Manager connected: " + connection.getInetAddress());

            manager = (Manager) in.readObject();

            Command.CommandTypeManager cmd = Command.CommandTypeManager.DEFAULT;
            while (cmd != Command.CommandTypeManager.QUIT) {
                int ord = in.readInt();
                cmd = Command.CommandTypeManager.values()[ord];
                System.out.println("Manager cmd: " + cmd);
                handleCommand(cmd);
            }
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Manager connection error.");
        } finally {
            try {
                // ensure clean shutdown
                out.close();
                in.close();
                connection.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            System.out.println("Manager session closed.");
        }
    }
}
