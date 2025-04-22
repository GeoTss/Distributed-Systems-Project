package org;

import org.Domain.Client;
import org.ServerSide.ClientCommunicate;
import org.ServerSide.TCPClient;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {

        Thread cl = new ClientCommunicate();
        cl.start();

    }
}
