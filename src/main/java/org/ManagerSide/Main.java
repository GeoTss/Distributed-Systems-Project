package org.ManagerSide;

import org.ClientSide.ClientHandler;
import org.Domain.Client;
import org.Domain.Location;

public class Main {

    public static void main(String[] args) {
        Thread cl = new ManagerHandler();
        cl.start();
    }
}
