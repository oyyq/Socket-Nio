package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 公共的数据封装
 * 提供了类型以及基本的长度的定义
 */
public abstract class Packet<Channel extends Closeable> implements Closeable {

    // 最大包大小，5个字节满载组成的Long类型
    public static final long MAX_PACKET_SIZE = (((0xFFL) << 32) | ((0xFFL) << 24) | ((0xFFL) << 16) | ((0xFFL) << 8) | ((0xFFL)));

    // BYTES 类型
    public static final byte TYPE_MEMORY_BYTES = 1;
    // String 类型
    public static final byte TYPE_MEMORY_STRING = 2;
    // 文件 类型
    public static final byte TYPE_STREAM_FILE = 3;
    // 长链接流 类型
    public static final byte TYPE_STREAM_DIRECT = 4;

    protected long length;
    private Channel channel;
    protected byte[] headerInfo;
    public long length() { return length; }
    public void setLength(long length) { this.length = length; }


    /**
     * 对外的获取当前实例的流操作
     * @return {@link java.io.InputStream} or {@link java.io.OutputStream}
     */
    public final Channel open() {
        if (channel == null) {
            channel = createChannel();
        }
        return channel;
    }


    /**
     * 对外的关闭资源操作，如果流处于打开状态应当进行关闭
     * @throws IOException IO异常
     */
    @Override
    public final void close() throws IOException {
        if (channel != null) {
            closeChannel(channel);
            channel = null;
        }
    }


    /**
     * 类型，直接通过方法得到:
     * {@link #TYPE_MEMORY_BYTES}
     * {@link #TYPE_MEMORY_STRING}
     * {@link #TYPE_STREAM_FILE}
     * {@link #TYPE_STREAM_DIRECT}
     *
     * @return 类型
     */
    public abstract byte type();


    /**
     * 创建流操作，应当将当前需要传输的数据转化为流
     * @return {@link java.io.InputStream} or {@link java.io.OutputStream}
     */
    protected abstract Channel createChannel();


    /**
     * 关闭流，当前方法会调用流的关闭操作
     * @param channel 待关闭的流
     * @throws IOException IO异常
     */
    protected void closeChannel(Channel channel) throws IOException {
        channel.close();
    }


    /**
     * 头部额外信息，用于携带额外的校验信息等
     * @return byte 数组，最大255长度
     */
    public byte[] headerInfo() {
        return headerInfo;
    }

    public void setHeaderInfo(byte[] headerInfo){
        this.headerInfo = headerInfo;
    }


}

