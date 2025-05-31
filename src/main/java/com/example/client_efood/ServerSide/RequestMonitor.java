package com.example.client_efood.ServerSide;

import java.util.concurrent.TimeoutException;

public class RequestMonitor {

    private Object result = null;
    private boolean already_answered = false;

    public synchronized void setResult(Object value){
        if(already_answered) return;
        result = value;
        already_answered = true;
        notify();
    }

    public synchronized <T> T getResult() throws InterruptedException {
        while (result == null){
            wait();
        }
        return (T) result;
    }

    public synchronized <T> T getResult(long timeoutMillis) throws TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (result == null) {
            long toWait = deadline - System.currentTimeMillis();
            if (toWait <= 0) throw new TimeoutException("worker timeout");
            wait(toWait);
        }
        return (T) result;
    }
}
