package Builder;

import GlobalHolders.GlobalSystemReader;
import Runnables.ConsoleWriter;
import Runnables.SocketReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;

public class ConnectionHandler {
    private final PrintWriter printWriter ;
    private final BufferedReader bufferedReader;
    public ConnectionHandler(Socket socket) throws IOException {
        this.printWriter=new PrintWriter(socket.getOutputStream(), true);
        this.bufferedReader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }
    public void getName() throws IOException {
            String name;
            do {
                System.out.println("Enter the name you will use throughout this session:");
                name = GlobalSystemReader.reader.readLine();
                if (name == null) throw new IOException("stdin closed");
            } while (name.isBlank());
            printWriter.println(name.trim());
        }

    public boolean Initialize() throws IOException {
        BufferedReader reader= GlobalSystemReader.reader;
        System.out.println("Enter Join followed by the Session name then the password to join a room:");
        System.out.println("Enter Create followed by the Session name then the password to join a room:");
        String[] msg = reader.readLine().trim().split(" ");
        if(msg.length!=3){
            System.out.println("Wrong Format! plz try again");
             return false;
        }
        else if(msg[0].equals("Join")){
            printWriter.println("JOIN,"+msg[1]+","+msg[2]);
            return isSuccessful();
        }
        else if(msg[0].equals("Create")){
            printWriter.println("CREATE,"+msg[1]+","+msg[2]);
            return isSuccessful();
        }
        else{
            System.out.println("Wrong Format! plz try again");
        return false;}
    }

    private boolean isSuccessful() throws IOException {
        String[] msg;
        msg=bufferedReader.readLine().trim().split(",");
        if(msg[0].equals("ERROR")){
            System.out.println(Arrays.toString(msg));
            return false;
        }
        System.out.println(Arrays.toString(msg));
        return true;
    }

    public void echoHeartBeat(){printWriter.println("PONG");}
    public void construct() throws IOException , InterruptedException {
        ConsoleWriter writer=new ConsoleWriter(GlobalSystemReader.reader,printWriter);
        Thread writerThread= new Thread(writer);
        writerThread.setDaemon(true);
        SocketReader socketReader=new SocketReader(bufferedReader,this);
        Thread readerThread=new Thread(socketReader);
        readerThread.start();
        writerThread.start();
        readerThread.join();
    }
}
