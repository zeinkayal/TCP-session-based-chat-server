package Server;

import Clients.ClientConnection;
import GlobalHolders.GlobalRouter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpServer {
    private static final int port=8080;
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientConnection connection=new ClientConnection(clientSocket);
                Thread thread = new Thread(()->
                    GlobalRouter.SHARED_ROUTER.start(connection)
                );

                thread.start();

            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
