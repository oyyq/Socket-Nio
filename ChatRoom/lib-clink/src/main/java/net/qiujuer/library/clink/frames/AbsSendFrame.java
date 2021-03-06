package net.qiujuer.library.clink.frames;

import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;

import java.io.IOException;

public abstract class AbsSendFrame extends Frame {
    // 帧头可读写区域大小
    volatile byte headerRemaining = Frame.FRAME_HEADER_LENGTH;
    // 帧体可读写区域大小
    volatile int bodyRemaining;
    //该帧是否取消?(也就是不会发送该帧, 不意味着这是取消帧)
    protected volatile boolean isCanceled = false;
    public volatile boolean isAddedToNode = false;

    AbsSendFrame(int length, byte type, byte flag, short identifier) {
        super(length, type, flag, identifier);
        bodyRemaining = length;
    }

    public AbsSendFrame(byte[] header) {
        super(header);
        bodyRemaining = getBodyLength();
    }

    public void cancel(){
        isCanceled = true;
    }

    public boolean isCanceled(){
        return isCanceled;
    }


    @Override
    public synchronized boolean handle(IoArgs args) throws IOException {
        try {
            args.limit(headerRemaining + bodyRemaining);
            args.startWriting();

            if (headerRemaining > 0 && args.remained()) {
                headerRemaining -= consumeHeader(args);
            }

            if (headerRemaining == 0 && args.remained() && bodyRemaining > 0) {
                bodyRemaining -= consumeBody(args);
            }

            return headerRemaining == 0 && bodyRemaining == 0;
        } finally {
            args.finishWriting();
        }
    }


    @Override
    public int getConsumableLength() {
        return headerRemaining + bodyRemaining;
    }

    private byte consumeHeader(IoArgs args) {
        int count = headerRemaining;
        int offset = header.length - count;
        return (byte) args.readFrom(header, offset, count);
    }

    protected abstract int consumeBody(IoArgs args) throws IOException;

    /**
     * 是否已经处于发送数据中，如果已经发送了部分数据则返回True
     * 只要头部数据已经开始消费，则肯定已经处于发送数据中
     * @return True，已发送部分数据
     */
    public synchronized boolean isSending() {
        return headerRemaining < Frame.FRAME_HEADER_LENGTH;
    }

    /**
     * 该帧是否是最后一帧了? 发完该帧要将Packet从AsyncPacketReader#myPackets中移除了
     * 若某包在发送过程中被取消, 并且添加了CancelFrame到帧队列, 那么在CancelFrame发完后才能在myPackets中移除
     * 某包已经在发最后一帧实体帧了,取消后不加入CancelFrame了, 将尾帧发完.
     * @return true
     */
    public abstract boolean isEndingFrame();

}
