package org.ClientSide;

import org.Domain.Client;
import org.Domain.Location;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        Client client = new Client("BigTso", new Location(45, 45));
        client.updateBalance(5000.f);
        Thread cl = new ClientHandler(client);
        cl.start();
    }
}
