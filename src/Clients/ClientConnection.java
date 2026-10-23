package Clients;

import Connection.ScheduledHeartBeatChecker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientConnection {
    private final Socket socket;
    private String clientName;
    private final PrintWriter writer;
    private final BufferedReader  bufferedReader;
    private final AtomicBoolean isAlive=new AtomicBoolean(true);
    private ScheduledFuture<?> pingTask;
    public ClientConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.bufferedReader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public synchronized void send(String msg) {
        writer.println(msg);
    }

    public String read() throws IOException {
        return bufferedReader.readLine();
    }
    public void startHeartBeatMonitor(){
        pingTask= ScheduledHeartBeatChecker.sendPingTask(this);
    }
    public void killHeartBeatMonitor(){
        if(pingTask!=null){pingTask.cancel(true);}
    }
    public Socket getSocket() { return socket; }

    public boolean consumeIsAlive(){
        return isAlive.getAndSet(false);
    }
    public void setIsAlive(){
        isAlive.set(true);
    }
    public void setClientName() throws IOException {
        String name=this.read();
        clientName=(name==null)?null:name.trim();
    }
    public String getClientName(){
        return this.clientName;
    }

}
