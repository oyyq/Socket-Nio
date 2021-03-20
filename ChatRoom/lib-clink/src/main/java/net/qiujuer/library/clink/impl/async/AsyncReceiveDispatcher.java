package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 接收调度
 * receiver与外界的"中间层"
 * 调度掌管包, 帧协议的AsyncPacketWriter
 * extends AbsIoArgsProcessor (this.receiver.setReceiveListener(this);) 监听SocketChannelAdapter接收IoArgs
 * implements AsyncPacketWriter.PacketProvider (new AsyncPacketWriter(this);) AsyncPacketWriter通知AsyncReceiveDispatcher准备包
 * implements ReceiveDispatcher (Connector#receiveDispatcher;) 由Connector调度SocketChannelAdapter在Selector上的注册 / 解注册
 */
public class AsyncReceiveDispatcher extends AbsIoArgsProcessor implements ReceiveDispatcher, AsyncPacketWriter.PacketProvider {

    private final Receiver receiver;
    private final ReceivePacketCallback callback;
    private AsyncPacketWriter writer = new AsyncPacketWriter(this);
    //是否关闭ReceiveDispatcher?
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    //是否弃用当前ReceiveDispatcher ?
    private volatile boolean isStop = false;


    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(this);
        this.callback = callback;
    }


    /**
     * 开始进入接收方法
     */
    @Override
    public void start() {
        registerReceive();
    }


    /**
     * 停止接收数据,已经接收到的数据要清空
     */
    @Override
    public void stop() {
        isStop = true;
        //阻塞直到一波到达数据接收完毕
        while (receivingDataStart && !isClosed.get()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        writer.clearData();        //清理接收到的所有packet. 关闭包的通道
    }



    /**
     * 关闭操作，关闭相关流, writer清理数据
     */
    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            //writer.clearData();
            writer.close();
            isStop = false;
        }
    }


    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    /**
     * 注册接收数据
     */
    private void registerReceive() {
        if(isClosed.get() || isStop) return;
        try {
            receiver.postReceiveAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }


    /**
     * 网络接收就绪，此时可以读取数据，需要返回一个容器用于容纳数据
     * @return 用以容纳数据的IoArgs
     */
    @Override
    public IoArgs provideIoArgs() {
        if(isClosed.get() || isStop) return null;
        IoArgs ioArgs = writer.takeIoArgs();
        // 一份新的IoArgs需要调用一次开始写入数据的操作
        ioArgs.startWriting();
        return ioArgs;
    }



    /**
     * 接收数据成功
     * @param args IoArgs
     */
    @Override
    public void onConsumeCompleted(IoArgs args) {
        if (isClosed.get() || isStop) { return; }

        // 消费数据之前标示args数据填充完成, 改变为可读取数据状态
        args.finishWriting();
        // 有数据则重复消费
        do {
            writer.consumeIoArgs(args);
        } while (args.remained() && !isClosed.get());

    }



    /**
     * 构建Packet操作，根据类型、长度构建一份用于接收数据的Packet
     */
    @Override
    public ReceivePacket takePacket(byte type, long length, byte[] headerInfo) {
        if(isClosed.get() || isStop) return null;
        return callback.onArrivedNewPacket(type, length, headerInfo);
    }


    /**
     * 当Packet接收数据完成或终止时回调
     *
     * @param packet    接收包
     * @param isSucceed 是否成功接收完成
     */
    @Override
    public void completedPacket(ReceivePacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
        if(isSucceed) {callback.onReceivePacketCompleted(packet);}
    }

    /**
     * 接收到心跳包
     */
    @Override
    public void onReceivedHeartbeat() {
        callback.onReceivedHeartbeat();
    }



    //通知Connector, SocketChannel当前没有可读数据
    private volatile boolean receivingDataStart = false;

    @Override
    public void notifyReceiveStart() { receivingDataStart = true;  }

    @Override
    public void notifyReceiveStop() { receivingDataStart  = false;  }



}
