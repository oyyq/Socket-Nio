package net.qiujuer.lesson.sample.client;

import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorCloseChain;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.impl.IoSelectorProvider;
import net.qiujuer.library.clink.impl.SchedulerImpl;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientTest {
    private static final int CLIENT_SIZE = 2000;
    private static final int SEND_THREAD_DELAY = 100;
    private static final int THREAD_SIZE = 1;
    private static volatile boolean done = false;


    public static void main(String[] args) throws IOException {
        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);
        if (info == null) {
            return;
        }

        File cachePath = Foo.getCacheDir("client/test");
        IoContext.setup()
                .ioProvider(new IoSelectorProvider())
                .scheduler(new SchedulerImpl(1))
                .start();


        int size = 0;
        final List<TCPClient> tcpClients = new ArrayList<>(CLIENT_SIZE);


        final ConnectorCloseChain closeChain = new ConnectorCloseChain() {
            @Override
            protected boolean consume(ConnectorHandler handler, Connector connector) {
                //noinspection SuspiciousMethodCalls
                tcpClients.remove(handler);
                //if (tcpClients.size() == 0) {
                //  CloseUtils.close(System.in);
                //}
                return false;
            }
        };


        for (int i = 0; i < CLIENT_SIZE; i++) {
            try {
                TCPClient tcpClient = TCPClient.startWith(info, cachePath, false);
                if (tcpClient == null) {
                    throw new NullPointerException();
                }

                tcpClient.getCloseChain().appendLast(closeChain);
                tcpClients.add(tcpClient);
                System.out.println("连接成功：" + (++size));

            } catch (IOException | NullPointerException e) {
                System.out.println("连接异常");
                break;
            }
        }

        System.in.read();
        TCPClient[] copyClients = tcpClients.toArray(new TCPClient[0]);

        Runnable runnable = () -> {
            while (!done) {
                for (TCPClient client : copyClients) {
                    if (!client.isClosed()) client.send("Hello~~Hello~~Hello~~Hello~~Hello~~Hello~~Hello~~");
                }
                try {
                    Thread.sleep(SEND_THREAD_DELAY);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        };


        Thread thread = new Thread(runnable);
        thread.start();


    }

}

