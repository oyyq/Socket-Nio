package net.qiujuer.library.clink.impl;

import net.qiujuer.library.clink.core.IoArgs;
import net.qiujuer.library.clink.core.IoProvider;
import net.qiujuer.library.clink.core.Receiver;
import net.qiujuer.library.clink.core.Sender;
import net.qiujuer.library.clink.utils.CloseUtils;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;



/**
 * 注册Selector监听, 解Selector注册监听, 通知sendDispatcher, receiveDispatcher 提供发送到网络的IoArgs / 从网络接收的IoArgs
 */
public class SocketChannelAdapter implements Sender, Receiver, Cloneable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangedListener listener;

    private IoArgs.IoArgsEventProcessor receiveIoEventProcessor;
    private IoArgs.IoArgsEventProcessor sendIoEventProcessor;

    // 最后活跃时间点
    private volatile long lastReadTime = System.currentTimeMillis();
    private volatile long lastWriteTime = System.currentTimeMillis();

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangedListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;
        //连接建立之后调整为non-blocking
        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventProcessor processor) {
        receiveIoEventProcessor = processor;
    }

    @Override
    public long getLastReadTime() {
        return lastReadTime;
    }

    @Override
    public void setSendListener(IoArgs.IoArgsEventProcessor processor) {
        sendIoEventProcessor = processor;
    }

    @Override
    public long getLastWriteTime() {
        return lastWriteTime;
    }


    @Override
    public boolean postReceiveAsync() throws IOException {
        if (isClosed.get()) { throw new IOException("Current channel is closed!"); }

        inputCallback.checkAttachNull();
        return ioProvider.registerInput(channel, inputCallback);          //注册读事件
    }

    @Override
    public void postUnregisterReceive() throws IOException {
        if (isClosed.get()) { throw new IOException("Current channel is closed!"); }

        ioProvider.unRegisterInput(channel);
    }


    @Override
    public boolean postSendAsync() throws IOException {
        if (isClosed.get()) { throw new IOException("Current channel is closed!"); }

        outputCallback.checkAttachNull();
        return ioProvider.registerOutput(channel, outputCallback);    //注册写事件
    }


    @Override
    public void postUnregisterSend() throws IOException{
        if (isClosed.get()) { throw new IOException("Current channel is closed!"); }
        //在解注册前确保SocketChannel不是正在发送中的状态
        ioProvider.unRegisterOutput(channel);
    }


    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            // 解除注册回调
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
            CloseUtils.close(channel);
            // 回调当前Channel已关闭
            listener.onChannelClosed(channel);
        }
    }



    //读事件就绪后, 开始接收, 一直接收到无法继续从SocketChannel取出数据.
    private final IoProvider.HandleProviderCallback inputCallback = new IoProvider.HandleProviderCallback() {

        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) return;
            IoArgs.IoArgsEventProcessor processor = receiveIoEventProcessor;
            processor.notifyReceiveStart();        //通知外层有SocketChannel有新数据到达

            //将上次没读满的IoArgs循环读满
            if (args != null) {
                try {
                    lastReadTime = System.currentTimeMillis();
                    args.readFrom(channel);
                    if (args.remained() && args.isNeedConsumeRemaining()) {
                        attach = args;                                          // 附加当前未消费完成的args
                        if(!ioProvider.registerInput(channel, this)){     //再次注册数据接收
                            attach = null;
                            throw new IOException("注册接收失败!");
                        }
                        return;
                    }else {
                        processor.onConsumeCompleted(args);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    CloseUtils.close(SocketChannelAdapter.this);
                    return;
                }
            }
            attach = null;


            if (isClosed.get()) return;
            while ((args = processor.provideIoArgs()) != null) {    //当前帧是否读完? true, args.limit = 6;  false, args.limit计算;  condition == true, 内部判断跳出
                try {
                    lastReadTime = System.currentTimeMillis();
                    args.readFrom(channel);
                    if (args.remained() && args.isNeedConsumeRemaining()) {
                        attach = args;                                              // 附加当前未消费完成的args
                        if(!ioProvider.registerInput(channel, this)){       //再次注册数据接收
                            attach = null;
                            throw new IOException("注册接收失败!");
                        }
                        return;
                    }else {
                        processor.onConsumeCompleted(args);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    CloseUtils.close(SocketChannelAdapter.this);
                    return;
                }

               if (isClosed.get()) return;
            }

            processor.notifyReceiveStop();              //args == null, inputCallback#attach == null
        }

    };






    private final IoProvider.HandleProviderCallback outputCallback = new IoProvider.HandleProviderCallback() {

        @Override
        protected void onProviderIo(IoArgs args) {
            if (isClosed.get()) return;
            IoArgs.IoArgsEventProcessor processor = sendIoEventProcessor;
            processor.notifyStartSend();        //通知外层开始发送数据了

            //将上次没写完的IoArgs循环写完
            if (args != null) {
                try {
                    lastWriteTime = System.currentTimeMillis();
                    args.writeTo(channel);
                    if (args.remained() && args.isNeedConsumeRemaining()) {
                        attach = args;                                                   // 附加当前未消费完成的args
                        if(!ioProvider.registerOutput(channel, this)){          //再次注册数据发送
                            attach = null;
                            throw new IOException("注册发送失败!");
                        }
                        return;
                    }else {
                        processor.onConsumeCompleted(args);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    CloseUtils.close(SocketChannelAdapter.this);
                    return;
                }
            }
            attach = null;


            if(isClosed.get()) return;

            //帧队列当前有数据, 不断地拿新的IoArgs
            while ((args = processor.provideIoArgs()) != null) {
                try {
                    lastWriteTime = System.currentTimeMillis();                          // 刷新输出时间
                    args.writeTo(channel);
                    if (args.remained() && args.isNeedConsumeRemaining()) {
                        attach = args;                                                   // 附加当前未消费完成的args
                        if(!ioProvider.registerOutput(channel, this)){          //再次注册数据发送
                            attach = null;
                            throw new IOException("注册发送失败!");
                        }
                        return;
                    } else {
                        processor.onConsumeCompleted(args);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    CloseUtils.close(SocketChannelAdapter.this);
                    return;
                }

                if(isClosed.get()) return;
            }


            //args == null; 当前无法从帧队列拿出IoArgs, 回调给外层, 外层检查包队列;  帧队列有数据, 再次注册写;
            // 帧队列没数据, 等待下次外层requestSend()注册写
            processor.notifyDataFullSent();
            processor.onConsumeFailed(null, new IOException("等待发送数据."));

        }
    };


    public interface OnChannelStatusChangedListener {
        void onChannelClosed(SocketChannel channel);
    }

}
