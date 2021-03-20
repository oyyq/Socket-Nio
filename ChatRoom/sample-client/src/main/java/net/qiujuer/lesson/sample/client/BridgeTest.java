package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.box.FileSendPacket;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;
import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * 测试2个客户端切换成桥接模式并互发消息
 */
public class BridgeTest {

    private static final int CLIENT_SIZE = 2;
    private static final int SEND_THREAD_DELAY = 20;
    public static final String DeskTop = "/Users/ouyangyunqing/Desktop/portrait.zip";
    public static final String roomname = "OYYQ";
    private static List<TCPClient> tcpClients = new ArrayList<>(CLIENT_SIZE);
    private static volatile boolean done = false;

    public static void main(String[] args) throws IOException {

        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);
        if (info == null) {
            return;
        }

        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .scheduler(new SchedulerImpl(1))
                .start();

        int size = 0;

        for (int i = 0; i < CLIENT_SIZE; i++) {
            try {
                File cachePath = Foo.getCacheDir("client/bridgeTest"+i);
                TCPClient tcpClient = TCPClient.startWith(info, cachePath, true);
                if (tcpClient == null) { throw new NullPointerException(); }

                tcpClients.add(tcpClient);
                System.out.println("连接成功：" + (++size));
            } catch (IOException | NullPointerException e) {
                System.out.println("连接异常");
                break;
            }
        }


        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        input.readLine();

        try {
            BridgeCommand(input);
            StartChat(input);
        }finally {
            for(int i = 0; i< CLIENT_SIZE; i++)
                tcpClients.get(i).exit();
        }

        IoContext.close();
    }


    private static void singleClientSend(TCPClient tcpClient, List<FileSendPacket> filePackets){
        for(int i = 0; i < filePackets.size(); i++) tcpClient.send(filePackets.get(i));
    }


    private static void BridgeCommand(BufferedReader input) throws IOException {
        int command_send = 0;

        do{
            String str = input.readLine();
            if(str.equalsIgnoreCase("join")) {
                String command = Foo.COMMAND_ENTER_ROOM+roomname;
                tcpClients.get(command_send%CLIENT_SIZE).send(command);
                command_send++;
            }

        }while (command_send < CLIENT_SIZE);

    }


    private static void StartChat(BufferedReader input) throws IOException {
        while (!done) {
            String mes = input.readLine();
            if(mes.equalsIgnoreCase("byebye")) {
                done = true;
            } else {
                tcpClients.get(0).send(mes);
            }
        }

    }


}
