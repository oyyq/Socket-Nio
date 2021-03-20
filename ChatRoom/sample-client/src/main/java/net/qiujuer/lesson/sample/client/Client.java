package net.qiujuer.lesson.sample.client;

import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.FileDividePackets;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.library.clink.box.FileSendPacket;
import net.qiujuer.library.clink.box.StreamDirectSendPacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.core.ScheduleJob;
import net.qiujuer.library.clink.core.schedule.IdleTimeoutScheduleJob;
import net.qiujuer.library.clink.core.schedule.IdleTimeoutScheduleJob2;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;
import net.qiujuer.library.clink.utils.CloseUtils;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class Client  {

    static ServerInfo info;
    static File cachePath;
    private static HashMap<String, TCPClient> FileClients = new HashMap<>();

    public static void main(String[] args) throws IOException {
        cachePath = Foo.getCacheDir("client");
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .scheduler(new SchedulerImpl(1))
                .start();

        info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            TCPClient tcpClient = null;

            try {
                tcpClient = produce(new ConnectorCloseChain() {
                    @Override
                    protected boolean consume(ConnectorHandler handler, Connector connector) {
                        CloseUtils.close(System.in);
                        return true;
                    }
                });

                // 开始调度一份心跳包
                ScheduleJob scheduleJob = new IdleTimeoutScheduleJob(5, TimeUnit.SECONDS, tcpClient);
                tcpClient.schedule(scheduleJob);

                write(tcpClient);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (tcpClient != null) {
                    tcpClient.exit();
                }
            }
        }

        IoContext.close();
    }



    private static void write(TCPClient tcpClient) throws IOException {
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));

        do {
            // 读取一行输入, 没有末尾'\n'
            String str = input.readLine();
            if (str == null || Foo.COMMAND_EXIT.equalsIgnoreCase(str)) {
                break;
            }

            if (str.length() == 0) { continue; }

            // --f url
            if (str.startsWith("--f")) {
                String[] array = str.split(" ");
                if (array.length >= 2) {
                    String filePath = array[1];
                    File file = new File(filePath);
                    if (file.exists() && file.isFile()) {
//                        List<FileSendPacket> filePackets = FileDividePackets.FilePackets(filePath);
//                        singleClientSend(tcpClient, filePackets);
//                        concurrentSendFile(filePackets);
                        sendStreamfile(tcpClient, file);
                        continue;
                    }
                }
            }

            if(str.equals("pause")){
               pauseFileClients();
               continue;
            }

            if(str.equals("resume")){
                resumeFileClients();
                continue;
            }

            // 发送字符串
            tcpClient.send(str);
        } while (true);
    }



    private static void singleClientSend(TCPClient tcpClient, List<FileSendPacket> filePackets){
        for(int i = 0; i < filePackets.size(); i++)
            tcpClient.send(filePackets.get(i));
    }



    //用文件传输模拟语音直流
    private static void sendStreamfile(TCPClient tcpClient, File file){
        StreamDirectSendPacket sdsp = null;
        try {
            sdsp = new StreamDirectSendPacket(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        String filename = file.getName();
        byte[] headerInfo = filename.getBytes();
        sdsp.setHeaderInfo(headerInfo);
        tcpClient.send(sdsp);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sdsp.setfinished(true);
    }



    private static void concurrentSendFile(List<FileSendPacket> fileSendPackets) throws IOException {
        int i = 0;

        ConnectorCloseChain clsChain = new ConnectorCloseChain() {
            @Override
            protected synchronized boolean consume(ConnectorHandler handler, Connector connector) {
                FileClients.remove(((TCPClient)handler).getClientId().toString());
                return true;
            }
        };

        if(FileClients.size() < fileSendPackets.size()) {
            for(i = 0; i< fileSendPackets.size(); i++ ) {
                TCPClient newClient = produce(clsChain);

                 ScheduleJob scheduleJob = new IdleTimeoutScheduleJob2(2, TimeUnit.SECONDS, newClient);
                 newClient.schedule(scheduleJob);

                FileClients.put(newClient.getClientId().toString(), newClient);
                newClient.send(fileSendPackets.get(i));
            }
        } else {
            Set<Map.Entry<String, TCPClient>> entries = FileClients.entrySet();
            for (Map.Entry<String, TCPClient> entry : entries) {
                FileSendPacket packet = fileSendPackets.get(i++);
                TCPClient client = entry.getValue();
                client.send(packet);
            }
        }
    }


    private static void pauseFileClients(){
        if(FileClients.size() > 0){
            Set<Map.Entry<String, TCPClient>> fileClients = FileClients.entrySet();
            fileClients.forEach((client) -> client.getValue().pause());
        }
    }


    private static void resumeFileClients(){
        if(FileClients.size() > 0){
            Set<Map.Entry<String, TCPClient>> fileClients = FileClients.entrySet();
            fileClients.forEach((client) -> client.getValue().resume());
        }
    }



    private static TCPClient produce(ConnectorCloseChain closeChain) throws IOException {
        TCPClient client = TCPClient.startWith(info, cachePath, true);
        client.getCloseChain().appendLast(closeChain);

        return client;
    }


}
