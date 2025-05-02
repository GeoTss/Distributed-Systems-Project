package org.ManagerSide;

import org.ClientSide.ClientHandler;
import org.Domain.Client;
import org.Domain.Location;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        ManagerHandler manager_handler = new ManagerHandler();
        manager_handler.startingPoint();
    }
}
