package com.example.administrator.test4tansferring;

import android.os.Environment;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Created by Administrator on 2018/2/6.
 */

public class InternetUtils
{
    /**
     *
     * @return 所有连接此设备热点的设备的ip，例如 192.168.43.114
     */
    private static ArrayList<String> getConnectedIP() {
        ArrayList<String> connectedIP = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                    "/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                Log.i("tag0", line);
                String[] splitted = line.split(" +");
                if (splitted != null && splitted.length >= 4) {
                    String ip = splitted[0];
                    connectedIP.add(ip);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return connectedIP;
    }

    /**
     *
     * @return 本机ip(本地网ip)，例如 192.168.43.1
     */
    public static String getHostIP() {

        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.i("yao", "SocketException");
            e.printStackTrace();
        }
        return hostIp;
    }

    /**
     * 向服务器传文件
     * 调用前要保证服务器端在接受文件状态，就是说先要服务器调用fileReceive，使其处于接收文件状态。否则可能出错
     * 不要放进主线程，阻塞
     *
     * @param file
     * @param serverIP 服务器的ip，即对方的ip
     * @param serverPort 服务器端口，必须和服务端开启的端口一致，就是说要和服务器调用fileReceive时传参的端口一致
     */
    @WorkerThread
    public static void fileTransfer(File file, String serverIP, int serverPort)
    {
        try
        {
            FileTransferClient client = new FileTransferClient(serverIP, serverPort); // 启动客户端连接
            client.sendFile(file); // 传输文件
            client.close();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 向服务器发送字符串
     * 必须让作为服务器的设备先调用receiveMsg方法
     * @param msg
     * @param serverIP
     * @param serverPort
     */
    @WorkerThread
    public static void sendMsg(String msg, String serverIP, int serverPort)
    {
        try
        {
            Socket socket = new Socket(serverIP, serverPort);

            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

            dos.writeInt(msg.length());
            dos.flush();

            System.out.println("======== 开始传输 ========");

            dos.write(msg.getBytes(), 0, msg.length());
            dos.flush();
            socket.close();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 作为服务器，接受别的设备(客户端)传来的文件
     * 等待，直到接收到文件
     * 一次调用只接收一个
     * 不要放进主线程，阻塞
     * @param savedDir 接收到文件的保存目录。不存在会创建。文件名和发送者那边的文件名相同
     * @param serverPort 服务器端口，1024 ~ 65535都行
     */
    @WorkerThread
    public static void fileReceive(File savedDir, int serverPort)
    {
        ServerSocket serverSocket = null;
        Socket socket = null;
        try
        {
            serverSocket = new ServerSocket(serverPort);

            // server尝试接收其他Socket的连接请求，注意这步是阻塞式的！！！
            socket = serverSocket.accept();
        } catch (Exception e)
        {
            e.printStackTrace();
        }


        /**
         * 我们的服务端处理客户端的连接请求是同步进行的， 每次接收到来自客户端的连接请求后，
         * 都要先跟当前的客户端通信完之后才能再处理下一个连接请求。 这在并发比较多的情况下会严重影响程序的性能，
         * 为此，我们可以把它改为如下这种异步处理与客户端通信的方式
         */
        // 每接收到一个Socket就建立一个新的线程来处理它
        new Thread(new Task(socket, savedDir)).start();
    }


    @WorkerThread
    public static byte[] receiveMsg(int serverPort)
    {
        ServerSocket serverSocket = null;
        Socket socket = null;
        try
        {
            serverSocket = new ServerSocket(serverPort);

            // server尝试接收其他Socket的连接请求，注意这步是阻塞式的！！！
            socket = serverSocket.accept();
            DataInputStream dis = new DataInputStream(socket.getInputStream());

            int length = dis.readInt();

            byte[] bytes = new byte[length];
            dis.read(bytes, 0, length);
            return bytes;
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }





    private static class Task implements Runnable {

        private Socket socket;
        private File savedDir;

        private DataInputStream dis;

        private FileOutputStream fos;

        public Task(Socket socket, File savedDir) {
            this.socket = socket;
            this.savedDir = savedDir;
        }

        @Override
        public void run() {
            try {
                dis = new DataInputStream(socket.getInputStream());

                // 文件名和长度
                String fileName = dis.readUTF();
                long fileLength = dis.readLong();
                if(!savedDir.exists()) {
                    savedDir.mkdir();
                }
                File file = new File(savedDir.getAbsolutePath() + File.separatorChar + fileName);
                fos = new FileOutputStream(file);

                // 开始接收文件
                byte[] bytes = new byte[1024];
                int length = 0;
                while((length = dis.read(bytes, 0, bytes.length)) != -1) {
                    fos.write(bytes, 0, length);
                    fos.flush();
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if(fos != null)
                        fos.close();
                    if(dis != null)
                        dis.close();
                    socket.close();
                } catch (Exception e) { }
            }
        }
    }






    /**
     * 文件传输Client端<br>
     * 功能说明：
     *
     * @author 大智若愚的小懂
     * @Date 2016年09月01日
     * @version 1.0
     */
    private static class FileTransferClient extends Socket {

        private Socket client;

        private FileInputStream fis;

        private DataOutputStream dos;


        public FileTransferClient(String serverIP, int serverPort) throws Exception {
            super(serverIP, serverPort);
            this.client = this;
            System.out.println("Cliect[port:" + client.getLocalPort() + "] 成功连接服务端");
        }

        /**
         * 向服务端传输文件
         * @throws Exception
         */
        public void sendFile(File file) throws Exception {
            try {
                if(file.exists()) {
                    fis = new FileInputStream(file);
                    dos = new DataOutputStream(client.getOutputStream());

                    // 文件名和长度
                    dos.writeUTF(file.getName());
                    dos.flush();
                    dos.writeLong(file.length());
                    dos.flush();

                    // 开始传输文件
                    System.out.println("======== 开始传输文件 ========");
                    byte[] bytes = new byte[1024];
                    int length = 0;
                    long progress = 0;
                    while((length = fis.read(bytes, 0, bytes.length)) != -1) {
                        dos.write(bytes, 0, length);
                        dos.flush();
                        progress += length;
                        System.out.print("| " + (100*progress/file.length()) + "% |");
                    }
                    System.out.println();
                    System.out.println("======== 文件传输成功 ========");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if(fis != null)
                    fis.close();
                if(dos != null)
                    dos.close();
                client.close();
            }
        }
    }
}
