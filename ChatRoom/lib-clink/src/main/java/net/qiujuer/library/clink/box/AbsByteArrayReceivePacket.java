package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.ReceivePacket;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/**
 * 定义最基础的基于{@link ByteArrayOutputStream}的输出接收包
 *
 * @param <Entity> 对应的实体范性，需定义{@link ByteArrayOutputStream}流最终转化为什么数据实体
 */
public abstract class AbsByteArrayReceivePacket<Entity> extends ReceivePacket<WritableByteChannel, Entity> {
    protected ByteArrayOutputStream byteOutStream ;

    public AbsByteArrayReceivePacket(long len) {
        super(len);
        byteOutStream = new ByteArrayOutputStream((int)len);
    }

    /**
     * 创建流操作直接返回一个{@link ByteArrayOutputStream}流
     *
     * @return {@link ByteArrayOutputStream}
     */
    @Override
    protected final WritableByteChannel createChannel() {
        return Channels.newChannel(byteOutStream);
    }
}

