package com.example.client_efood.Workers.Listeners;

import com.example.client_efood.ServerSide.RequestMonitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;

public class ReplicationListener extends Thread {

    private Integer id;
    private final ObjectInputStream worker_in;

    private final HashMap<Long, HashMap<Integer, RequestMonitor>> monitor_responses_rep = new HashMap<>();
    private final HashMap<Long, HashMap<Integer, Object>> pendingResults = new HashMap<>();
    private volatile boolean running = true;


    public ReplicationListener(ObjectInputStream in) {
        this.worker_in = in;
    }

    public RequestMonitor registerMonitor(long requestId, int from_id, RequestMonitor monitor) {
        synchronized (monitor_responses_rep) {

            if (monitor == null) {
                System.err.println("Listener " + this.id + ": Attempted to register a null monitor for requestId=" + requestId + ", from_id=" + from_id);
                return null;
            }
            System.out.println("Listener " + this.id + ": Registering monitor for requestId=" + requestId + ", from_id=" + from_id);
            HashMap<Integer, RequestMonitor> maps =
                    monitor_responses_rep.computeIfAbsent(requestId, k -> new HashMap<>());
            maps.put(from_id, monitor);
        }

        Object bufferedResult;
        synchronized (pendingResults) {
            HashMap<Integer, Object> bufMap = pendingResults.get(requestId);
            bufferedResult = (bufMap == null ? null : bufMap.remove(from_id));
            if (bufMap != null && bufMap.isEmpty()) {
                pendingResults.remove(requestId);
            }
        }
        if (bufferedResult != null) {
            System.out.println("Listener " + this.id + ": Found pending result for requestId=" + requestId + ", from_id=" + from_id + ". Notifying monitor.");
            monitor.setResult(bufferedResult);

            synchronized (monitor_responses_rep) {
                HashMap<Integer, RequestMonitor> maps = monitor_responses_rep.get(requestId);
                if (maps != null) {
                    maps.remove(from_id);
                    if (maps.isEmpty()) {
                        monitor_responses_rep.remove(requestId);
                    }
                }
            }
        } else {
            System.out.println("Listener " + this.id + ": No pending result for requestId=" + requestId + ", from_id=" + from_id + ". Monitor is now waiting.");
        }
        return monitor;
    }

    public void unregisterMonitor(long requestId, int from_id) {
        synchronized (monitor_responses_rep) {
            HashMap<Integer, RequestMonitor> monitors = monitor_responses_rep.get(requestId);
            if (monitors != null) {
                monitors.remove(from_id);
                System.out.println("Listener " + this.id + ": Unregistered monitor for requestId=" + requestId + ", from_id=" + from_id);
                if (monitors.isEmpty()) {
                    monitor_responses_rep.remove(requestId);
                    System.out.println("Listener " + this.id + ": Removed empty monitors map for requestId=" + requestId);
                }
            } else {
                System.out.println("Listener " + this.id + ": Attempted to unregister monitor for requestId=" + requestId + ", from_id=" + from_id + " but no map found.");
            }
        }
    }

    @Override
    public void run() {
        System.out.println("Listener for ID: " + this.id + " RUNNING on thread: " + Thread.currentThread().getName());
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                int from_id;
                long requestId;
                Object result;
                 System.out.println("Listener " + this.id + ": Waiting to read from worker_in...");
                synchronized (worker_in) {
                    if (worker_in == null) {
                        System.err.println("Listener " + this.id + ": worker_in is null. Terminating.");
                        break;
                    }
                    from_id = worker_in.readInt();
                    requestId = worker_in.readLong();
                    result = worker_in.readObject();
                }
                System.out.println("Listener " + this.id + ": Received response from_id=" + from_id + ", requestId=" + requestId + ", resultType=" + (result != null ? result.getClass().getSimpleName() : "null"));

                RequestMonitor monToNotify = null;
                synchronized (monitor_responses_rep) {
                    HashMap<Integer, RequestMonitor> monitors = monitor_responses_rep.get(requestId);
                    if (monitors != null) {
                        System.out.println("Listener " + this.id + ": Found monitors map for requestId=" + requestId + ". Contains keys: " + monitors.keySet());

                        if (monitors.containsKey(from_id)) {

                            monToNotify = monitors.remove(from_id);
                            System.out.println("Listener " + this.id + ": Found and removed monitor for from_id=" + from_id + ", requestId=" + requestId);

                            if (monitors.isEmpty()) {
                                monitor_responses_rep.remove(requestId);
                                System.out.println("Listener " + this.id + ": Removed monitors map for requestId=" + requestId);
                            }
                        } else {
                            System.out.println("Listener " + this.id + ": No monitor registered for from_id=" + from_id + " (expected) in requestId=" + requestId + " map. Will add to pending.");
                        }
                    } else {
                        System.out.println("Listener " + this.id + ": No monitors map found for requestId=" + requestId + ". Will add to pending.");
                    }
                }

                if (monToNotify != null) {
                    System.out.println("Listener " + this.id + ": Notifying monitor for from_id=" + from_id + ", requestId=" + requestId);

                    monToNotify.setResult(result);
                } else {
                    System.out.println("Listener " + this.id + ": No active monitor, adding to pendingResults: from_id=" + from_id + ", requestId=" + requestId);

                    synchronized (pendingResults) {
                        pendingResults
                                .computeIfAbsent(requestId, k -> new HashMap<>())
                                .put(from_id, result);
                    }
                }
            } catch (IOException e) {
                if (running && !Thread.currentThread().isInterrupted())
                    System.err.println("Listener ID: " + this.id + " IOException (Worker connection likely closed/reset): " + e.getMessage());

                running = false;

            } catch (ClassNotFoundException e) {
                System.err.println("Listener ID: " + this.id + " ClassNotFoundException: " + e.getMessage());
                e.printStackTrace();
                running = false;

            } catch (Exception e) {
                System.err.println("Listener ID: " + this.id + " Unexpected Exception: " + e.getMessage());
                e.printStackTrace();
                running = false;
            }
        }
        System.out.println("Listener ID: " + this.id + " THREAD TERMINATING.");

        try {
            if (worker_in != null) worker_in.close();
        } catch (IOException e) {
            System.err.println("Listener ID: " + this.id + " Error closing worker_in: " + e.getMessage());
        }
    }

    public Integer getHandlerId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public void shutdown() {
        System.out.println("Listener ID: " + this.id + " shutdown() called.");
        running = false;
        this.interrupt();
        try {
            if (worker_in != null) {
                worker_in.close();
            }
        } catch (IOException e) {
            System.err.println("Listener ID: " + this.id + " Exception during shutdown close: " + e.getMessage());
        }
    }
}