package com.yigit.buds2blescanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Passive analyzer for the Buds2 RFCOMM stream observed in captures.
 * It does not transmit anything. It treats FD...DD as a candidate frame
 * envelope and reports byte-level statistics without assuming command meaning.
 */
public final class BudsProtocolAnalyzer {
    public static final class Frame {
        public final byte[] bytes;
        public final int length;
        public final int start;
        public final int end;
        public final int[] variableOffsets;
        public final String checksumNote;

        Frame(byte[] bytes, int[] variableOffsets, String checksumNote) {
            this.bytes = bytes;
            this.length = bytes.length;
            this.start = bytes.length == 0 ? -1 : bytes[0] & 0xFF;
            this.end = bytes.length == 0 ? -1 : bytes[bytes.length - 1] & 0xFF;
            this.variableOffsets = variableOffsets;
            this.checksumNote = checksumNote;
        }

        public String summary() {
            StringBuilder s = new StringBuilder();
            s.append("FRAME len=").append(length)
             .append(" start=").append(String.format(Locale.US, "%02X", start))
             .append(" end=").append(String.format(Locale.US, "%02X", end));
            if (length > 2) {
                s.append(" header=");
                int n = Math.min(8, length);
                for (int i = 0; i < n; i++) s.append(String.format(Locale.US, "%02X ", bytes[i] & 0xFF));
            }
            s.append(" checksum=").append(checksumNote);
            return s.toString();
        }
    }

    private final ArrayList<Byte> pending = new ArrayList<>();
    private final ArrayList<byte[]> history = new ArrayList<>();
    private int frameNumber;

    /** Feed an arbitrary RFCOMM read chunk. A single read may contain partial or multiple frames. */
    public synchronized List<Frame> feed(byte[] chunk) {
        List<Frame> out = new ArrayList<>();
        if (chunk == null) return out;
        for (byte b : chunk) pending.add(b);
        while (true) {
            int start = indexOf(0xFD);
            if (start < 0) { pending.clear(); break; }
            if (start > 0) pending.subList(0, start).clear();
            int end = indexOfAfter(0xDD, 1);
            if (end < 0) break;
            byte[] frame = new byte[end + 1];
            for (int i = 0; i <= end; i++) frame[i] = pending.get(i);
            pending.subList(0, end + 1).clear();
            history.add(frame);
            Frame f = new Frame(frame, new int[0], checksumAnalysis(frame));
            frameNumber++;
            out.add(f);
        }
        return out;
    }

    public synchronized int frameCount() { return frameNumber; }

    private int indexOf(int value) {
        for (int i = 0; i < pending.size(); i++) if ((pending.get(i) & 0xFF) == value) return i;
        return -1;
    }

    private int indexOfAfter(int value, int from) {
        for (int i = from; i < pending.size(); i++) if ((pending.get(i) & 0xFF) == value) return i;
        return -1;
    }

    private String checksumAnalysis(byte[] a) {
        if (a.length < 4) return "too_short";
        int last2 = ((a[a.length - 2] & 0xFF) << 8) | (a[a.length - 1] & 0xFF);
        int sum8 = 0;
        int xor8 = 0;
        for (int i = 0; i < a.length - 2; i++) {
            sum8 = (sum8 + (a[i] & 0xFF)) & 0xFF;
            xor8 ^= (a[i] & 0xFF);
        }
        int crc16 = crc16Ccitt(a, 0, a.length - 2);
        if (last2 == crc16 || swap16(last2) == crc16) return "CRC16_CCITT_MATCH";
        if ((last2 & 0xFF) == sum8 || (last2 & 0xFF) == xor8) return "8BIT_CHECKSUM_CANDIDATE";
        return "unknown_last2=0x" + String.format(Locale.US, "%04X", last2);
    }

    private static int swap16(int x) { return ((x & 0xFF) << 8) | ((x >>> 8) & 0xFF); }

    private static int crc16Ccitt(byte[] a, int off, int len) {
        int crc = 0xFFFF;
        for (int i = off; i < off + len; i++) {
            crc ^= (a[i] & 0xFF) << 8;
            for (int j = 0; j < 8; j++) crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
        }
        return crc & 0xFFFF;
    }
}
