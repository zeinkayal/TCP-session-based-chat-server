package TestDataFactory;

import Clients.ClientConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockServerForMultiClients {
    public static final CopyOnWriteArrayList<ClientConnection> clientConnections =new CopyOnWriteArrayList<>();
    public static void startServer(){
        try(ServerSocket serverSocket=new ServerSocket(8080)){
            while (true){
                Socket clientSocket=serverSocket.accept();
                ClientConnection clientConnection=new ClientConnection(clientSocket);
                clientConnections.add(clientConnection);
            }
        }
        catch(IOException e){
            throw new RuntimeException(e);
        }
    }
}
