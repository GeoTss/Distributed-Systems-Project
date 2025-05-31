package com.example.client_efood.MessagePKG;

import com.example.client_efood.Domain.Utils.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private HashMap<String, Pair<MessageArgCast, Object>> argument_list = new HashMap<>();

    public <T> T getArgument(String tag){
        Pair<MessageArgCast, Object> arg_pair = argument_list.get(tag);
        return arg_pair.first.getCastedArg(arg_pair.second);
    }

    public void addArgument(String tag, Pair<MessageArgCast, Object> arg){
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
