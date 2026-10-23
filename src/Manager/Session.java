package Manager;

import Clients.ClientConnection;
import GlobalHolders.GlobalMapHolder;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

public record Session(String password, CopyOnWriteArrayList<ClientConnection> participants, String id, ScheduledFuture<?> sendPingTask) {
    public Session(String id, String password, ClientConnection clientConnection) {
        this(password, new CopyOnWriteArrayList<>(), id,null);
        this.participants.add(clientConnection);
    }

    public synchronized boolean addParticipant(ClientConnection connection) {
        if(this.participants.isEmpty()){return false;}
        participants.add(connection);
        return true;
    }

    public synchronized void remove(ClientConnection connection) {
        participants.remove(connection);
        if (this.participants.isEmpty()) {
            GlobalMapHolder.SHARED_MAP.remove(this.id);
        }
    }
}
