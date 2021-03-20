package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.utils.CloseUtils;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * 分包, 分帧调度器
 */
public class AsyncSendDispatcher extends AbsIoArgsProcessor implements SendDispatcher, AsyncPacketReader.PacketProvider {

    private final Sender sender;
    //是否正在发送 ?
    private final AtomicBoolean isSending = new AtomicBoolean();
    //是否关闭SendDispatcher?
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    //是否暂停发送 ?
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    //Connector是否弃用当前SendDispatcher ?
    private volatile boolean isStop = false;

    private final AsyncPacketReader reader = new AsyncPacketReader(this);
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();
    private final Object queueLock = new Object();

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        sender.setSendListener(this);
    }


    /**
     * 发送Packet
     * AsyncSendDispatcher: 包队列
     * AsyncPacketReader: 帧队列
     * @param packet 数据
     */
    @Override
    public void send(SendPacket packet) {
        if(isClosed.get() || isStop) return;
        synchronized (queueLock) { queue.offer(packet); }
        if(isPaused.get()) return;

        reader.appendFrames();
        requestSend();
    }


    /**
     * 请求网络进行数据发送
     */
    private void requestSend() {
        synchronized (isSending) {
            if (isSending.get() || isPaused.get() || isClosed.get() ) {
                return;
            }

            // 有数据帧或者心跳帧
            if (reader.hasMoreFrames()) {
                try {
                    boolean isSucceed = sender.postSendAsync();
                    if (isSucceed) {
                        isSending.set(true);
                    } else {
                        throw new IOException("注册失败");
                    }
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                    closeAndNotify();
                }
            }
        }
    }



    /**
     * reader从queue中提取一份Packet
     * @return 如果队列有可用于发送的数据则返回该Packet
     */
    @Override
    public SendPacket takePacket() {
        if(isClosed.get())  return null;
        SendPacket packet;
        synchronized (queueLock) { packet = queue.poll(); }

        if (packet != null && packet.isCanceled()) {
            return takePacket();
        }

        return packet;    // packet == null时, 此时 包队列已经没有Packet了.
    }



    @Override
    public boolean hasPackets() {
        synchronized (queueLock) {
            return queue.size() > 0;
        }
    }


    /**
     * 发送心跳帧，将心跳帧放到帧发送队列进行发送
     * 当发送暂停时,
     */
    @Override
    public void sendHeartbeat() {
        if (reader.hasMoreFrames() || isPaused.get() || isClosed.get() || isStop) { return; }

        if (reader.requestSendHeartbeatFrame()) {
            requestSend();
        }
    }


    /**
     * 暂停数据帧和心跳帧的发送
     */
    @Override
    public void pause() {
        if(isPaused.compareAndSet(false, true)) {
            reader.pause();
        }
    }


    /**
     * 恢复播放
     */
    @Override
    public void resume() {
        if(isPaused.compareAndSet(true, false)){
            reader.resume();
            if(isClosed.get() || isStop) return;
            requestSend();
        }
    }


    /**
     * 取消Packet操作
     * 如果还在队列中，代表Packet未进行发送，则直接标志取消，并返回即可
     * 如果未在队列中，则让reader尝试扫描当前发送序列，查询是否当前Packet正在发送
     * 如果是则进行取消相关操作
     * @param packetId 在发送队列中的Id,
     * @param packet
     */
    @Override
    public void cancel(short packetId, SendPacket packet) {
        boolean ret;
        //Packet在包队列中等待, 没有发送任何数据
        synchronized (queueLock) {
            ret = queue.remove(packet);
        }
        if (ret) {
            packet.cancel();
            return;
        }

        //ret == false, Packet已经被sub-thread从包队列中拿出, packetId是Packet的标志, 取消这个包
        reader.cancel(packetId);
    }



    /**
     * 尽快停止发送现存数据
     */
    @Override
    public void stop() {
        isStop = true;                      //禁止外层调用AsyncSendDispatcher#send(Packet)将新包放入包队列
        reader.stopDesPck = true;           //禁止将包队列中的包拿出, 拆帧
        synchronized (queueLock){ while (queue.size() > 0) { queue.poll(); } }          //将没拆成帧的包取出丢弃

        reader.cancelAllSending();                    //将所有将要拆帧, 部分拆成帧, 完全拆成帧 的包取消
        while (reader.getNodeSize() > 0 || channelSending){
            //阻塞到reader的所有有效帧都发出 => nodeSize == 0;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }



    /**
     * 完成Packet发送
     * @param isSucceed 是否成功
     */
    @Override
    public void completedPacket(Packet packet, boolean isSucceed) {
        CloseUtils.close(packet);
    }


    /**
     * 请求网络发送异常时触发,
     * 进行关闭当前SocketChannel的写通道
     */
    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    /**
     * 关闭操作，关闭自己同时需要关闭reader
     */
    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            isPaused.set(false);
            isStop = false;
            // reader关闭
            reader.close();
            // 清理队列
            queue.clear();
            // 设置当前发送状态
            synchronized (isSending) {
                isSending.set(false);
            }
        }
    }


    /**
     * 网络发送就绪回调，当前已进入发送就绪状态，等待填充数据进行发送
     * 此时从reader中填充数据，并进行后续网络发送
     * @return NULL，可能填充异常，或者想要取消本次发送
     */
    @Override
    public IoArgs provideIoArgs() { return isClosed.get() ? null : reader.fillData(); }


    /**
     * IoArgs承载完数据,在消费过程中出现异常.
     * @param args IoArgs
     * @param e    异常信息
     */
    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        synchronized (isSending) {
            isSending.set(false);
        }

        if(!isPaused.get() && !isClosed.get() && !isStop) {
            requestSend();
        }
    }




    private volatile boolean channelSending = false;

    @Override
    public void notifyStartSend() { channelSending = true; }

    @Override
    public void notifyDataFullSent() { channelSending = false; }


}
