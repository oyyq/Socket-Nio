package net.qiujuer.library.clink.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;

/**
 * 接收包的定义
 */
public abstract class ReceivePacket<Channel extends WritableByteChannel, Entity> extends Packet<Channel> {

    // 定义当前接收包最终的实体
    private Entity entity;

    public ReceivePacket(long len) {
        this.length = len;
    }

    /**
     * 得到最终接收到的数据实体
     *
     * @return 数据实体
     */
    public Entity entity() {
        return entity;
    }

    /**
     * 根据接收到的流转化为对应的实体
     *
     * @param stream {@link OutputStream}
     * @return 实体
     */
    protected abstract Entity buildEntity(Channel channel);

    /**
     * 先关闭流，随后将流的内容转化为对应的实体
     * @param stream 待关闭的流
     * @throws IOException IO异常
     */
    @Override
    protected final void closeChannel(Channel channel) throws IOException {
        super.closeChannel(channel);
        entity = buildEntity(channel);
    }

}
