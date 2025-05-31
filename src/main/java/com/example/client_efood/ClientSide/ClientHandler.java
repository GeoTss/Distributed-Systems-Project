package com.example.client_efood.ClientSide;

import com.example.client_efood.ClientSide.ClientStates.ClientStates;
import com.example.client_efood.Domain.Utils;
import com.example.client_efood.Domain.Utils.Pair;
import com.example.client_efood.MessagePKG.Message;
import com.example.client_efood.MessagePKG.MessageArgCast;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.StatePattern.HandlerInfo;
import com.example.client_efood.StatePattern.LockStatus;
import com.example.client_efood.StatePattern.StateArguments;
import com.example.client_efood.StatePattern.StateTransition;
import com.example.client_efood.Domain.Client;
import com.example.client_efood.ServerSide.ConnectionType;
import com.example.client_efood.ServerSide.MasterServer;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientHandler {

    public static Scanner sc_input = new Scanner(System.in);

    private Client client_info;

    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    private HandlerInfo handler_info;

    public ClientHandler(Client _client_info){
        client_info = _client_info;
    }

    public void start(){
        new Thread(this::stateLoop).start();
        new Thread(() -> {
            try {
                outputLoop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    public void outputLoop() throws InterruptedException {
        Pair<Runnable, Pair<Boolean, LockStatus>> output;
        while(true) {
            synchronized (handler_info.output_queue) {
                while (handler_info.output_queue.isEmpty()) {
                    handler_info.output_queue.wait();
                }
                output = handler_info.output_queue.poll();
            }

            if(output == null)
                break;

            Runnable task = output.first;

            boolean should_notify = output.second.first;
            LockStatus lock = output.second.second;

            synchronized (System.in) {
                if(task != null)
                    task.run();
                else
                    continue;
            }

            if (lock != null) {
                lock.input_status[0] = 1;
                synchronized (lock.input_lock) {
                    lock.input_lock.notify();
                }
            }
        }
    }

    public void startingPoint() throws IOException {

        Socket request_socket = new Socket(MasterServer.SERVER_HOST, MasterServer.SERVER_CLIENT_PORT);

        outputStream = new ObjectOutputStream(request_socket.getOutputStream());
        inputStream = new ObjectInputStream(request_socket.getInputStream());

        outputStream.writeInt(ConnectionType.CLIENT.ordinal());
        outputStream.flush();

        Message client_info = new Message();
        client_info.addArgument("command_ord", new Utils.Pair<>(MessageArgCast.INT_ARG, MessageType.SET_INFO.ordinal()));
        client_info.addArgument("username", new Utils.Pair<>(MessageArgCast.STRING_CAST, "BigTso"));
        client_info.addArgument("longitude", new Utils.Pair<>(MessageArgCast.DOUBLE_ARG, 23.7275));
        client_info.addArgument("latitude", new Utils.Pair<>(MessageArgCast.DOUBLE_ARG, 37.9838));
        client_info.addArgument("balance", new Utils.Pair<>(MessageArgCast.FLOAT_ARG, 100000.0f));

        outputStream.writeObject(client_info);
        outputStream.flush();

        handler_info = new HandlerInfo();
        handler_info.outputStream = outputStream;
        handler_info.inputStream = inputStream;

        start();
    }

    public void stateLoop(){

        try {

            ClientStates current_state = null;
            StateArguments current_args = null;

            StateTransition transition = new StateTransition(ClientStates.State.INITIAL.getCorresponding_state(), null);

            while(true){

                current_state = (ClientStates) transition.nextState;
                current_args = transition.nextArgs;

                current_state.handleState(handler_info, current_args);

                synchronized (handler_info.transition_queue) {
                    while (handler_info.transition_queue.isEmpty()) {
                        handler_info.transition_queue.wait();
                    }
                    transition = handler_info.transition_queue.poll();
                }

                if (transition == null) break;
            }

            outputStream.writeInt(MessageType.QUIT.ordinal());
            outputStream.flush();

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
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
