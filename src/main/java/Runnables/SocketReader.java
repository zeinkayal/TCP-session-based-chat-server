package Runnables;

import Builder.ConnectionHandler;

import java.io.BufferedReader;
import java.io.IOException;
public class SocketReader implements Runnable{
    private final BufferedReader socketInput;
    private final ConnectionHandler connectionHandler;
    public SocketReader(BufferedReader bufferedReader,ConnectionHandler connectionHandler){
        this.socketInput=bufferedReader;
        this.connectionHandler=connectionHandler;
    }
    @Override
    public void run() {
       try{
           String msg;
           while ((msg=socketInput.readLine())!=null){
               if("PING".equals(msg)){
                   connectionHandler.echoHeartBeat();
                   continue;
               }
               System.out.println(msg);
           }
       } catch (IOException e) {
           System.out.println("Reader Error: "+e.getMessage());
       }

    }
}

