package org.ClientSide;

import org.ClientSide.ClientStates.ClientHandlerInfo;
import org.ClientSide.ClientStates.ClientState;
import org.ClientSide.ClientStates.InitialState;
import org.Domain.Client;
import org.ServerSide.Command;
import org.ServerSide.MasterServer;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {

    private Client client_info;

    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    public ClientHandler(Client _client_info){
        client_info = _client_info;
    }

    @Override
    public void run(){

        try {
            Socket request_socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);

            outputStream = new ObjectOutputStream(request_socket.getOutputStream());
            inputStream = new ObjectInputStream(request_socket.getInputStream());

            outputStream.writeObject(client_info);
            outputStream.flush();

            ClientHandlerInfo clientHandlerInfo = new ClientHandlerInfo();
            clientHandlerInfo.outputStream = outputStream;
            clientHandlerInfo.inputStream = inputStream;

            ClientState initial_state = new InitialState();
            initial_state.handleState(clientHandlerInfo, null);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
