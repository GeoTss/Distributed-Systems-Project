package com.example.client_efood.ClientSide;

import com.example.client_efood.Domain.Client;
import com.example.client_efood.Domain.Location;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        Client client = new Client("BigTso", new Location(45, 45));
        client.updateBalance(5000.f);
        new ClientHandler(client).startingPoint();
    }
}
