package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {
    void setSendListener(IoArgs.IoArgsEventProcessor processor);

    boolean postSendAsync() throws IOException;

    void postUnregisterSend() throws IOException;

    /**
     * 获取输出数据的时间
     *
     * @return 毫秒
     */
    long getLastWriteTime();
}
