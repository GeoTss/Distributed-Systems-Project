package com.example.client_efood.ManagerSide;

import com.example.client_efood.Domain.Utils;
import com.example.client_efood.MessagePKG.MessageType;
import com.example.client_efood.StatePattern.HandlerInfo;
import com.example.client_efood.StatePattern.LockStatus;
import com.example.client_efood.StatePattern.StateTransition;
import com.example.client_efood.ManagerSide.ManagerStates.ManagerState;
import com.example.client_efood.ServerSide.ConnectionType;
import com.example.client_efood.ServerSide.MasterServer;
import com.example.client_efood.StatePattern.StateArguments;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class ManagerHandler {

    public static Scanner sc_input = new Scanner(System.in);

    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private HandlerInfo handler_info;

    public void start() {
        new Thread(this::stateLoop).start();
        new Thread(() -> {
            try {
                outputLoop();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }).start();
    }

    public void startingPoint() throws IOException {

        Socket request_socket = new Socket(MasterServer.SERVER_HOST, MasterServer.SERVER_CLIENT_PORT);

        outputStream = new ObjectOutputStream(request_socket.getOutputStream());
        inputStream = new ObjectInputStream(request_socket.getInputStream());

        outputStream.writeInt(ConnectionType.MANAGER.ordinal());
        outputStream.flush();

        handler_info = new HandlerInfo();
        handler_info.outputStream = outputStream;
        handler_info.inputStream = inputStream;

        start();
    }

    public void outputLoop() throws InterruptedException {
        while (true) {
            Utils.Pair<Runnable, Utils.Pair<Boolean, LockStatus>> entry;

            synchronized (handler_info.output_queue) {
                while (handler_info.output_queue.isEmpty()) {
                    handler_info.output_queue.wait();
                }
                entry = handler_info.output_queue.poll();
            }
            if(entry == null)
                break;

            Runnable task = entry.first;
            boolean should_notify = entry.second.first;
            LockStatus lock = entry.second.second;

            synchronized (System.in) {
                task.run();
            }

            if (should_notify && lock != null) {
                synchronized (lock.input_lock) {
                    lock.input_status[0] = 1;
                    lock.input_lock.notify();
                }
            }
        }
    }

    public void stateLoop() {
        try {
            ManagerState current_state;
            StateArguments current_args;
            StateTransition transition = new StateTransition(
                    ManagerState.State.INITIAL.getCorresponding_state(), null
            );

            while (true) {
                current_state = (ManagerState) transition.nextState;
                current_args = transition.nextArgs;
                current_state.handleState(handler_info, current_args);

                synchronized (handler_info.transition_queue) {
                    while (handler_info.transition_queue.isEmpty()) {
                        handler_info.transition_queue.wait();
                    }
                    transition = handler_info.transition_queue.poll();
                }

                if (transition == null)
                    break;
            }

            outputStream.writeInt(MessageType.QUIT.ordinal());
            outputStream.flush();
        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
                sc_input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
