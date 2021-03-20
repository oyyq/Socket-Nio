package net.qiujuer.lesson.sample.client;


import net.qiujuer.lesson.sample.client.bean.ServerInfo;
import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.lesson.sample.foo.handle.ConnectorHandler;
import net.qiujuer.lesson.sample.foo.handle.ConnectorStringPacketChain;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.UUID;

public class TCPClient extends ConnectorHandler {
    public static UUID ClientId;

    public TCPClient(SocketChannel socketChannel, File cachePath, boolean printReceiveString) throws IOException {
        super(socketChannel, cachePath);
        if (printReceiveString) {
            getStringPacketChain().appendLast(new PrintStringPacketChain());
        }
    }

    public static class PrintStringPacketChain extends ConnectorStringPacketChain {
        @Override
        protected boolean consume(ConnectorHandler handler, StringReceivePacket stringReceivePacket) {
            String str = stringReceivePacket.entity();
            System.out.println(str);
            return true;
        }
    }

    static TCPClient startWith(ServerInfo info, File cachePath) throws IOException {
        return startWith(info, cachePath, true);
    }


    static TCPClient startWith(ServerInfo info, File cachePath, boolean printReceiveString) throws IOException {
        ClientId = UUID.randomUUID();
        SocketChannel socketChannel = SocketChannel.open();

        // 连接本地，端口2000；超时时间3000ms
        socketChannel.connect(new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress().toString());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress().toString());

        try {
            return new TCPClient(socketChannel, cachePath, printReceiveString);
        } catch (Exception e) {
            System.out.println("连接异常");
            CloseUtils.close(socketChannel);
        }

        return null;
    }


    public UUID getClientId() {
        return ClientId;
    }


    /**
     * 只测试文件直流包
     * @param length     长度
     * @param headerInfo 额外信息
     * @return
     */
    @Override
    protected WritableByteChannel createNewReceiveDirectOutputStream(long length, byte[] headerInfo) {
        //根据headerInfo创建一个直流包的输出
        String filename = new String(headerInfo);
        File clientfile = Foo.createRandomTemp(cachePath, filename);

        try {
            return Channels.newChannel(new FileOutputStream(clientfile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }


}
