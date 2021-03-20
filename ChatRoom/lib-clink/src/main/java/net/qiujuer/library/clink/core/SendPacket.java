package net.qiujuer.library.clink.core;

import java.nio.channels.ReadableByteChannel;

/**
 * 发送的包定义
 */

public abstract class SendPacket<T extends ReadableByteChannel> extends Packet<T> {
    private volatile boolean isCanceled;

    public boolean isCanceled() {
        return isCanceled;
    }

    /**
     * 设置取消发送标记
     */
    public void cancel() {
        isCanceled = true;
    }

}
