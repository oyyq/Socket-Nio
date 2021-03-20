package net.qiujuer.library.clink.impl.async;

import net.qiujuer.library.clink.box.StreamDirectSendPacket;
import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.core.ds.BytePriorityNode;
import net.qiujuer.library.clink.frames.*;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;



/**
 * Packet转换为帧序列，并进行读取发送的封装管理类
 */
public class AsyncPacketReader implements Closeable {
    private final PacketProvider provider;
    private volatile IoArgs args = new IoArgs();

    private volatile BytePriorityNode<Frame> node;            // Frame队列
    private volatile int nodeSize = 0;

    private AtomicBoolean isClosed = new AtomicBoolean(false);
    private AtomicBoolean isPaused= new AtomicBoolean(false);
    private AtomicBoolean isAppending = new AtomicBoolean(false);        //是否正在从包队列中拿出包, 拆解成帧, 添加到帧队列
    public volatile boolean stopDesPck = false;       //stop dessemble packets into frames  停止将新的包拆成帧放入帧队列, 停止放入心跳帧

    private HashMap<Integer, SendPacket> mySendPackets = new HashMap<>();
    private Object packetsLock = new Object();
    private static final int timesleep = 20;

    AsyncPacketReader(PacketProvider provider) { this.provider = provider; }

    /**
     * 请求从 {@link #provider}队列中拿一份Packet进行发送
     * sub-thread
     * @return 如果当前Reader中有可以用于网络发送的数据，则返回True
     */
     boolean hasMoreFrames() {
         if(isClosed.get()) return false;
         synchronized (this) { if( nodeSize >= 1 ) return true; }           //帧队列有数据
         synchronized (packetsLock){ if( mySendPackets.size() > 0 ) return true; }          //当前有子线程正在拆包成帧, 放入帧队列


        if (provider.hasPackets()) {
            appendFrames();
            return true;
        }

        //帧队列和包队列都没有数据 => 未来帧队列也不会有帧.
        return false;
    }



    /**
     * 请求发送一份心跳帧，若当前心跳帧已存在则不重新添加到队列
     * @return True 添加队列成功
     */
    boolean requestSendHeartbeatFrame() {
        if(stopDesPck || isClosed.get() || isPaused.get()) return false;
        synchronized (this) {
            for (BytePriorityNode<Frame> x = node; x != null; x = x.next) {
                Frame frame = x.item;
                if (frame.getBodyType() == Frame.TYPE_COMMAND_HEARTBEAT) {
                    return false;
                }
            }
        }

        if(stopDesPck || isClosed.get() || isPaused.get()) return false;
        appendNewFrame(new HeartbeatSendFrame());
        return true;
    }



    /**
     * 填充数据到IoArgs中
     * @return 如果当前有可用于发送的帧，则填充数据并返回，如果填充失败可返回null
     */
    IoArgs fillData() {
        if(isClosed.get()) return null;
        AbsSendFrame currentFrame;
        currentFrame = (AbsSendFrame) getCurrentFrame();
        if(currentFrame == null || isPaused.get()) return null;

        //currentFrame: 有效帧(包没取消; 包取消,但还要发的帧; 取消帧,心跳帧)
        try {
            if (currentFrame.handle(args)) {
                if(currentFrame.isEndingFrame()) {     //currentFrame (SendEntityFrame or CancelFrame) 是否是包的最后一帧了 ?
                    SendPacket packet;                  //将AsyncSendDispatcher#mySendPackets的对应包移除
                    synchronized (packetsLock){ packet = mySendPackets.remove((int)currentFrame.getBodyIdentifier()); }
                    provider.completedPacket(packet, !(currentFrame instanceof CancelSendFrame));
                }
                popCurrentFrame();                  // 将currentFrame从链头弹出
            }
            return args;
        } catch (IOException e) {
            e.printStackTrace();    //如: packet的channel已经关闭, args从packet通道读取数据, 抛出异常
        }
        return null;
    }



    /**
     * 每发一个Packet,
     * 就将该Packet所有帧全部加入AsyncPacketReader的帧队列
     * main-thread
     */
    public void appendFrames(){
        if(isClosed.get() || stopDesPck) return;
        if( isAppending.get() || !provider.hasPackets()) return;

        synchronized (packetsLock) {
            if (mySendPackets.size() > 0) return;

            if (isAppending.compareAndSet(false, true)) {
                SendPacket packet;  short packetId;

                do {
                    if(isClosed.get() || stopDesPck) break;
                    packetId = generateIdentifier();
                    if (packetId < 0) break;

                    packet = provider.takePacket();
                    if (packet == null || packet.isCanceled()) break;

                    mySendPackets.put((int)packetId, packet);
                    IoContext.get().dessemblePool.execute(new DessemblePacket(packet, packetId, packet instanceof StreamDirectSendPacket));
                } while (mySendPackets.size() < 255);

                isAppending.compareAndSet(true, false);
            }
        }
    }



    /**
     * 将非直流传输包, 也就是已知包长度的包, 在子线程中拆解成帧, 加入到帧队列
     */
    class DessemblePacket implements Runnable {
        private SendPacket packet;      //待拆包
        private short identifier;       //包标志
        private boolean isStream;       //是否是直流包

        public DessemblePacket(SendPacket packet, short identifier, boolean isStream){
            this.packet = packet;
            this.identifier = identifier;
            this.isStream = isStream;
        }


        @Override
        public void run() {

            if(isClosed.get()) return;
            if(packet.isCanceled()){
                synchronized (packetsLock){ if(mySendPackets.values().contains(packet)) mySendPackets.remove((int)identifier); }
                return;
            }

            byte[] headerInfo = packet.headerInfo();
            AbsSendFrame frame = new SendHeaderFrame(identifier, packet, headerInfo == null ? 0: headerInfo.length);
            do{
                appendNewFrame(frame);
                if(isClosed.get() || packet.isCanceled()) break;
                //直流每20ms装一帧.
                if(isStream){ try { Thread.sleep(timesleep); } catch (InterruptedException e) {} }
                frame = (AbsSendFrame) frame.nextFrame();
            }while (frame!= null);

            if(packet.isCanceled() && frame instanceof SendHeaderFrame && !frame.isAddedToNode){
                //头帧没加入队列
                synchronized(packetsLock) { if(mySendPackets.values().contains(packet)) mySendPackets.remove((int)identifier); }
            }
        }
    }



    public void pause(){ isPaused.compareAndSet(false, true); }
    public void resume(){ isPaused.compareAndSet(true, false); }



    /*
     * main-thread
     * @param packet 待取消的packet
     * 1. Packet的Frame已经全部添加入帧队列了,
     * 2. Packet的Frame一部分加入帧队列了(packet#isCanceled, volatile boolean, 保证子线程可见性).
     * 3. Packet的Frame没有任何一个在帧队列
     */
    synchronized void cancel(short packedId) {
        SendPacket packet;
        synchronized (packetsLock) {
            if (!mySendPackets.keySet().contains((int)packedId)) return;
            packet = mySendPackets.get((int)packedId);
        }
        if(packet.isCanceled()) return;         //不能被重复取消
        packet.cancel();


        boolean cfa = false;                //cancelframe added ?
        final int snapShot = nodeSize;        //当前帧长度快照
        int offset = 0;


        if (snapShot == 0) return;              //帧队列没有帧.不需接下来的遍历
        BytePriorityNode<Frame> x = node;
        do {
            Frame frame = x.item;
            if (frame instanceof AbsSendPacketFrame) {          //CancelFrame & HeartbeatFrame是不取消的
                AbsSendPacketFrame packetFrame = (AbsSendPacketFrame) frame;

                if (packetFrame.getPacket() == packet) {
                    boolean removable = packetFrame.abort();
                    if (removable) {
                        packetFrame.cancel();                         // 标记包取消
                    }

                    if (!cfa) {
                        if (!(frame instanceof SendHeaderFrame && removable)) {     //头帧的数据已经全部发送或者发送了一部分
                            if(!(packetFrame.isEndingFrame() && !removable)) {       //实体尾帧还没发送任何数据
                                // 添加终止帧, 通知接收方
                                cfa = true;
                                CancelSendFrame cancelSendFrame = new CancelSendFrame(packetFrame.getBodyIdentifier());
                                appendNewFrame(cancelSendFrame);
                            } else {
                                //最后一帧实体帧已经发送了一部分数据
                                cfa = true;
                            }
                        } else {
                            //头帧的数据还没发送
                            cfa = true;
                            synchronized (packetsLock){ if(mySendPackets.values().contains(packet)) { mySendPackets.remove((int) packetFrame.getBodyIdentifier()); } }
                            provider.completedPacket(packet, false);
                        }
                    }
                }
            }

            offset++;
            if (offset >= snapShot) break;
            x = x.next;
        } while (x != null);

    }



    /**
     * 关闭当前Reader，关闭时应关闭所有的Frame对应的Packet
     */
    @Override
    public synchronized void close() {
        if(isClosed.compareAndSet(false,true)) {
            while (node != null) {
                Frame frame = node.item;
                if (frame instanceof AbsSendPacketFrame) {
                    SendPacket packet = mySendPackets.remove((int)frame.getBodyIdentifier());
                    provider.completedPacket(packet, false);
                }
                node = node.next;
            }

            nodeSize = 0;
            node = null;
            isPaused.set(false);
            isAppending.set(false);
            stopDesPck = false;
        }
    }



    /**
     * 添加新的帧
     * 优化点: 不用优先级添加, 记住末尾帧end, end.next = ..
     * @param frame 新帧
     */
    private synchronized void appendNewFrame(Frame frame) {
        if(isClosed.get() || frame.packetCanceled())  return;

        BytePriorityNode<Frame> newNode = new BytePriorityNode<>(frame);
        if (node != null) {
            node.appendWithPriority(newNode);
        } else {
            node = newNode;
        }
        nodeSize++;
        ((AbsSendFrame)frame).isAddedToNode = true;
    }



    /**
     * 获取当前从链表头开始, 没被取消的帧
     *
     * @return Frame
     */
    public synchronized Frame getCurrentFrame() {
        if(isClosed.get()) return null;
        if (node != null && ((AbsSendFrame)node.item).isCanceled()) {
            BytePriorityNode<Frame> tmp = node;
            node = node.next;
            tmp.next = null;
            nodeSize--;
            return getCurrentFrame();
        }

        return node == null? null: node.item;
    }


    /**
     * 弹出链表头的帧
     */
    private synchronized void popCurrentFrame() {
        BytePriorityNode<Frame> tmp = node;
        node = node.next;
        tmp.next = null;
        nodeSize--;
        if(isClosed.get()) return;
        if (this.node == null) {
            hasMoreFrames();
        }
    }


    public int getNodeSize() {
        return nodeSize;
    }

    public void cancelAllSending(){
        Integer[] keys;
        synchronized (packetsLock){ keys = mySendPackets.keySet().toArray(new Integer[0]); }
        for(int key : keys) cancel((short) key);
    }



    /**
     * 删除某帧对应的链表节点
     * @param removeNode 待删除的节点
     * @param before     当前删除节点的前一个节点，用于构建新的链表结构
     */
    private synchronized void removeFrame(BytePriorityNode<Frame> removeNode, BytePriorityNode<Frame> before) {
        if (before == null) {
            // A B C
            // B C
            node = removeNode.next;
        } else {
            // A B C
            // A C
            before.next = removeNode.next;
        }
        nodeSize--;
        if(isClosed.get()) return;
        if (node == null) {
            hasMoreFrames();
        }
    }



    /**
     * 构建一份Packet惟一标志
     * @return 标志为：1～255
     */
    private short generateIdentifier() {
        for (int i = 1; i <= 255; i++) {
            if (!(mySendPackets.keySet().contains(i))) {
                return (short) i;
            }
        }

        return -1;
    }


    /**
     * Packet提供者
     */
    interface PacketProvider {
        /**
         * 拿Packet操作
         * @return 如果队列有可以发送的Packet则返回不为null
         */
        SendPacket takePacket();

        boolean hasPackets();

        /**
         * 结束一份Packet
         * @param packet    发送完成的包
         * @param isSucceed 是否成功发送完成
         */
        void completedPacket(Packet packet, boolean isSucceed);
    }


}
