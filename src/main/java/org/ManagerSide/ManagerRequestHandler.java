package org.ManagerSide;

import org.Domain.*;
import org.ServerSide.ActiveReplication.ReplicationHandler;
import org.ServerSide.ClientRequests.ThrowingBiConsumer;
import org.ServerSide.Command;
import org.ServerSide.RequestMonitor;
import org.Workers.WorkerHandler;
import org.ServerSide.MasterServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class ManagerRequestHandler extends Thread {

    private final ObjectInputStream in;

    private final ObjectOutputStream out;
    private final Socket connection;

    private Manager manager;

    public static ArrayList<ReplicationHandler> replicated_worker_handlers;

    public ManagerRequestHandler(Socket connection, ObjectOutputStream out, ObjectInputStream in) throws IOException {
        this.connection = connection;
        this.out = out;
        this.in = in;
    }

    /**
     * Try sending to main worker, on failure fail over to replicas.
     */
    private boolean sendToWorkerWithReplicas(ReplicationHandler replicatedWorker, long requestId, ThrowingBiConsumer<ObjectOutputStream, Integer> writeLogic, RequestMonitor monitor) {
        WorkerHandler main = replicatedWorker.getMain();
        try {
            ObjectOutputStream wout = main.getWorker_out();
            synchronized (main) {
                main.registerMonitor(requestId, monitor);
                writeLogic.accept(wout, -1);
                wout.flush();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Main worker failed, trying replicas: " + e.getMessage());
            for (WorkerHandler replica : replicatedWorker.getReplicas()) {
                try {
                    ObjectOutputStream rout = replica.getWorker_out();
                    synchronized (replica) {
                        replica.registerMonitor(requestId, monitor);
                        writeLogic.accept(rout, main.getHandlerId());
                        rout.flush();
                    }
                    return true;
                } catch (IOException ex) {
                    System.err.println("Replica failed, trying next...");
                }
            }
        }
        return false;
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

        ThrowingBiConsumer<ObjectOutputStream, Integer> writer = (wout, workerId) -> {
            wout.writeInt(Command.CommandTypeManager.ADD_SHOP.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(workerId);
            wout.writeUTF(name);
            wout.writeDouble(latitude);
            wout.writeDouble(longitude);
            wout.writeUTF(foodCategory);
            wout.writeFloat(initialStars);
            wout.writeInt(initialVotes);
            wout.writeUTF(logoPath);
        };

        Shop new_shop = new Shop(name, latitude, longitude, foodCategory, initialStars, initialVotes, logoPath);
        int shop_id = new_shop.getId();

        System.out.println("New shop ID: " + shop_id);

        ReplicationHandler responsible_worker = getWorkerForShop(shop_id);
    }


    private void handleAddProduct(long requestId)
            throws IOException, ClassNotFoundException, InterruptedException {
        // read a Product object from client
        Product prod = (Product) in.readObject();

        // broadcast to all replication groups
        ArrayList<RequestMonitor> monitors = new ArrayList<>();
        ThrowingBiConsumer<ObjectOutputStream, Integer> writer = (wout, workerId) -> {
            wout.writeInt(Command.CommandTypeManager.ADD_PRODUCT.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(workerId);
            wout.writeObject(prod);
        };
        for (ReplicationHandler r : replicated_worker_handlers) {
            RequestMonitor m = new RequestMonitor();
            sendToWorkerWithReplicas(r, requestId, writer, m);
            monitors.add(m);
        }

        // collect confirmations
        boolean success = true;
        for (RequestMonitor m : monitors) {
            Boolean ok = (Boolean) m.getResult();
            if (ok == null || !ok) success = false;
        }

        out.writeBoolean(success);
        out.flush();
    }

    private void handleAddAvailableProduct(long requestId)
            throws IOException, InterruptedException {
        int shopId = in.readInt();
        int productId = in.readInt();
        int quantity = in.readInt();

        ReplicationHandler handler = getWorkerForShop(shopId);
        RequestMonitor monitor = new RequestMonitor();

        ThrowingBiConsumer<ObjectOutputStream, Integer> writer = (wout, workerId) -> {
            wout.writeInt(Command.CommandTypeManager.ADD_AVAILABLE_PRODUCT.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(workerId);
            wout.writeInt(shopId);
            wout.writeInt(productId);
            wout.writeInt(quantity);
        };

        sendToWorkerWithReplicas(handler, requestId, writer, monitor);
        Boolean result = (Boolean) monitor.getResult();
        out.writeBoolean(result != null && result);
        out.flush();
    }

    private void handleRemoveAvailableProduct(long requestId)
            throws IOException, InterruptedException {
        int shopId = in.readInt();
        int productId = in.readInt();
        int quantity = in.readInt();

        ReplicationHandler handler = getWorkerForShop(shopId);
        RequestMonitor monitor = new RequestMonitor();

        ThrowingBiConsumer<ObjectOutputStream, Integer> writer = (wout, workerId) -> {
            wout.writeInt(Command.CommandTypeManager.REMOVE_AVAILABLE_PRODUCT.ordinal());
            wout.writeLong(requestId);
            wout.writeInt(workerId);
            wout.writeInt(shopId);
            wout.writeInt(productId);
            wout.writeInt(quantity);
        };

        sendToWorkerWithReplicas(handler, requestId, writer, monitor);
        Boolean result = (Boolean) monitor.getResult();
        out.writeBoolean(result != null && result);
        out.flush();
    }

    private static ReplicationHandler getWorkerForShop(int shopId) {
        int chunk = MasterServer.getConfig_info().getWorker_chunk();
        return replicated_worker_handlers.get(shopId / chunk);
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
