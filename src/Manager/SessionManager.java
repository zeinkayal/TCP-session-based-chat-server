package Manager;


import Clients.ClientConnection;
import GlobalHolders.GlobalMapHolder;
import java.util.Objects;

public class SessionManager {
public Session create(ClientConnection connection, String password, String sessionId){
    Session newSession=new Session(sessionId,password,connection);
    Session similarSession=GlobalMapHolder.SHARED_MAP.putIfAbsent(sessionId,newSession);
    if(similarSession!=null){
    connection.send("ERROR, A session with the same name already exits");
    return null;}
    connection.send("OK, Session successfully created");
    return newSession;
}
public Session join(ClientConnection connection,String password,String sessionId){
   Session session= GlobalMapHolder.SHARED_MAP.get(sessionId);
    if(session==null){
        connection.send("ERROR, No Such Session Exists");
        return null;
    }
    else if(!Objects.equals(password,session.password())){
        connection.send("ERROR, Wrong password! Please enter the password again");
        return null;
    }
    if(!session.addParticipant(connection)){
        connection.send("ERROR, No Such Session Exists");
        return null;}
    connection.send("OK, Successfully joined session");
    return session;
}

}
