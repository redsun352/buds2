package com.yigit.buds2blescanner.pro;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Conservative Buds2 frame parser. Unknown messages are preserved rather than guessed. */
public final class Buds2Protocol {
    public static final int START = 0xFD, END = 0xDD;
    private Buds2Protocol() {}

    public static ProtocolFrame parse(byte[] frame) {
        if (frame == null || frame.length < 7 || (frame[0] & 255) != START || (frame[frame.length-1] & 255) != END)
            return new ProtocolFrame(frame, -1, false);
        int id = frame.length > 3 ? frame[3] & 255 : -1;
        int crcPos = frame.length - 3;
        int expected = ((frame[crcPos] & 255) << 8) | (frame[crcPos+1] & 255);
        int actual = crc16(frame, 1, crcPos);
        return new ProtocolFrame(Arrays.copyOf(frame, frame.length), id, expected == actual);
    }

    public static int crc16(byte[] data, int off, int len) {
        int crc = 0xFFFF;
        for (int i=off; i<off+len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int b=0; b<8; b++) crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
        }
        return crc & 0xFFFF;
    }

    public static String hex(byte[] data) {
        if (data == null) return "";
        StringBuilder s = new StringBuilder(data.length * 3);
        for (byte b : data) s.append(String.format("%02X ", b & 255));
        return s.toString().trim();
    }
}
