package com.yigit.buds2blescanner.pro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Streaming SM-R177 analyzer. It deliberately keeps unknown fields raw instead
 * of guessing their meaning. It can split partial reads and multiple frames.
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

            int header = ((buffer.get(1) & 0xFF) << 8) | (buffer.get(2) & 0xFF);
            // The SM-R177 capture shows a 14-bit length plus a 2-bit rolling sequence.
            int length = header & 0x3FFF;
            int total = length + 5; // FD + 2-byte header + message/payload + CRC2 + DD
            if (length < 2 || total < 7 || total > 8192) {
                buffer.remove(0);
                continue;
            }
            if (buffer.size() < total) break;
            if ((buffer.get(total - 1) & 0xFF) != 0xDD) {
                buffer.remove(0);
                continue;
            }
            byte[] frame = new byte[total];
            for (int i=0; i<total; i++) frame[i] = buffer.get(i);
            buffer.subList(0, total).clear();
            result.add(new FrameEvent(timestampMs, direction, frame));
        }
        return result;
    }

    public synchronized void reset() { buffer.clear(); }

    private int indexOf(int value) {
        for (int i=0; i<buffer.size(); i++) if ((buffer.get(i) & 0xFF) == value) return i;
        return -1;
    }
}
