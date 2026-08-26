package com.yigit.buds2blescanner.pro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Streaming SM-R177 analyzer. It keeps unknown fields raw, splits partial reads
 * and multiple frames, and does not assume the 16-bit header is a frame length.
 */
public final class ProtocolAnalyzer {
    public enum Direction { RX, TX }

    public static final class FrameEvent {
        public final long timestampMs;
        public final Direction direction;
        public final byte[] raw;
        public final int header;
        public final int sequence;
        public final int messageId;
        public final byte[] payload;
        public final int storedCrc;
        public final boolean crcValid;
        public final int subtype;

        FrameEvent(long timestampMs, Direction direction, byte[] raw) {
            this.timestampMs = timestampMs;
            this.direction = direction;
            this.raw = Arrays.copyOf(raw, raw.length);
            this.header = Buds2Protocol.header(raw);
            this.sequence = (header < 0) ? -1 : ((header >>> 14) & 0x03);
            this.messageId = Buds2Protocol.messageId(raw);
            this.payload = Buds2Protocol.payload(raw);
            this.storedCrc = Buds2Protocol.storedCrcLe(raw);
            this.crcValid = Buds2Protocol.verifyCrcLe(raw);
            this.subtype = payload.length == 0 ? -1 : payload[0] & 0xFF;
        }

        public boolean is(int id) { return messageId == id; }
        public boolean isF6Subtype(int type) { return messageId == 0xF6 && subtype == type; }
        public String hex() { return Buds2Protocol.hex(raw); }
    }

    private final List<Byte> buffer = new ArrayList<>();

    public synchronized List<FrameEvent> feed(Direction direction, byte[] bytes, long timestampMs) {
        if (bytes == null || bytes.length == 0) return Collections.emptyList();
        for (byte b : bytes) buffer.add(b);
        List<FrameEvent> result = new ArrayList<>();
        while (true) {
            int start = indexOf(0xFD);
            if (start < 0) { buffer.clear(); break; }
            if (start > 0) buffer.subList(0, start).clear();
            if (buffer.size() < 7) break;

            // Do not derive frame length from the two-byte header: capture data
            // contains values such as 08 2A/C8 2A while the actual frame is much
            // shorter. Find the earliest DD that produces a valid CRC instead.
            int end = findValidEnd(6, Math.min(buffer.size() - 1, 8191));
            if (end < 0) {
                // If there is no complete candidate yet, keep the buffer. If it
                // has grown too large without a valid frame, resync at next FD.
                if (buffer.size() > 8192) buffer.remove(0);
                break;
            }

            int total = end + 1;
            byte[] frame = new byte[total];
            for (int i=0; i<total; i++) frame[i] = buffer.get(i);
            buffer.subList(0, total).clear();
            result.add(new FrameEvent(timestampMs, direction, frame));
        }
        return result;
    }

    private int findValidEnd(int from, int to) {
        for (int i = Math.max(from, 6); i <= to; i++) {
            if ((buffer.get(i) & 0xFF) != 0xDD) continue;
            int length = i + 1;
            byte[] candidate = new byte[length];
            for (int j=0; j<length; j++) candidate[j] = buffer.get(j);
            if (Buds2Protocol.verifyCrcLe(candidate)) return i;
        }
        return -1;
    }

    public synchronized void reset() { buffer.clear(); }

    private int indexOf(int value) {
        for (int i=0; i<buffer.size(); i++) if ((buffer.get(i) & 0xFF) == value) return i;
        return -1;
    }
}
