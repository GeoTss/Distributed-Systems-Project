package org.StatePattern;

import org.Domain.Utils.Pair;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

public class HandlerInfo {
    public ObjectOutputStream outputStream;
    public ObjectInputStream inputStream;
    public Queue<StateTransition> transition_queue = new LinkedList<>();
    public Queue<Pair<Runnable, Pair<Boolean, LockStatus>>> output_queue = new LinkedList<>();
}
