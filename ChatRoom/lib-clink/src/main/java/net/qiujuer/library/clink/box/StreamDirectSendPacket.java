package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.Packet;
import net.qiujuer.library.clink.core.SendPacket;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class StreamDirectSendPacket extends SendPacket<ReadableByteChannel> {
    private InputStream inputStream;
    private volatile boolean isfinished = false;

    public StreamDirectSendPacket(InputStream inputStream) {
        // 用以读取数据进行输出的输入流
        this.inputStream = inputStream;
        // 长度不固定，所以为最大值
        this.length = MAX_PACKET_SIZE;
    }


    @Override
    public byte type() {
        return Packet.TYPE_STREAM_DIRECT;
    }

    @Override
    protected ReadableByteChannel createChannel() {
        return Channels.newChannel(inputStream);
    }

    /**
     * 获取当前可用数据大小
     * PS: 对于流的类型有限制，文件流一般可用正常获取，
     * 对于正在填充的流不一定有效，或得不到准确值
     * <p>
     * 我们利用该方法不断得到直流传输的可发送数据量，从而不断生成Frame
     * <p>
     * 缺陷：对于流的数据量大于Int有效值范围外则得不到准确值
     * <p>
     * 一般情况下，发送数据包时不使用该方法，而使用总长度进行运算
     * 对于直流传输则需要使用该方法，因为对于直流而言没有最大长度
     *
     * @return 默认返回stream的可用数据量：0代表无数据可输出了
     */
    public int available() {
        try {
            int available = inputStream.available();
            if (available < 0) {
                return 0;
            }
            return available;
        } catch (IOException e) {
            return 0;
        }
    }


    public void setfinished(boolean finished) {
        isfinished = finished;
    }


    public boolean isfinished(){
        return isfinished;
    }

}
