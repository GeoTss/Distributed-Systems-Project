package com.example.client_efood.ServerSide;

import com.example.client_efood.ClientSide.ClientRequestHandler;
import com.example.client_efood.Domain.Utils.Pair;
import com.example.client_efood.MessagePKG.Message;
import com.example.client_efood.MessagePKG.MessageArgCast;
import com.example.client_efood.ReducerSide.Reducer;
import com.example.client_efood.Workers.Listeners.ReplicationListener;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientBatch {
    public static int batchSize = 0;
    private static int nextBatchId = 0;
    private int batchId;
    private List<ClientRequestHandler> activeClients = new ArrayList<>();

    private Map<Integer, Pair<ObjectOutputStream, ObjectInputStream>> batchWorkerStreams = new HashMap<>();
    private Map<Integer, ReplicationListener> batchWorkerListeners = new HashMap<>();

    private ObjectOutputStream batchReducerOutputStream;
    private ObjectInputStream batchReducerInputStream;
    private ReplicationListener batchReducerListener;

    public ClientBatch() {
        batchId = nextBatchId++;
        System.out.println("Creating Batch ID: " + batchId);
    }

    public boolean canAcceptClient() {
        return activeClients.size() < batchSize;
    }

    public void addClient(ClientRequestHandler clientHandler) {
        activeClients.add(clientHandler);
        clientHandler.setClientBatch(this);
    }

    public void setNewWorkerStreams(int worker_id, String worker_host, int worker_port) throws IOException {

        Pair<ObjectOutputStream, ObjectInputStream> existingStreams = batchWorkerStreams.remove(worker_id);
        if (existingStreams != null) {
            try {
                if (existingStreams.first != null)
                    existingStreams.first.close();
            } catch (IOException e) {
                System.err.println("Error closing old OOS for worker " + worker_id + " in batch " + batchId + ": " + e.getMessage());
            }
            try {
                if (existingStreams.second != null)
                    existingStreams.second.close();
            } catch (IOException e) {
                System.err.println("Error closing old OIS for worker " + worker_id + " in batch " + batchId + ": " + e.getMessage());
            }
        }
        ReplicationListener existingListener = batchWorkerListeners.remove(worker_id);
        if (existingListener != null) {
            existingListener.shutdown();
        }

        synchronized (batchWorkerStreams) {
            try {
                System.out.println("Trying to connect to worker " + worker_id + " with port: " + worker_port + "...");
                Socket new_worker_con = new Socket(worker_host, worker_port);
                ObjectOutputStream out = new ObjectOutputStream(new_worker_con.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(new_worker_con.getInputStream());

                out.writeInt(ConnectionType.CLIENT.ordinal());
                out.flush();

                batchWorkerStreams.put(worker_id, new Pair<>(out, in));

                batchWorkerListeners.put(worker_id, new ReplicationListener(in));
                batchWorkerListeners.get(worker_id).start();
            } catch (IOException e){
                e.printStackTrace();
                throw new IOException();
            }
        }
    }

    public int initializeBatch() {
        for (Integer worker_id : MasterServer.worker_streams.keySet()){

//            Integer worker_port = MasterServer.worker_id_port.get(worker_id);
            try {
                setNewWorkerStreams(worker_id, MasterServer.config_info.getWorkerHost(worker_id), MasterServer.config_info.getWorkerPort(worker_id));
            }catch(IOException e){
                System.out.println("Error occurred while trying to connect with worker.");
                e.printStackTrace();
            }

            try {
                Socket new_reducer_con = new Socket(Reducer.REDUCER_HOST, Reducer.REDUCER_WORKER_PORT);
                batchReducerOutputStream = new ObjectOutputStream(new_reducer_con.getOutputStream());
                batchReducerInputStream = new ObjectInputStream(new_reducer_con.getInputStream());

                Message message = new Message();
                message.addArgument("connection_type", new Pair<>(MessageArgCast.INT_ARG, ConnectionType.CLIENT_BATCH.ordinal()));
                message.addArgument("id", new Pair<>(MessageArgCast.INT_ARG, batchId));

                sendToReducer(message);

                batchReducerListener = new ReplicationListener(batchReducerInputStream);
                batchReducerListener.start();
            }catch(IOException e){
                System.out.println("Error occurred while trying to connect with reducer.");
                e.printStackTrace();
            }
        }
        return 0;
    }

    public void sendToReducer(Message message) throws IOException {
        if (batchReducerOutputStream != null) {
            synchronized (batchReducerOutputStream) {
                batchReducerOutputStream.reset();
                batchReducerOutputStream.writeObject(message);
                batchReducerOutputStream.flush();
            }
        } else {
            throw new IOException("Batch " + batchId + ": No output stream for Reducer.");
        }
    }

    public ObjectOutputStream workerOutput(int worker_id){
        Pair<ObjectOutputStream, ObjectInputStream> streams =  batchWorkerStreams.get(worker_id);
        if(streams == null)
            return null;
        return streams.first;
    }

    public RequestMonitor registerMonitorForWorkerListener(long request_id, int worker_id, RequestMonitor monitor){
        ReplicationListener listener = batchWorkerListeners.get(worker_id);
        if(listener == null)
            return null;
        return listener.registerMonitor(request_id, worker_id, monitor);
    }

    public void unregisterMonitorForWorkerListener(int worker_id, long request_id){
        batchWorkerListeners.get(worker_id).unregisterMonitor(request_id, worker_id);
    }

    public RequestMonitor setMonitorForReducer(long request_id, RequestMonitor monitor){
        return batchReducerListener.registerMonitor(request_id, MasterServer.REDUCER_ID, monitor);
    }
}
