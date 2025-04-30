package org.ServerSide.ActiveReplication;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class ReplicationHandler {
    private int id;

    private int main_id;
    private ObjectOutputStream main;

    private HashMap<Integer, ObjectOutputStream> replicas = new HashMap<>();

    public void add_replica(int rep_id, ObjectOutputStream rep){
        replicas.put(rep_id, rep);
    }

    public ObjectOutputStream getMain(){
        return main;
    }

    public Set<Integer> getReplicaIds(){
        return replicas.keySet();
    }

    public List<ObjectOutputStream> getReplicasOutputs(){
        return replicas.values().stream().toList();
    }

    public ObjectOutputStream getReplicaOutput(int id){
        return replicas.get(id);
    }

    public void setMain(ObjectOutputStream main) {
        this.main = main;
    }

    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();

        str.append("Main worker ID: ").append(main_id).append("\n");
        str.append("Fallback workers IDs: [\n");
        for(int f_work_id: getReplicaIds()){
            str.append("\t").append(f_work_id).append("\n");
        }
        str.append("]");
        return str.toString();
    }

    public void promoteToMain(int replica_id) {

        if (main != null) {
            replicas.put(main_id, main);
        }

        main_id = replica_id;
        main = replicas.get(replica_id);

        replicas.remove(replica_id);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMainId(){
        return main_id;
    }

    public void setMainId(int _main_id){
        main_id = _main_id;
    }

    public void clearData(){
        replicas.clear();
    }
}
