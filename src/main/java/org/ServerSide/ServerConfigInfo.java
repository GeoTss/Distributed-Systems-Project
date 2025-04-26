package org.ServerSide;

public class ServerConfigInfo {
    private int worker_count;
    private int number_of_replicas;

    public ServerConfigInfo(int worker_count, int number_of_replicas){
        this.worker_count = worker_count;
        this.number_of_replicas = number_of_replicas;
    }

    public int get_number_of_replicas() {
        return number_of_replicas;
    }

    public int getWorker_count() {
        return worker_count;
    }

    @Override
    public String toString(){
        return "Worker Count: " + worker_count + "\nNumber of worker replicas: " + number_of_replicas;
    }
}
