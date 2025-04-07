package org;

import org.Domain.Client;
import org.ServerSide.ClientCommunicate;
import org.ServerSide.TCPClient;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {

        for(int i = 0; i < 10; ++i) {
            Thread cl = new ClientCommunicate();
            cl.start();
        }
    }
}
