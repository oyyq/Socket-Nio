package net.qiujuer.library.clink.core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {
    void setReceiveListener(IoArgs.IoArgsEventProcessor processor);

    boolean postReceiveAsync() throws IOException;

    void postUnregisterReceive() throws IOException;

    /**
     * 获取读取数据的时间
     *
     * @return 毫秒
     */
    long getLastReadTime();
}
