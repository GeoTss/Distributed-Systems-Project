package org.Workers;

import org.ServerSide.RequestMonitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

public class WorkerHandler extends Thread{

    private final ObjectOutputStream worker_out;
    private final ObjectInputStream worker_in;

    private final HashMap<Long, RequestMonitor> monitors_responses = new HashMap<>();

    public WorkerHandler(ObjectOutputStream out, ObjectInputStream in) {
        worker_out = out;
        worker_in = in;
    }

    public RequestMonitor registerRequest(long requestId){
        RequestMonitor monitor = new RequestMonitor();
        synchronized (monitors_responses){
            monitors_responses.put(requestId, monitor);
        }
        return monitor;
    }

    public void registerMonitor(long requestId, RequestMonitor monitor) {
        synchronized (monitors_responses){
            monitors_responses.put(requestId, monitor);
        }
    }

    @Override
    public void run(){
        try {
            while(true){
                long requestId = worker_in.readLong();
                Object result = worker_in.readObject();

                RequestMonitor monitor;
                synchronized (monitors_responses) {
                    monitor = monitors_responses.remove(requestId);
                }

                if (monitor != null) {
                    monitor.setResult(result);
                } else {
                    System.err.println("No monitor found for requestId: " + requestId);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("WorkerHandler crashed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public ObjectOutputStream getWorker_out() {
        return worker_out;
    }
}
