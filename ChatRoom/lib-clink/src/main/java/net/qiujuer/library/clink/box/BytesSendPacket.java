package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.SendPacket;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * 纯Byte数组发送包
 */
public class BytesSendPacket extends SendPacket<ReadableByteChannel> {
    private final byte[] bytes;

    public BytesSendPacket(byte[] bytes) {
        this.bytes = bytes;
        this.length = bytes.length;
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_BYTES;
    }


    @Override
    protected ReadableByteChannel createChannel() {
        return Channels.newChannel(new ByteArrayInputStream(bytes));
    }

}