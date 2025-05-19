package org.Workers.Listeners;

import org.ServerSide.RequestMonitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;

public class ReplicationListener extends Thread {

    private Integer id;
    private final ObjectInputStream worker_in;

    private final HashMap<Long, HashMap<Integer, RequestMonitor>> monitor_responses_rep = new HashMap<>();

    private final HashMap<Long, HashMap<Integer, Object>> pendingResults = new HashMap<>();

    public ReplicationListener(ObjectInputStream in) {
        worker_in = in;
    }

    public RequestMonitor registerMonitor(long requestId, int from_id, RequestMonitor monitor) {
        synchronized (monitor_responses_rep) {
            HashMap<Integer, RequestMonitor> maps =
                    monitor_responses_rep.computeIfAbsent(requestId, k -> new HashMap<>());
            maps.put(from_id, monitor);
        }

        Object buffered;
        synchronized (pendingResults) {
            System.out.println("Request: " + requestId + " adding pending result from " + from_id + " worker");
            HashMap<Integer, Object> bufMap = pendingResults.get(requestId);
            buffered = (bufMap == null ? null : bufMap.remove(from_id));
            if (bufMap != null && bufMap.isEmpty()) {
                pendingResults.remove(requestId);
            }
        }
        if (buffered != null) {
            monitor.setResult(buffered);
            synchronized (monitor_responses_rep) {
                HashMap<Integer, RequestMonitor> maps = monitor_responses_rep.get(requestId);
                if (maps != null) {
                    maps.remove(from_id);
                    if (maps.isEmpty()) {
                        monitor_responses_rep.remove(requestId);
                    }
                }
            }
        }

        return monitor;
    }


    public void unregisterMonitor(long requestId, int from_id) {
        synchronized (monitor_responses_rep) {
            monitor_responses_rep.get(requestId).remove(from_id);
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                int from_id;
                long requestId;
                Object result;
                synchronized (worker_in) {
                    from_id = worker_in.readInt();
                    requestId = worker_in.readLong();
                    result = worker_in.readObject();
                }

                synchronized (monitor_responses_rep) {
                    HashMap<Integer, RequestMonitor> monitors = monitor_responses_rep.get(requestId);
                    if (monitors != null && monitors.containsKey(from_id)) {

                        RequestMonitor mon = monitors.remove(from_id);
                        mon.setResult(result);
                        if (monitors.isEmpty()) {
                            monitor_responses_rep.remove(requestId);
                        }
                    } else {
                        pendingResults
                                .computeIfAbsent(requestId, k -> new HashMap<>())
                                .put(from_id, result);
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

    public void shutdown() {
        try {
            worker_in.close();
        } catch (IOException ignored) {}
        this.interrupt();
    }
}
