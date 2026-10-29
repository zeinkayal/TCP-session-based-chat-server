package Runnables;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

public class ConsoleWriter implements Runnable{
   private final PrintWriter out;
   private final BufferedReader in;
    public ConsoleWriter(BufferedReader bufferedReader,PrintWriter printWriter){
       this.in=bufferedReader;
       this.out=printWriter;
    }
    @Override
    public void run(){
     try{
        String msg;
        while((msg=in.readLine())!=null){
           out.println(msg);
            if("./Exit".equals(msg)){
                out.println("closing socket...");
                return;}
        }
     }catch (IOException e){System.out.println("Writer Error: "+e.getMessage());}
    }
}
