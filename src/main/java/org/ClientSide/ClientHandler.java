package org.ClientSide;

import org.ClientSide.ClientStates.ClientStates;
import org.StatePattern.HandlerInfo;
import org.StatePattern.StateArguments;
import org.StatePattern.StateInterface;
import org.StatePattern.StateTransition;
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

            HandlerInfo handler_info = new HandlerInfo();
            handler_info.outputStream = outputStream;
            handler_info.inputStream = inputStream;

            ClientStates currentState;
            StateArguments currentArgs;

            StateTransition transition = new StateTransition(ClientStates.State.INITIAL.getCorresponding_state(), null);

            do {
                currentState = (ClientStates) transition.nextState;
                currentArgs = transition.nextArgs;

                transition = currentState.handleState(handler_info, currentArgs);
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
