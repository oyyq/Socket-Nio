package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.ReceivePacket;
import java.nio.channels.WritableByteChannel;


public class StreamDirectReceivePacket extends ReceivePacket<WritableByteChannel, WritableByteChannel> {
    private WritableByteChannel outputChannel;

    public StreamDirectReceivePacket(WritableByteChannel outputChannel, long length) {
        super(length);
        this.outputChannel = outputChannel;
    }

    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }

    @Override
    protected WritableByteChannel createChannel() {
        return outputChannel;
    }

    @Override
    protected WritableByteChannel buildEntity(WritableByteChannel channel) {
        return outputChannel;
    }


}
