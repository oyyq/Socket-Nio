package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.SendPacket;

import java.io.*;
import java.nio.channels.FileChannel;

/**
 * 文件发送包
 */
public class FileSendPacket extends SendPacket<FileChannel> {
    private final File file;
    private RandomAccessFile ramFile;
    private long start;
    private long end;

    public FileSendPacket(File file) {
        this.file = file;
    }


    @Override
    public byte type() {
        return TYPE_STREAM_FILE;
    }

    /**
     * 使用File构建文件读取流，用以读取本地的文件数据进行发送
     *
     * @return 文件读取流
     */
    @Override
    protected FileChannel createChannel() {
        start = ((((long) headerInfo[0]) & 0xFFL) << 32)
                | ((((long) headerInfo[1]) & 0xFFL) << 24)
                | ((((long) headerInfo[2]) & 0xFFL) << 16)
                | ((((long) headerInfo[3]) & 0xFFL) << 8)
                | (((long) headerInfo[4]) & 0xFFL);

        end = ((((long) headerInfo[5]) & 0xFFL) << 32)
                | ((((long) headerInfo[6]) & 0xFFL) << 24)
                | ((((long) headerInfo[7]) & 0xFFL) << 16)
                | ((((long) headerInfo[8]) & 0xFFL) << 8)
                | (((long) headerInfo[9]) & 0xFFL);

        try {
            if(end-start +1 != length) throw new IOException("wrong file packet length! ");
            ramFile = new RandomAccessFile(this.file,"r");
            ramFile.seek(start);
            return ramFile.getChannel();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
