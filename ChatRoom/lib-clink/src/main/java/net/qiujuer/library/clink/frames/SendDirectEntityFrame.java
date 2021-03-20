package net.qiujuer.library.clink.frames;

import net.qiujuer.library.clink.box.StreamDirectSendPacket;
import net.qiujuer.library.clink.core.Frame;
import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.SendPacket;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;


/**
 * 流帧: 每个时刻从ReadableByteChannel中读取一段字节available
 * 帧体长度 == available.
 */
public class SendDirectEntityFrame extends AbsSendPacketFrame {
    private final ReadableByteChannel channel;

    SendDirectEntityFrame(short identifier,
                          int available,
                          ReadableByteChannel channel,
                          SendPacket packet) {
        super(Math.min(available, Frame.MAX_CAPACITY),
                Frame.TYPE_PACKET_ENTITY,
                Frame.FLAG_NONE,
                identifier,
                packet);
        this.channel = channel;
    }

    @Override
    protected int consumeBody(IoArgs args) throws IOException {
        if (packet == null) {
            // 已终止当前帧，则填充假数据
            return args.fillEmpty(bodyRemaining);
        }
        return args.readFrom(channel);
    }

    @Override
    protected Frame buildNextFrame() {
        if(((StreamDirectSendPacket)packet).isfinished()){        //语音直流包已经结束(通话挂断), 发送一帧取消帧
            System.out.println("挂断");
            return new CancelSendFrame(getBodyIdentifier());
        }else {
            int available = ((StreamDirectSendPacket)packet).available();
            if (available >= 0) {
                //available == 0, 当前没有语音数据, 发送一帧无数据的帧
                return new SendDirectEntityFrame(getBodyIdentifier(), available, channel, packet);
            }  else {
                //inputstream异常, 发出取消帧.
                return new CancelSendFrame(getBodyIdentifier());
            }
        }
        //Receiver端接收到CancelReceiveFrame就会关闭包
    }


    public static Frame buildEntityFrame(SendPacket packet, short identifier){
        if(((StreamDirectSendPacket)packet).isfinished()){        //语音直流包已经结束(通话挂断), 发送一帧取消帧
            System.out.println("挂断");
            return new CancelSendFrame(identifier);
        }else {
            int available = ((StreamDirectSendPacket)packet).available();
            if (available >= 0) {
                //available == 0, 当前没有语音数据, 发送一帧无数据的帧
                return new SendDirectEntityFrame(identifier, available, ((StreamDirectSendPacket) packet).open(), packet);
            }  else {
                //inputstream异常, 发出取消帧.
                return new CancelSendFrame(identifier);
            }
        }
    }


    /**
     * 一直返回false, 只有CancelSendFrame才是
     * @return false
     */
    @Override
    public boolean isEndingFrame() {
        return false;
    }

}
