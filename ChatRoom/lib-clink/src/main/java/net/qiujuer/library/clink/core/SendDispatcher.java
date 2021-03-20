package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * 发送数据的调度者
 * 缓存所有需要发送的数据，通过队列对数据进行发送
 * 并且在发送数据时，实现对数据的基本包装
 */
public interface SendDispatcher extends Closeable {
    /**
     * 发送一份数据
     *
     * @param packet 数据
     */
    void send(SendPacket packet);

    /**
     * 暂停帧的发送, 当前帧: 1. 没开始发送, 2. 已经发送了一些字节, 但没发送完毕.
     */
    void pause();

    /**
     * 恢复帧的发送,
     */
    void resume();

    /**
     * 发送心跳包
     */
    void sendHeartbeat();

    /**
     * 取消发送数据
     *
     * @param packetId 包标志
     */
    void cancel(short packetId, SendPacket packet);

    void stop();
}
