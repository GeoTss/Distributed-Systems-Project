package org.Workers;

import org.MessagePKG.MessageType;
import org.ServerSide.MasterServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;

public class WorkerManager {

    public static void main(String[] args) throws IOException {

        Socket worker_initializer = null;
        ObjectOutputStream server_writer = null;
        ObjectInputStream server_input = null;
        try {
            worker_initializer = new Socket("127.0.0.1", MasterServer.SERVER_CLIENT_PORT);

            server_writer = new ObjectOutputStream(worker_initializer.getOutputStream());
            server_input = new ObjectInputStream(worker_initializer.getInputStream());

            int command_type = server_input.readInt();
            MessageType command = MessageType.values()[command_type];

            while (command != MessageType.END_OF_INITIALIZATION){
                System.out.println("Received " + command);
                WorkerClient new_worker_client = new WorkerClient();
                new_worker_client.start();

                command_type = server_input.readInt();
                command = MessageType.values()[command_type];
            }
        }catch (IOException e){
            if(worker_initializer != null) {
                assert server_writer != null && server_input != null;

                server_writer.close();
                server_input.close();
                worker_initializer.close();
            }
            e.printStackTrace();
        }finally {
            System.out.println("[Closing worker manager!]");
        }
    }

}
