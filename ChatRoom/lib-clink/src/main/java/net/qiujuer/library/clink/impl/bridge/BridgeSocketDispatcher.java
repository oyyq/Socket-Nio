package net.qiujuer.library.clink.impl.bridge;

import net.qiujuer.library.clink.core.*;
import net.qiujuer.library.clink.utils.CloseUtils;
import net.qiujuer.library.clink.utils.plugin.CircularByteBuffer;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * BridgeSocketDispatcher 不再需要一个Writer掌握帧, 包的接收协议
 *
 */
public class BridgeSocketDispatcher extends AbsBridgeSocketDispatcher {

    private final CircularByteBuffer mBuffer;
    //根据缓冲区得到读取, 写入通道
    private final ReadableByteChannel readableByteChannel;
    private final WritableByteChannel writableByteChannel;

    //Receiver接收数据, 将数据转入mBuffer的IoArgs
    private final IoArgs receiveIoArgs = new IoArgs(256, false);
    //一端的数据接收方
    private volatile Receiver receiver;

    //将数据从mBuffer中读出, 转给Sender的IoArgs, isNeedConsumeRemaining = true => 必须将IoArgs中的数据全部写出
    private final IoArgs sendIoArgs = new IoArgs();
    //另一端的数据接收方
    private volatile Sender sender;
    private final AtomicBoolean isSending = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    public BridgeSocketDispatcher(){
        mBuffer = new CircularByteBuffer(1024);
        readableByteChannel = Channels.newChannel(mBuffer.getInputStream());
        writableByteChannel = Channels.newChannel(mBuffer.getOutputStream());
    }

    private void registerReceive() {
        try {
            this.receiver.postReceiveAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    IoArgs.IoArgsEventProcessor receiveProcessor = new AbsIoArgsProcessor() {

        //只承担数据的转运, 不关心数据的分包, 分帧
        @Override
        public IoArgs provideIoArgs() {
            receiveIoArgs.resetLimit();
            receiveIoArgs.startWriting();
            return receiveIoArgs;
        }


        @Override
        public void onConsumeCompleted(IoArgs args) {
            if (isClosed.get()) { return; }
            // 消费数据之前标示args数据填充完成, 改变为可读取数据状态
            args.finishWriting();
            int datalen= 0;
            try {
                //将args的所有数据写入圆形缓冲区, mBuffer是阻塞写
                datalen =  args.writeTo(writableByteChannel);
            } catch (IOException e) {
                e.printStackTrace();
            }

            //确定mBuffer是有数据的, 将数据请求发送出去
            if(datalen > 0){ registerSend(); }
        }


    };



    IoArgs.IoArgsEventProcessor sendProcessor = new AbsIoArgsProcessor() {
        @Override
        public IoArgs provideIoArgs() {
            try {
                int available = mBuffer.getAvailable();
                IoArgs args = BridgeSocketDispatcher.this.sendIoArgs;
                if (available > 0) {
                    args.limit(available);
                    args.startWriting();
                    args.readFrom(readableByteChannel);
                    args.finishWriting();
                    return args;
                }
            } catch (IOException e){
                e.printStackTrace();
            }

            return null;
        }

        @Override
        public void onConsumeFailed(IoArgs args, Exception e) {
            synchronized (isSending) {
                isSending.set(false);
            }
            if( !isClosed.get()) {
                registerSend();
            }
        }

    };



    //绑定一个发送者
    public void bindSender(Sender sender) {
        // 清理老的发送者回调
        final Sender oldSender = this.sender;
        if (oldSender != null) {
            oldSender.setSendListener(null);
        }

        synchronized (isSending) {
            isSending.set(false);
        }

        this.sender = sender;
        if(sender!= null) {
            this.sender.setSendListener(sendProcessor);
            registerSend();
        }
    }


    public void registerSend(){
        synchronized (isSending) {
            if (isSending.get() || isClosed.get() || sender == null) {
                return;
            }

            if(mBuffer.getAvailable() > 0) {
                try {
                    boolean isSucceed = sender.postSendAsync();
                    if (isSucceed) {
                        isSending.set(true);
                    } else {
                        throw new IOException("注册失败");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    closeAndNotify();
                }
            }
        }

    }


    public void bindReceiver(Receiver receiver){
        // 清理老的发送者回调
        final Receiver oldReceiver = this.receiver;
        if (oldReceiver != null) {
            oldReceiver.setReceiveListener(null);
        }

        this.receiver = receiver;
        if(receiver!= null) {
            this.receiver.setReceiveListener(receiveProcessor);
        }
    }


    @Override
    public void start() {
        mBuffer.clear();            //开始接收前先清理buffer
        registerReceive();          //注册接收
    }


    //停止接收, 发送
    @Override
    public void stop() {
        //todo
    }

    @Override
    public void close() throws IOException {
        //todo
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }




}
