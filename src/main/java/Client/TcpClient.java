package Client;

import Builder.ConnectionHandler;

import java.io.*;
import java.net.Socket;

public class TcpClient {
    public static final int port= 8080;
    public static final String hostName="localhost";
    public static void main(String[] args) {
        boolean success;
        try (Socket socket = new Socket(hostName, port)) {
                ConnectionHandler connectionHandler = new ConnectionHandler(socket);
                connectionHandler.getName();
                do {success=connectionHandler.Initialize();} while (!success);
                connectionHandler.construct();
            } catch (IOException | InterruptedException e) {
                System.out.println(e.getMessage());
            }
        }
    }

