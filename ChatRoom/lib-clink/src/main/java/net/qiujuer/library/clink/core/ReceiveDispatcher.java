package net.qiujuer.library.clink.core;

import java.io.Closeable;

/**
 * 接收的数据调度封装
 * 把一份或者多分IoArgs组合成一份Packet
 */
public interface ReceiveDispatcher extends Closeable {
    void start();

    void stop();

    interface ReceivePacketCallback {
        /**
         * 接收到新的包的开始
         * @param type
         * @param length
         * @param headerInfo
         * @return
         */
        ReceivePacket<?, ?> onArrivedNewPacket(byte type, long length, byte[] headerInfo);

        /**
         * 接收到包的结束
         * @param packet
         */
        void onReceivePacketCompleted(ReceivePacket packet);

        /**
         * 接收到心跳包
         */
        void onReceivedHeartbeat();
    }
}
