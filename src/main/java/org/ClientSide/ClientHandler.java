package org.ClientSide;

import org.ClientSide.ClientStates.ClientHandlerInfo;
import org.ClientSide.ClientStates.ClientState;
import org.ClientSide.ClientStates.ClientStateArgs.ClientStateArgument;
import org.ClientSide.ClientStates.InitialState;
import org.ClientSide.ClientStates.StateTransition;
import org.Domain.Client;
import org.ServerSide.Command;
import org.ServerSide.MasterServer;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientHandler extends Thread {

    public static Scanner sc_input = new Scanner(System.in);

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

            ClientHandlerInfo handler_info = new ClientHandlerInfo();
            handler_info.outputStream = outputStream;
            handler_info.inputStream = inputStream;

            ClientState.State currentState = null;
            ClientStateArgument currentArgs = null;

            StateTransition transition = new StateTransition(ClientState.State.INITIAL, null);

            do {
                currentState = transition.nextState;
                currentArgs = transition.nextArgs;

                ClientState currentHandler = currentState.getCorresponding_state();
                transition = currentHandler.handleState(handler_info, currentArgs);
            } while(transition != null);

            outputStream.writeInt(Command.CommandTypeClient.QUIT.ordinal());
            outputStream.flush();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }finally {
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
