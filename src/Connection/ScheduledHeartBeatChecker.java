package Connection;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import Clients.ClientConnection;

public class ScheduledHeartBeatChecker {
    private static final ScheduledExecutorService scheduledService= Executors.newScheduledThreadPool(4);
    public static ScheduledFuture<?> sendPingTask(ClientConnection clientConnection){
        return scheduledService.scheduleAtFixedRate(()-> {
            if (clientConnection.consumeIsAlive()) {
                clientConnection.send("PING");
            }
            else {
                clientConnection.killHeartBeatMonitor();
                try {
                    clientConnection.getSocket().close();
                } catch (IOException ignored) {
                }
            }
        },5,5,TimeUnit.SECONDS);
    }

}
