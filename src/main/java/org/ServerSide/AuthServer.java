package org.ServerSide;

import org.Domain.Credentials;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class AuthServer {
    private static final int PORT = 4321;
    private static final HashMap<String, String> users = new HashMap<>();
    private static final HashMap<String, String> activeSessions = new HashMap<>();

    public static void main(String[] args) {

        users.put("TheoM", "twrathagineixamos123");
        users.put("BigTso", "cppmoment123");
        users.put("Toto", "QuantumIzCheater1");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Authentication Server started. Listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                new ClientAuthHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static HashMap<String, String> getActiveSessions() {
        return activeSessions;
    }

    static class ClientAuthHandler extends Thread {
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private Socket socket;
        public ClientAuthHandler(Socket _socket) {
            this.socket = _socket;
        }
        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                Object receivedObject = in.readObject();
                if (receivedObject instanceof Credentials) {
                    Credentials _credentials = (Credentials) receivedObject;

                    System.out.println("Login attempt from: " + _credentials.getUsername());

                    if (users.containsKey(_credentials.getUsername()) &&
                        Objects.equals(users.get(_credentials.getUsername()), _credentials.getPassword())) {

                        String sessionToken = UUID.randomUUID().toString();
                        activeSessions.put(sessionToken, _credentials.getUsername());

                        System.out.println("Authentication successful! Token: " + sessionToken);

                        out.writeObject("SUCCESS: " + sessionToken);
                    } else {
                        out.writeObject("FAILURE");
                    }
                    out.flush();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    in.close();
                    out.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
