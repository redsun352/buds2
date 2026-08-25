package com.yigit.buds2blescanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Passive analyzer for the Buds2 RFCOMM stream observed in captures.
 * It does not transmit anything. It treats FD...DD as a candidate envelope,
 * keeps a streaming buffer, detects repeated message shapes, compares frames
 * byte-by-byte, and tests several common checksum candidates.
 */
public final class BudsProtocolAnalyzer {
    public static final class Frame {
        public final byte[] bytes;
        public final int length;
        public final int start;
        public final int end;
        public final int[] variableOffsets;
        public final String checksumNote;
        public final String shapeKey;
        public final String diffNote;

        Frame(byte[] bytes, int[] variableOffsets, String checksumNote, String shapeKey, String diffNote) {
            this.bytes = bytes;
            this.length = bytes.length;
            this.start = bytes.length == 0 ? -1 : bytes[0] & 0xFF;
            this.end = bytes.length == 0 ? -1 : bytes[bytes.length - 1] & 0xFF;
            this.variableOffsets = variableOffsets;
            this.checksumNote = checksumNote;
            this.shapeKey = shapeKey;
            this.diffNote = diffNote;
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
            s.append(" shape=").append(shapeKey)
             .append(" checksum=").append(checksumNote);
            if (diffNote != null && !diffNote.isEmpty()) s.append(" diff=").append(diffNote);
            return s.toString();
        }
    }

    private final ArrayList<Byte> pending = new ArrayList<>();
    private final ArrayList<Frame> history = new ArrayList<>();
    private int frameNumber;

    public synchronized void reset() {
        pending.clear();
        history.clear();
        frameNumber = 0;
    }

    /** Feed an arbitrary RFCOMM read chunk. A single read may contain partial or multiple frames. */
    public synchronized List<Frame> feed(byte[] chunk) {
        List<Frame> out = new ArrayList<>();
        if (chunk == null || chunk.length == 0) return out;
        for (byte b : chunk) pending.add(b);
        while (true) {
            int start = indexOf(0xFD);
            if (start < 0) { pending.clear(); break; }
            if (start > 0) pending.subList(0, start).clear();

            // Do not assume the first FD after the start is a new frame. The
            // candidate envelope is terminated by DD; any embedded FD is kept.
            int end = indexOfAfter(0xDD, 1);
            if (end < 0) break;

            byte[] frame = new byte[end + 1];
            for (int i = 0; i <= end; i++) frame[i] = pending.get(i);
            pending.subList(0, end + 1).clear();

            String shape = shapeKey(frame);
            String diff = diffAgainstPreviousSameShape(frame, shape);
            int[] vars = variableOffsetsAgainstPreviousSameShape(frame, shape);
            Frame f = new Frame(frame, vars, checksumAnalysis(frame), shape, diff);
            history.add(f);
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

    /** Shape ignores bytes that commonly vary while retaining framing and length. */
    private String shapeKey(byte[] a) {
        if (a.length == 0) return "EMPTY";
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.US, "%02X", a[0] & 0xFF));
        if (a.length > 1) s.append('-').append(String.format(Locale.US, "%02X", a[1] & 0xFF));
        if (a.length > 3) s.append('-').append(String.format(Locale.US, "%02X", a[3] & 0xFF));
        s.append("-L").append(a.length);
        return s.toString();
    }

    private Frame previousSameShape(String shape) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Frame f = history.get(i);
            if (f.shapeKey.equals(shape)) return f;
        }
        return null;
    }

    private int[] variableOffsetsAgainstPreviousSameShape(byte[] a, String shape) {
        Frame p = previousSameShape(shape);
        if (p == null || p.bytes.length != a.length) return new int[0];
        ArrayList<Integer> v = new ArrayList<>();
        for (int i = 0; i < a.length; i++) if (a[i] != p.bytes[i]) v.add(i);
        int[] out = new int[v.size()];
        for (int i = 0; i < v.size(); i++) out[i] = v.get(i);
        return out;
    }

    private String diffAgainstPreviousSameShape(byte[] a, String shape) {
        Frame p = previousSameShape(shape);
        if (p == null || p.bytes.length != a.length) return "none";
        int count = 0;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (a[i] != p.bytes[i]) {
                if (count == 0) s.append("offsets=");
                else s.append(',');
                s.append(i).append(':')
                 .append(String.format(Locale.US, "%02X", p.bytes[i] & 0xFF))
                 .append('>')
                 .append(String.format(Locale.US, "%02X", a[i] & 0xFF));
                count++;
                if (count >= 16) { s.append(",..."); break; }
            }
        }
        return count == 0 ? "identical" : s.toString();
    }

    private String checksumAnalysis(byte[] a) {
        if (a.length < 4) return "too_short";
        int last2be = ((a[a.length - 2] & 0xFF) << 8) | (a[a.length - 1] & 0xFF);
        int last2le = ((a[a.length - 1] & 0xFF) << 8) | (a[a.length - 2] & 0xFF);
        int sum8 = 0;
        int xor8 = 0;
        int sum16 = 0;
        for (int i = 0; i < a.length - 2; i++) {
            int b = a[i] & 0xFF;
            sum8 = (sum8 + b) & 0xFF;
            xor8 ^= b;
            sum16 = (sum16 + b) & 0xFFFF;
        }
        int crcCcittFFFF = crc16Ccitt(a, 0, a.length - 2, 0xFFFF);
        int crcCcitt0000 = crc16Ccitt(a, 0, a.length - 2, 0x0000);
        int crcX25 = crc16X25(a, 0, a.length - 2);
        if (last2be == crcCcittFFFF || last2le == crcCcittFFFF) return "CRC16_CCITT_FFFF_MATCH";
        if (last2be == crcCcitt0000 || last2le == crcCcitt0000) return "CRC16_CCITT_0000_MATCH";
        if (last2be == crcX25 || last2le == crcX25) return "CRC16_X25_MATCH";
        if ((last2be & 0xFF) == sum8 || (last2le & 0xFF) == sum8) return "8BIT_SUM_CANDIDATE";
        if ((last2be & 0xFF) == xor8 || (last2le & 0xFF) == xor8) return "8BIT_XOR_CANDIDATE";
        if (last2be == sum16 || last2le == sum16) return "SUM16_CANDIDATE";
        return "unknown_last2=0x" + String.format(Locale.US, "%04X", last2be);
    }

    private static int crc16Ccitt(byte[] a, int off, int len, int initial) {
        int crc = initial & 0xFFFF;
        for (int i = off; i < off + len; i++) {
            crc ^= (a[i] & 0xFF) << 8;
            for (int j = 0; j < 8; j++) crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
        }
        return crc & 0xFFFF;
    }

    private static int crc16X25(byte[] a, int off, int len) {
        int crc = 0xFFFF;
        for (int i = off; i < off + len; i++) {
            crc ^= a[i] & 0xFF;
            for (int j = 0; j < 8; j++) crc = ((crc & 1) != 0) ? (crc >>> 1) ^ 0x8408 : (crc >>> 1);
        }
        return (~crc) & 0xFFFF;
    }
}
