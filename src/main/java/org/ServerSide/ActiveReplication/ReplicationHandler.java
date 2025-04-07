package org.ServerSide.ActiveReplication;

import org.Workers.WorkerHandler;

import java.util.ArrayList;
import java.util.List;

public class ReplicationHandler {
    private WorkerHandler main;
    private List<WorkerHandler> replicas = new ArrayList<>();

    public void add_replica(WorkerHandler rep){
        replicas.add(rep);
    }

    public WorkerHandler getMain(){
        return main;
    }

    public List<WorkerHandler> getReplicas(){
        return replicas;
    }

    public void setMain(WorkerHandler main) {
        this.main = main;
    }
}
