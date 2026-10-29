package Tests;

import Clients.ClientConnection;
import GlobalHolders.GlobalMapHolder;
import GlobalHolders.GlobalRouter;
import Manager.Session;
import Manager.SessionManager;
import TestDataFactory.MockClient;
import TestDataFactory.MockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class TestForConnectionLifeCycleAndSessionCleanUp {
    private static ClientConnection clientConnection;
    private static final SessionManager sessionManager=new SessionManager();
    private static Session session;
    private static Thread streamerThread;
    @BeforeEach
     void initializeConnection(){
        clientConnection=MockServer.startServer();
        session=sessionManager.create(clientConnection,"123","z.chat");
        streamerThread=new Thread(()->
                GlobalRouter.SHARED_ROUTER.streamAndCleanUp(clientConnection,session)
        );
    }
    @Test
    void connectionLifeCycleTest() {
        assertTrue(clientConnection.getIsAlive());
        assertNull(clientConnection.getHeartBeatMonitor());
        clientConnection.startHeartBeatMonitor();
        await()
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(()->assertFalse(clientConnection.getIsAlive()
                        ,"isAlive should flip to false within 6 seconds interval"));
        streamerThread.start();
        await()
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(()->assertTrue(clientConnection.getIsAlive()
                        ,"isAlive should have been flipped back to true "));

    }
    @AfterEach
    void cleanUp(){
        MockClient.closeSocket();
    }
    @Test
    void sessionCleanUpTest(){
        assertNull(clientConnection.getHeartBeatMonitor());
        assertTrue(GlobalMapHolder.SHARED_MAP.contains(session));
        streamerThread.start();
        MockClient.closeSocket();
        await()
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(()->assertTrue(session.participants().isEmpty()));
        await()
                .atMost(Duration.ofSeconds(6))
                .untilAsserted(()->assertTrue(GlobalMapHolder.SHARED_MAP.isEmpty()));

    }

}
