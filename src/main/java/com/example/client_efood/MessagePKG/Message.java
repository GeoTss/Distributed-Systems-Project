package com.example.client_efood.MessagePKG;

import com.example.client_efood.Domain.Utils.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private HashMap<String, Pair<MessageArgCast, Object>> argument_list = new HashMap<>();

    public Message() {}

    public Message(Message other) {
        if (other != null && other.argument_list != null) {
            for (Map.Entry<String, Pair<MessageArgCast, Object>> entry : other.argument_list.entrySet()) {

                this.argument_list.put(entry.getKey(), new Pair<>(entry.getValue().first, entry.getValue().second));
            }
        }
    }

    public <T> T getArgument(String tag){
        Pair<MessageArgCast, Object> arg_pair = argument_list.get(tag);
        return arg_pair.first.getCastedArg(arg_pair.second);
    }

    public void addArgument(String tag, Pair<MessageArgCast, Object> arg){
        argument_list.put(tag, arg);
    }

    public void replaceArgument(String tag, Pair<MessageArgCast, Object> arg){
        argument_list.put(tag, arg);
    }

    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();
        str.append("{\n");
        argument_list.forEach((tag, raw_arg) -> {
            if(raw_arg.first == MessageArgCast.ARRAY_LIST_ARG)
                str
                    .append("[" + tag + "] -> " + ((ArrayList<?>) raw_arg.first.getCastedArg(raw_arg.second)).size())
                    .append('\n');
            else
                str
                    .append("[" + tag + "] -> " + raw_arg.first.getCastedArg(raw_arg.second))
                    .append('\n');
        }
        );
        str.append("}");
        return str.toString();
    }
}
