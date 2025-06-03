package com.example.client_efood.ServerSide;

import java.util.ArrayList;

public class ServerConfigInfo {
    private int worker_count;
    private int number_of_replicas;
    private int client_batch_size;
    private ArrayList<Integer> worker_ports;
    private ArrayList<String> worker_hosts;

    public ServerConfigInfo(int worker_count, int number_of_replicas, int client_batch_size){
        this.worker_count = worker_count;
        this.number_of_replicas = number_of_replicas;
        this.client_batch_size = client_batch_size;
    }

    public int get_number_of_replicas() {
        return number_of_replicas;
    }

    public int getWorker_count() {
        return worker_count;
    }

    public int getClient_batch_size() {
        return client_batch_size;
    }

    public ArrayList<Integer> getWorker_ports() {
        return worker_ports;
    }

    public void setWorker_ports(ArrayList<Integer> worker_ports) {
        this.worker_ports = worker_ports;
    }

    public ArrayList<String> getWorker_hosts() {
        return worker_hosts;
    }

    public void setWorker_hosts(ArrayList<String> worker_hosts) {
        this.worker_hosts = worker_hosts;
    }

    public String getWorkerHost(int worker_id) { return worker_hosts.get(worker_id); }
    public int getWorkerPort(int worker_id){
        return worker_ports.get(worker_id);
    }

    @Override
    public String toString(){
        return "Worker Count: " + worker_count +
                "\nNumber of worker replicas: " + number_of_replicas +
                "\nClient batch size: " + client_batch_size +
                "\nWorker Hosts: " + worker_hosts +
                "\nWorker Ports: " + worker_ports;
    }

}
