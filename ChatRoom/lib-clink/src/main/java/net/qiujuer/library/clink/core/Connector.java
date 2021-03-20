package net.qiujuer.library.clink.core;

import net.qiujuer.library.clink.box.*;
import net.qiujuer.library.clink.impl.SocketChannelAdapter;
import net.qiujuer.library.clink.impl.async.AsyncReceiveDispatcher;
import net.qiujuer.library.clink.impl.async.AsyncSendDispatcher;
import net.qiujuer.library.clink.impl.bridge.BridgeSocketDispatcher;
import net.qiujuer.library.clink.utils.CloseUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {
    protected UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;
    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;

    private SendDispatcher cacheSendDispatcher;             //缓存服务器的接收调度器, 转换桥接使用
    private ReceiveDispatcher cacheReceiveDispatcher;        //..

    private final List<ScheduleJob> scheduleJobs = new ArrayList<>(4);
    protected AtomicBoolean isPaused = new AtomicBoolean(false);
    protected AtomicBoolean isClosed = new AtomicBoolean(false);

    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;
        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(this.channel, context.getIoProvider(), this);

        this.sender = adapter;
        this.receiver = adapter;

        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver, receivePacketCallback);

        // 注册接收
        receiveDispatcher.start();
    }


    public void send(String msg) {
        SendPacket packet = new StringSendPacket(msg);
        sendDispatcher.send(packet);
    }

    public void send(SendPacket packet) {
        sendDispatcher.send(packet);
    }


    public void pause(){
        if(isPaused.compareAndSet(false,true)) {
            sendDispatcher.pause();
        }
    }

    public void resume() {
        if(isPaused.compareAndSet(true,false)) {
            sendDispatcher.resume();
        }
    }


    /**
     * 调度一份任务
     * @param job 任务
     */
    public void schedule(ScheduleJob job) {
        synchronized (scheduleJobs) {
            if (scheduleJobs.contains(job)) {
                return;
            }
            IoContext context = IoContext.get();
            Scheduler scheduler = context.getScheduler();
            job.schedule(scheduler);
            scheduleJobs.add(job);
        }
    }


    /**
     * 发射一份空闲超时事件
     */
    public void fireIdleTimeoutEvent() {
        if(!(sendDispatcher instanceof BridgeSocketDispatcher)){ sendDispatcher.sendHeartbeat(); }
    }

    /**
     * 发射一份异常事件，子类需要关注
     *
     * @param throwable 异常
     */
    public void fireExceptionCaught(Throwable throwable) { }

    /**
     * 获取最后的活跃时间点
     *
     * @return 发送、接收的最后活跃时间
     */
    public long getLastActiveTime() {
        return Math.max(sender.getLastWriteTime(), receiver.getLastReadTime());
    }


    public void stopSend(){
        sendDispatcher.stop();
    }

    @Override
    public void close() throws IOException {
        if(isClosed.compareAndSet(false,true)) {
            synchronized (scheduleJobs) {
                for (ScheduleJob scheduleJob : scheduleJobs) {
                    scheduleJob.unSchedule();
                }
                scheduleJobs.clear();
            }

            receiveDispatcher.close();
            sendDispatcher.close();
            sender.close();
            receiver.close();
            channel.close();
        }
    }



    @Override
    public void onChannelClosed(SocketChannel channel) {
        CloseUtils.close(this);
    }

    public boolean isClosed(){
        return isClosed.get();
    }

    protected void onReceivedPacket(ReceivePacket packet) {
        // System.out.println(key.toString() + ":[New Packet]-Type:" + packet.type() + ", Length:" + packet.length);
    }

    protected abstract File createNewReceiveFile(byte[] headerInfo);

    /**
     * 当接收包是直流数据包时，需要得到一个用以存储当前直流数据的输出流，
     * 所有接收到的数据都将通过输出流输出
     *
     * @param length     长度
     * @param headerInfo 额外信息
     * @return 输出流
     */
    protected abstract WritableByteChannel createNewReceiveDirectOutputStream(long length, byte[] headerInfo);


    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        @Override
        public ReceivePacket<?, ?> onArrivedNewPacket(byte type, long length, byte[] headerInfo) {
            switch (type) {
                case Packet.TYPE_MEMORY_BYTES:
                    return new BytesReceivePacket(length);
                case Packet.TYPE_MEMORY_STRING:
                    return new StringReceivePacket(length);
                case Packet.TYPE_STREAM_FILE: {
                    FileReceivePacket fileReceivePacket = new FileReceivePacket(length, createNewReceiveFile(headerInfo));
                    fileReceivePacket.setHeaderInfo(headerInfo);
                    return fileReceivePacket;
                }
                case Packet.TYPE_STREAM_DIRECT:
                    return new StreamDirectReceivePacket(createNewReceiveDirectOutputStream(length, headerInfo), length);
                default:
                    throw new UnsupportedOperationException("Unsupported packet type:" + type);
            }
        }

        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {
            onReceivedPacket(packet);
        }

        @Override
        public void onReceivedHeartbeat() {
            System.out.println(key.toString() + ":[Heartbeat]");
        }
    };


    public UUID getKey() {
        return key;
    }

    public abstract boolean isPaused();


    /**
     * 转换桥接模式
     */
    public void changeToBridge(BridgeSocketDispatcher dispatcher) {
        if (receiveDispatcher instanceof BridgeSocketDispatcher) { return; }

        cacheReceiveDispatcher = receiveDispatcher;
        cacheReceiveDispatcher.stop();               // 解注册, 将已经到达SocketChannel的数据接收完, 并丢弃缓存的接收到的数据
        receiveDispatcher = dispatcher;             // 构建新的接收者调度器

        dispatcher.bindReceiver(receiver);

        dispatcher.start();
    }


    public void bindToBridge(BridgeSocketDispatcher dispatcher) {
        if (sendDispatcher instanceof BridgeSocketDispatcher) { return; }

        cacheSendDispatcher = sendDispatcher;
        // 将当前正在发的帧发完, 并发出一个取消帧告诉接收方将包丢弃.
        cacheSendDispatcher.stop();
        sendDispatcher = dispatcher;

        dispatcher.bindSender(sender);      //开始注册发送了.
    }



    //恢复到初始的服务器端receiveDsipatcher, sendDispatcher.
    public void rebind(){
        //TODO
    }


}
