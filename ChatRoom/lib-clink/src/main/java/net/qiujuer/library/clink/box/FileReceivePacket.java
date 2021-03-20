package net.qiujuer.library.clink.box;

import net.qiujuer.library.clink.core.ReceivePacket;

import java.io.*;
import java.nio.channels.FileChannel;

/**
 * 文件接收包
 */
public class FileReceivePacket extends ReceivePacket<FileChannel, File> {
    private File file;
    private RandomAccessFile ramFile;
    private long offset;

    public FileReceivePacket(long len, File file) {
        super(len);
        this.file = file;
    }


    @Override
    public byte type() {
        return TYPE_STREAM_FILE;
    }

    @Override
    protected FileChannel createChannel() {
        try {
            ramFile = new RandomAccessFile(file, "rw");
            if(headerInfo != null) {
                offset = ((((long) headerInfo[0]) & 0xFFL) << 32)
                        | ((((long) headerInfo[1]) & 0xFFL) << 24)
                        | ((((long) headerInfo[2]) & 0xFFL) << 16)
                        | ((((long) headerInfo[3]) & 0xFFL) << 8)
                        | (((long) headerInfo[4]) & 0xFFL);
                ramFile.seek(offset);
            }
            return ramFile.getChannel();
        }  catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected File buildEntity(FileChannel channel) {
        return file;
    }

}
