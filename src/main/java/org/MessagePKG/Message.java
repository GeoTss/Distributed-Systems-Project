package org.MessagePKG;

import org.Domain.Utils.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class Message implements Serializable {

    private HashMap<String, Pair<MessageArgCast, Object>> argument_list = new HashMap<>();
    private int arg_index = 0;

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
        argument_list.forEach((tag, raw_arg) -> str.append("[" + tag + "] -> " + raw_arg.first.getCastedArg(raw_arg.second)));
        return str.toString();
    }
}
