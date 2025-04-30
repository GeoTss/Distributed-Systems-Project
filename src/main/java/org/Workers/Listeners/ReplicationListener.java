package org.Workers.Listeners;

import org.ServerSide.RequestMonitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;

public class ReplicationListener extends Thread {

    private Integer id;
    private final ObjectInputStream worker_in;

    private final HashMap<Long, HashMap<Integer, RequestMonitor>> monitor_responses_rep = new HashMap<>();

    public ReplicationListener(ObjectInputStream in) {
        worker_in = in;
    }

    public RequestMonitor registerMonitor(long requestId, int from_id, RequestMonitor monitor) {
        HashMap<Integer, RequestMonitor> worker_monitors = monitor_responses_rep.computeIfAbsent(requestId, (_) -> new HashMap<>());
        worker_monitors.putIfAbsent(from_id, monitor);
        return worker_monitors.get(from_id);
    }

    public void unregisterMonitor(long requestId, int from_id){
        monitor_responses_rep.get(requestId).remove(from_id);
    }

    @Override
    public void run() {
        try {
            while (true) {
                int from_id = worker_in.readInt();
                long requestId = worker_in.readLong();
                Object result = worker_in.readObject();

                synchronized (this) {
                    HashMap<Integer, RequestMonitor> worker_results;

                    synchronized (monitor_responses_rep) {
                        worker_results = monitor_responses_rep.get(requestId);
                    }

                    if (worker_results == null) {
                        System.err.println("From listener with " + id + ": no monitors found for requestId: " + requestId);
                    } else {
                        RequestMonitor corresponding_monitor = worker_results.get(from_id);
                        if(corresponding_monitor == null) {
                            System.out.println("From listener with " + id + ": Registering new monitor for request " + requestId + " in listener " + id + " for worker " + from_id + " which wasn't registered before.");
                            corresponding_monitor = new RequestMonitor();
                            corresponding_monitor.setResult(result);
                            registerMonitor(requestId, from_id, corresponding_monitor);
                        }
                        else{
                            corresponding_monitor.setResult(result);
                            worker_results.remove(from_id);
                        }
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            try {
                worker_in.close();
            } catch (IOException ex) {
                System.err.println("WorkerHandler crashed: " + e.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public Integer getHandlerId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
