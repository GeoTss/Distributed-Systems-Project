package com.example.client_efood.Workers;

import com.example.client_efood.MessagePKG.Message;

import java.io.Serializable;

public class DataChange implements Serializable {

    private final long timestamp;
    private String change_id;
    private final Message message;

    DataChange(Message _message){
        timestamp = System.currentTimeMillis();
        message = _message;

        long request_id = message.getArgument("request_id");
        String request_str = String.valueOf(request_id);

        change_id = String.format("%019d_%s", timestamp, request_str);
    }

    public Message getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }


    public String getChange_id() {
        return change_id;
    }

    @Override
    public String toString() {
        return "DataChange{" +
                "change_id='" + change_id + '\'' +
                ", message=" + message +
                '}';
    }
}
