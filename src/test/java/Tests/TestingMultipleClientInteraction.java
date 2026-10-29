package Tests;

import Clients.ClientConnection;
import GlobalHolders.GlobalMapHolder;
import GlobalHolders.GlobalRouter;
import Manager.Session;
import TestDataFactory.MockServerForMultiClients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class TestingMultipleClientInteraction{
    @BeforeAll
    public static void startServer(){
        Thread serverThread = Thread.ofVirtual()
                .name("MultiClientServer thread",1)
                .start(MockServerForMultiClients::startServer);
    }
    public void msgWriter(Socket socket, String msg){
        try {
            PrintWriter printWriter=new PrintWriter(socket.getOutputStream(),true);
            printWriter.println(msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public String msgReader(Socket socket)  {
        try {
            socket.setSoTimeout(3000);
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }
    @Test
    void multiClientChatRoomBehaviourTest(){
        Socket client1;
        Socket client2;
        ClientConnection clientConnection1;
        ClientConnection clientConnection2;
        try {
            client1 =new Socket("localhost",8080);
            await()
                    .atMost(Duration.ofSeconds(3))
                    .untilAsserted(()-> assertFalse(MockServerForMultiClients.clientConnections.isEmpty()));
            clientConnection1=MockServerForMultiClients.clientConnections.getFirst();
            client2=new Socket("localhost",8080);
            await()
                    .atMost(Duration.ofSeconds(3))
                    .untilAsserted(() ->
                            assertEquals(2, MockServerForMultiClients.clientConnections.size()));

            clientConnection2=MockServerForMultiClients.clientConnections.getLast();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        clientConnection1.setClientName("client1");
        clientConnection2.setClientName("client2");
        String msg1,msg2;
        Session session=new Session("z.chat","123",clientConnection1);
        session.addParticipant(clientConnection2);
        GlobalMapHolder.SHARED_MAP.put(session.id(),session);
        Thread connectionThread1=Thread
                .ofVirtual()
                .start(()-> GlobalRouter.SHARED_ROUTER.streamAndCleanUp(clientConnection1,session));
        Thread connectionThread2=Thread
                .ofVirtual()
                .start(()-> GlobalRouter.SHARED_ROUTER.streamAndCleanUp(clientConnection2,session));
        msgWriter(client1,"hey");
        msg1=msgReader(client2);
        msg2=msgReader(client1);
        assertEquals("client1 : hey",msg1);
        assertNull(msg2);
    }
}
