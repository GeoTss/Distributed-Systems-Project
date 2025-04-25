package org.ManagerSide;

import org.ServerSide.ConnectionType;
import org.ServerSide.MasterServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ManagerHandler extends Thread{

    ObjectOutputStream outputStream;
    ObjectInputStream inputStream;

    @Override
    public void run(){
        Socket request_socket = null;
        try {
            request_socket = new Socket(MasterServer.SERVER_LOCAL_HOST, MasterServer.SERVER_CLIENT_PORT);

            ObjectOutputStream outputStream = new ObjectOutputStream(request_socket.getOutputStream());
            ObjectInputStream inputStream = new ObjectInputStream(request_socket.getInputStream());

            outputStream.writeInt(ConnectionType.MANAGER.ordinal());
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                outputStream.close();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
