package org.ServerSide.ActiveReplication;

import org.Domain.Utils;
import org.ServerSide.MasterServer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ReplicationHandler {
    private int id;

    private int main_id;

    private ArrayList<Integer> replicas = new ArrayList<>();

    public void add_replica(int rep_id){
        replicas.add(rep_id);
    }

    public ObjectOutputStream getMain(){
        Utils.Pair<ObjectOutputStream, ObjectInputStream> streams = MasterServer.worker_streams.get(main_id);
        if(streams == null)
            return null;
        return streams.first;
    }

    public ArrayList<Integer> getReplicaIds(){
        return replicas;
    }

    public ArrayList<ObjectOutputStream> getReplicasOutputs(){
        return replicas.stream()
                .filter(rep_id -> MasterServer.worker_streams.get(rep_id) != null)
                .map(rep_id -> MasterServer.worker_streams.get(rep_id).first)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public ObjectOutputStream getReplicaOutput(int id){
        Utils.Pair<ObjectOutputStream, ObjectInputStream> streams = MasterServer.worker_streams.get(id);
        if(streams == null)
            return null;
        return streams.first;
    }

//    public void setMain(ObjectOutputStream main) {
//        this.main = main;
//    }

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
        replicas.add(main_id);
        main_id = replica_id;
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
