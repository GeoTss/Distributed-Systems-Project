package org.ClientSide;

import org.ClientSide.ClientStates.ClientStates;
import org.Domain.Utils.Pair;
import org.ManagerSide.ManagerStates.ManagerState;
import org.StatePattern.HandlerInfo;
import org.StatePattern.LockStatus;
import org.StatePattern.StateArguments;
import org.StatePattern.StateTransition;
import org.Domain.Client;
import org.ServerSide.Command;
import org.ServerSide.ConnectionType;
import org.ServerSide.MasterServer;
import sun.misc.Signal;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class ClientHandler {

    public static Scanner sc_input = new Scanner(System.in);

    private Client client_info;

    private InetAddress wifiAddress;
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
                    System.out.println("[OutputDispatcher] Started.");
                }
                output = handler_info.output_queue.poll();
            }

            if(output == null)
                break;

            Runnable task = output.first;

            System.out.println("[OutputDispatcher] Got task: " + task);

            boolean should_notify = output.second.first;
            LockStatus lock = output.second.second;

            task.run();
//            System.out.println("[OutputDispatcher] Ran successfully: " + task);
//            System.out.println("Trying to enter the lock if... " + lock);
            if (lock != null) {
                lock.input_status[0] = 1;
//                System.out.println("[OutputDispatcher] Trying to notify...: " + lock);
                synchronized (lock.input_lock) {
                    lock.input_lock.notify();
//                    System.out.println("[OutputDispatcher] Notifying: " + task);
                }
            }
//            System.out.println("Exiting output pool");
        }
    }

    public void startingPoint() throws IOException {

        InetAddress wifiAddress = MasterServer.getWifiInetAddress();
        System.out.println("Inet Address: " + wifiAddress);
        Socket request_socket = new Socket(wifiAddress, MasterServer.SERVER_CLIENT_PORT);

        outputStream = new ObjectOutputStream(request_socket.getOutputStream());
        inputStream = new ObjectInputStream(request_socket.getInputStream());

        outputStream.writeInt(ConnectionType.CLIENT.ordinal());
        outputStream.flush();

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

            outputStream.writeInt(Command.CommandTypeClient.QUIT.ordinal());
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
