package net.qiujuer.lesson.sample.foo;

import net.qiujuer.library.clink.box.FileSendPacket;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class FileDividePackets {
    public static final int FilePacketsNumber = 4;

    public static List<FileSendPacket> FilePackets(String filePath){
        List<FileSendPacket> filePackets = new ArrayList<>();
        File file = new File(filePath);
        String fileName =filePath.substring(filePath.lastIndexOf("/")+1);

        if(file.exists()){
            long fileLength = file.length();
            long packetLength = fileLength / FilePacketsNumber;

            for(int i = 0; i < FilePacketsNumber; i++){
                long offset = i*packetLength;
                FileSendPacket packet = new FileSendPacket(file);
                packet.setLength(packetLength);
                byte[] headerInfo = generateHeader(offset,offset+packetLength-1, fileName);
                packet.setHeaderInfo(headerInfo);

                if(i == FilePacketsNumber-1) {
                    packet.setLength(fileLength - i * packetLength);
                    headerInfo = generateHeader(offset,fileLength-1, fileName);
                    packet.setHeaderInfo(headerInfo);
                }
                filePackets.add(packet);
            }
            return filePackets;
        }
        return null;
    }


    //start, end : inclusive
    private static byte[] generateHeader(long start, long end, String fileName) {
        byte[] header = new byte[10+fileName.length()];
        header[0] = (byte) (start >> 32);
        header[1] = (byte) (start >> 24);
        header[2] = (byte) (start >> 16);
        header[3] = (byte) (start >> 8);
        header[4] = (byte) (start);

        header[5] = (byte) (end >> 32);
        header[6] = (byte) (end >> 24);
        header[7] = (byte) (end >> 16);
        header[8] = (byte) (end >> 8);
        header[9] = (byte) (end);
        System.arraycopy(fileName.getBytes(), 0, header, 10, fileName.length());
        return header;
    }



}
