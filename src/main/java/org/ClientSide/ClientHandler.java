package org.ClientSide;

import org.ClientSide.ClientStates.ClientHandlerInfo;
import org.ClientSide.ClientStates.ClientState;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.ClientSide.ClientStates.StateTransition;
import org.Domain.Client;
import org.ServerSide.Command;
import org.ServerSide.ConnectionType;
import org.ServerSide.MasterServer;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class ClientHandler extends Thread {

    public static Scanner sc_input = new Scanner(System.in);

    private Client client_info;

    private InetAddress wifiAddress;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    public ClientHandler(Client _client_info){
        client_info = _client_info;
    }

    @Override
    public void run(){

        try {

            wifiAddress = MasterServer.getWifiInetAddress();
            System.out.println("Inet Address: " + wifiAddress);
            Socket request_socket = new Socket(wifiAddress, MasterServer.SERVER_CLIENT_PORT);

            outputStream = new ObjectOutputStream(request_socket.getOutputStream());
            inputStream = new ObjectInputStream(request_socket.getInputStream());

            outputStream.writeInt(ConnectionType.CLIENT.ordinal());
            outputStream.flush();

            outputStream.writeObject(client_info);
            outputStream.flush();

            ClientHandlerInfo handler_info = new ClientHandlerInfo();
            handler_info.outputStream = outputStream;
            handler_info.inputStream = inputStream;

            ClientState.State currentState;
            ClientStateArgument currentArgs;

            StateTransition transition = new StateTransition(ClientState.State.INITIAL, null);

            do {
                currentState = transition.nextState;
                currentArgs = transition.nextArgs;

                ClientState currentHandler = currentState.getCorresponding_state();
                transition = currentHandler.handleState(handler_info, currentArgs);
            } while(transition != null);

            outputStream.writeInt(Command.CommandTypeClient.QUIT.ordinal());
            outputStream.flush();

        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                outputStream.close();
                inputStream.close();
                sc_input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
