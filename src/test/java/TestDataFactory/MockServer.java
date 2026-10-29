package TestDataFactory;

import Clients.ClientConnection;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MockServer {
    public static ClientConnection startServer() {
        ClientConnection clientConnection;
    try (ServerSocket serverSocket=new ServerSocket(8080)){
        Thread client=new Thread(()->MockClient.startClient(serverSocket.getLocalPort()));
        client.start();
        Socket socket=serverSocket.accept();
        clientConnection=new ClientConnection(socket);
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
    return clientConnection;
    }

}
