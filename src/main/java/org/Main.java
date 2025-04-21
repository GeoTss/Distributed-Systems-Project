package org;

import org.ServerSide.ClientCommunicate;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {

        for(int i = 0; i < 1; ++i) {
            Thread cl = new ClientCommunicate();
            cl.start();
        }
    }
}
