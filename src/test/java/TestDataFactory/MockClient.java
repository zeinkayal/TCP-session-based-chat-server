package TestDataFactory;

import Builder.ConnectionHandler;

import java.io.*;
import java.net.Socket;

public class MockClient {
    private static final String hostName="localhost";
    private static Socket socket;
    public static void  startClient(int port) {
        try{
            socket=new Socket(hostName,port);
            ConnectionHandler connectionHandler = new ConnectionHandler(socket);
            connectionHandler.construct();
        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
        }

    }

    public static void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}


