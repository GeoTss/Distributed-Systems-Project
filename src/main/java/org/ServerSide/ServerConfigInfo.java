package org.ServerSide;

public class ServerConfigInfo {
    private int worker_chunk;
    private int number_of_replicas;

    public ServerConfigInfo(int worker_chunk, int number_of_replicas){
        this.worker_chunk = worker_chunk;
        this.number_of_replicas = number_of_replicas;
    }

    public int getWorker_chunk() {
        return worker_chunk;
    }

    public int getNumber_of_replicas() {
        return number_of_replicas;
    }

    @Override
    public String toString(){
        return "Worker Chunks: " + worker_chunk + "\nNumber of worker replicas: " + number_of_replicas;
    }
}
