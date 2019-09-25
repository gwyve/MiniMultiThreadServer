package src;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by VE on 2019/9/18.
 */
public class SimpleHttpServer {
    private ThreadPool<HttpRequestHandler> threadPool = new DefaultThreadPool<HttpRequestHandler>();
    private File rootFile;
    private ServerSocket serverSocket;
    private int port;

    public SimpleHttpServer(String rootDir,int port){
        this.rootFile = new File(rootDir);
        this.port = port;
    }

    public void start() throws Exception{
        serverSocket = new ServerSocket(this.port);
        Socket socket = null;
        while ((socket = serverSocket.accept())!=null){
            threadPool.execute(new HttpRequestHandler(socket,rootFile));
        }
        serverSocket.close();
    }

    public static void main(String[] args){
        if (args == null || args.length!=2){
            System.out.println("Error: filePath port");
            return;
        }
        String path = args[0];
        int port = Integer.parseInt(args[1]);
        SimpleHttpServer server = new SimpleHttpServer(path,port);
        try {
            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}