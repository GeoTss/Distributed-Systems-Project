package org.Workers;

import org.ServerSide.RequestMonitor;

import javax.management.monitor.Monitor;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class WorkerHandler extends Thread{

    public static Integer gl_id = 0;

    private Integer id;
    private final ObjectOutputStream worker_out;
    private final ObjectInputStream worker_in;

    private final HashMap<Long, RequestMonitor> monitors_responses = new HashMap<>();
    private final HashMap<Long, ArrayList<RequestMonitor>> monitor_responses_rep = new HashMap<>();

    public WorkerHandler(ObjectOutputStream out, ObjectInputStream in) {
        id = gl_id++;
        worker_out = out;
        worker_in = in;
    }

    public RequestMonitor registerRequest(long requestId){
        RequestMonitor monitor = new RequestMonitor();
//        synchronized (monitors_responses){
//            monitors_responses.put(requestId, monitor);
//        }
        synchronized (monitor_responses_rep){
            monitor_responses_rep
                    .computeIfAbsent(requestId, (_) -> new ArrayList<>())
                    .add(monitor);
        }
        return monitor;
    }

    public void registerMonitor(long requestId, RequestMonitor monitor) {
//        synchronized (monitors_responses){
//            monitors_responses.put(requestId, monitor);
//        }

        synchronized (monitor_responses_rep){
            monitor_responses_rep
                    .computeIfAbsent(requestId, (_) -> new ArrayList<>())
                    .add(monitor);
        }
    }

    @Override
    public void run(){
        try {
            while(true){
                long requestId = worker_in.readLong();
                Object result = worker_in.readObject();

                RequestMonitor monitor;
//                synchronized (monitors_responses) {
//                    monitor = monitors_responses.remove(requestId);
//                }
                ArrayList<RequestMonitor> monitors_for_id;
                synchronized (monitor_responses_rep){
                    monitors_for_id = monitor_responses_rep.get(requestId);
                }

                if(monitors_for_id == null){
                    System.err.println("No monitors found for requestId: " + requestId);
                }
                else{
                    RequestMonitor last_monitor = monitors_for_id.getLast();
                    last_monitor.setResult(result);
                    monitors_for_id.removeLast();
                }

//                if (monitor != null) {
//                    monitor.setResult(result);
//                } else {
//                    System.err.println("No monitor found for requestId: " + requestId);
//                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("WorkerHandler crashed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public ObjectOutputStream getWorker_out() {
        return worker_out;
    }

    public Integer getHandlerId() {
        return id;
    }
}
