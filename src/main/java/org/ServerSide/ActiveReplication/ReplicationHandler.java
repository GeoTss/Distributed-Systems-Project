package org.ServerSide.ActiveReplication;

import org.Workers.WorkerHandler;

import java.util.ArrayList;
import java.util.List;

public class ReplicationHandler {
    private int id;
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

    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();

        str.append("Main worker ID: ").append(main.getHandlerId()).append("\n");
        str.append("Fallback workers IDs: [\n");
        for(WorkerHandler f_work: replicas){
            str.append("\t").append(f_work.getHandlerId()).append("\n");
        }
        str.append("]");
        return str.toString();
    }

    public void promoteToMain(WorkerHandler replica) {
        replicas.remove(replica);

        if (main != null) {
            replicas.add(main);
        }
        main = replica;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
