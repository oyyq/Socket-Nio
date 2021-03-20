package net.qiujuer.library.clink.box;

import java.io.ByteArrayOutputStream;
import java.nio.channels.WritableByteChannel;


/**
 * 字符串接收包
 */
public class StringReceivePacket extends AbsByteArrayReceivePacket<String> {

    public StringReceivePacket(long len) {
        super(len);
    }

    @Override
    protected String buildEntity(WritableByteChannel channel) {
        return byteOutStream.toString();
    }

    @Override
    public byte type() {
        return TYPE_MEMORY_STRING;
    }
}