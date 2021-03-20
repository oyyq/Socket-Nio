package net.qiujuer.lesson.sample.foo.handle;


import net.qiujuer.lesson.sample.foo.Foo;
import net.qiujuer.library.clink.box.StringReceivePacket;
import net.qiujuer.library.clink.core.Connector;
import net.qiujuer.library.clink.core.IoContext;
import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;


public class ConnectorHandler extends Connector {
    protected final File cachePath;
    private final String clientInfo;
    private final ConnectorCloseChain closeChain = new DefaultPrintConnectorCloseChain();
    private final ConnectorStringPacketChain stringPacketChain = new DefaultNonConnectorStringPacketChain();

    public ConnectorHandler(SocketChannel socketChannel, File cachePath) throws IOException {
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        this.cachePath = cachePath;
        setup(socketChannel);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    @Override
    public boolean isPaused() {
        return isPaused.get() ;
    }

    public void exit() { CloseUtils.close(this); }

    /**
     * 内部监测到链接断开的回调
     * @param channel SocketChannel
     */
    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        closeChain.handle(this, this);
    }


    /**
     * 包接收开始
     * @param headerInfo
     * @return
     */
    @Override
    protected File createNewReceiveFile(byte[] headerInfo) {
        String filename = new String(headerInfo, 10, headerInfo.length-10);
        return Foo.createRandomTemp(cachePath, filename);
    }

    @Override
    protected WritableByteChannel createNewReceiveDirectOutputStream(long length, byte[] headerInfo) {
        return null;
    }


    /**
     * 包接收完毕
     * @param packet
     */
    @Override
    protected void onReceivedPacket(ReceivePacket packet) {
        super.onReceivedPacket(packet);
        switch (packet.type()) {
            case Packet.TYPE_MEMORY_STRING: {
                deliveryStringPacket((StringReceivePacket) packet);
                break;
            }
            default: {
                System.out.println("New Packet:" + packet.type() + "-" + packet.length());
            }
        }
    }


    /**
     * 避免阻塞当前的数据读取线程调度，则单独交给另外一个调度线程进行数据调度
     * @param packet StringReceivePacket
     */
    private void deliveryStringPacket(StringReceivePacket packet) {
        IoContext.get().getScheduler().delivery(() -> stringPacketChain.handle(this, packet));
    }

    /**
     * 获取当前链接的消息处理责任链 链头
     *
     * @return ConnectorStringPacketChain
     */
    public ConnectorStringPacketChain getStringPacketChain() {
        return stringPacketChain;
    }

    /**
     * 获取当前链接的关闭链接处理责任链 链头
     *
     * @return ConnectorCloseChain
     */
    public ConnectorCloseChain getCloseChain() {
        return closeChain;
    }
}
