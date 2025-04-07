package org.ServerSide;

public class RequestMonitor {

    private Object result = null;
    private boolean already_answered = false;

    public synchronized void setResult(Object value){
        if(already_answered) return;
        result = value;
        already_answered = true;
        notify();
    }

    public synchronized Object getResult() throws InterruptedException {
        while (result == null){
            wait();
        }
        return result;
    }
}
