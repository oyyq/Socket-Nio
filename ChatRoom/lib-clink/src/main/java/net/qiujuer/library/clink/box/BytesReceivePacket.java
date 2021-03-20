package net.qiujuer.library.clink.box;

import java.io.ByteArrayOutputStream;
import java.nio.channels.WritableByteChannel;

/**
 * 纯Byte数组接收包
 */

public class BytesReceivePacket extends AbsByteArrayReceivePacket<byte[]> {

    public BytesReceivePacket(long len) {
        super(len);
    }

    @Override
    protected byte[] buildEntity(WritableByteChannel channel) {
        return byteOutStream.toByteArray();
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_BYTES;
    }

}