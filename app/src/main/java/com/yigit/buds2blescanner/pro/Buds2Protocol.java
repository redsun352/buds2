package com.yigit.buds2blescanner.pro;

import java.util.Arrays;

/** Conservative SM-R177 RFCOMM frame utilities. */
public final class Buds2Protocol {
    public static final int START = 0xFD, END = 0xDD;
    private Buds2Protocol() {}

    public static ProtocolFrame parse(byte[] frame) {
        if (frame == null || frame.length < 7 || (frame[0] & 0xFF) != START || (frame[frame.length - 1] & 0xFF) != END)
            return new ProtocolFrame(frame, -1, false);
        return new ProtocolFrame(Arrays.copyOf(frame, frame.length), messageId(frame), verifyCrcLe(frame));
    }

    public static int messageId(byte[] frame) { return frame != null && frame.length > 3 && (frame[0] & 0xFF) == START ? frame[3] & 0xFF : -1; }
    public static int header(byte[] frame) { return frame == null || frame.length < 3 ? -1 : ((frame[1]&255)<<8)|(frame[2]&255); }
    public static byte[] payload(byte[] frame) { return frame == null || frame.length < 7 ? new byte[0] : Arrays.copyOfRange(frame, 4, frame.length - 3); }

    /** CRC16-CCITT, verified against the supplied SM-R177 capture. */
    public static int crc16Ccitt(byte[] data, int off, int len) {
        int crc = 0xFFFF;
        for (int i=off;i<off+len;i++) { crc ^= (data[i]&255)<<8; for(int b=0;b<8;b++) crc=(crc&0x8000)!=0?((crc<<1)^0x1021)&0xFFFF:(crc<<1)&0xFFFF; }
        return crc & 0xFFFF;
    }

    /** Capture uses the two checksum bytes immediately before DD in little-endian order. */
    public static boolean verifyCrcLe(byte[] frame) {
        if (frame == null || frame.length < 7 || (frame[0]&255)!=START || (frame[frame.length-1]&255)!=END) return false;
        int p=frame.length-3;
        int stored=(frame[p]&255)|((frame[p+1]&255)<<8);
        return stored==crc16Ccitt(frame,1,p-1);
    }
    public static int storedCrcLe(byte[] frame) { if(frame==null||frame.length<7)return -1; int p=frame.length-3; return (frame[p]&255)|((frame[p+1]&255)<<8); }
    public static String hex(byte[] data) { if(data==null)return ""; StringBuilder s=new StringBuilder(data.length*3); for(byte b:data)s.append(String.format("%02X ",b&255)); return s.toString().trim(); }
}
