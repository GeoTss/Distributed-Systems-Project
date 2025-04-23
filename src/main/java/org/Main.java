package org;

import org.ClientSide.ClientHandler;
import org.ClientSide.ClientStates.ClientState;
import org.Domain.Client;
import org.Domain.Location;
import org.ServerSide.ClientCommunicate;
import org.ServerSide.TCPClient;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        Client client = new Client("BigTso", new Location(45, 45));
        Thread cl = new ClientHandler(client);
        cl.start();
    }
}
