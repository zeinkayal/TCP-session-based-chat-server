package Router;

import Clients.ClientConnection;
import GlobalHolders.GlobalMapHolder;
import Manager.Session;
import Manager.SessionManager;
import java.io.IOException;

public class Router {

    private static final SessionManager sessionManager=new SessionManager();
    public void start(ClientConnection connection){
        String[] msg;
        Session session=null;
        try {
            connection.setClientName();
            if (connection.getClientName() == null) {return;}
            while (session==null){
            String buffer= connection.read();
            if(buffer==null){return;}
            msg= buffer.trim().split(",");
            if(msg[0].equals("JOIN")){
               session=sessionManager.join(connection,msg[2],msg[1]);
            }
            else if(msg[0].equals("CREATE")){
                session=sessionManager.create(connection,msg[2],msg[1]);
            }
            else {
               connection.send("Please join or create a session to start chatting");
                return;
            }}
            connection.startHeartBeatMonitor();
            broadcastMessage(connection,session.id());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            connection.killHeartBeatMonitor();
            if(session!=null){
                session.remove(connection);
            }
            try {
                connection.getSocket().close();
            } catch (IOException ignored) {
            }
        }

    }
    public void broadcastMessage( ClientConnection connection,String sessionId) throws IOException {
        String msg;
        while((msg=connection.read())!=null&&!msg.equals("./Exit")){
            if(msg.equals("PONG")){
                connection.setIsAlive();
                continue;
            }
            for(ClientConnection c: GlobalMapHolder.SHARED_MAP.get(sessionId).participants()){
                if(c!=connection){
                    c.send(connection.getClientName()+" : "+msg);
                }
            }
        }
    }
}
