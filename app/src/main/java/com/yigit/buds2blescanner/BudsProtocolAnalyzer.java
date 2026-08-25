package com.yigit.buds2blescanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Passive analyzer for Samsung Galaxy Buds SPP/RFCOMM frames.
 *
 * Buds+ / Buds2-family SPP frames use:
 *   FD | 16-bit little-endian header | message-id | payload | CRC16 | DD
 *
 * The 16-bit header contains the message size in its low 10 bits. That size
 * is message-id + payload + CRC (3 bytes overhead inside the size field).
 * Bit 12 is the response flag and bit 13 is the fragment flag.
 *
 * Therefore a complete frame is: header-size + 4 bytes
 * (FD + 2-byte header + DD). The payload may contain FD/DD; delimiters alone
 * must never be used to split a stream.
 */
public final class BudsProtocolAnalyzer {
    public static final class Frame {
        public final byte[] bytes;
        public final int length;
        public final int start;
        public final int end;
        public final int header;
        public final int declaredSize;
        public final int messageId;
        public final boolean response;
        public final boolean fragment;
        public final int payloadLength;
        public final int[] variableOffsets;
        public final String checksumNote;
        public final String shapeKey;
        public final String diffNote;

        Frame(byte[] bytes, int header, int messageId, boolean response, boolean fragment,
              int[] variableOffsets, String checksumNote, String shapeKey, String diffNote) {
            this.bytes = bytes;
            this.length = bytes.length;
            this.start = bytes.length == 0 ? -1 : bytes[0] & 0xFF;
            this.end = bytes.length == 0 ? -1 : bytes[bytes.length - 1] & 0xFF;
            this.header = header;
            this.declaredSize = header & 0x03FF;
            this.messageId = messageId;
            this.response = response;
            this.fragment = fragment;
            this.payloadLength = Math.max(0, declaredSize - 3);
            this.variableOffsets = variableOffsets;
            this.checksumNote = checksumNote;
            this.shapeKey = shapeKey;
            this.diffNote = diffNote;
        }

        public String summary() {
            StringBuilder s = new StringBuilder();
            s.append("FRAME len=").append(length)
             .append(" header=").append(String.format(Locale.US, "%04X", header))
             .append(" size=").append(declaredSize)
             .append(" msgId=").append(String.format(Locale.US, "%02X", messageId))
             .append(" payload=").append(payloadLength)
             .append(" response=").append(response)
             .append(" fragment=").append(fragment)
             .append(" start=").append(String.format(Locale.US, "%02X", start))
             .append(" end=").append(String.format(Locale.US, "%02X", end))
             .append(" shape=").append(shapeKey)
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

    /** Feed arbitrary RFCOMM read chunks; handles partial and multiple frames. */
    public synchronized List<Frame> feed(byte[] chunk) {
        List<Frame> out = new ArrayList<>();
        if (chunk == null || chunk.length == 0) return out;
        for (byte b : chunk) pending.add(b);

        while (true) {
            int start = indexOf(0xFD);
            if (start < 0) {
                pending.clear();
                break;
            }
            if (start > 0) pending.subList(0, start).clear();

            // FD + two header bytes are required.
            if (pending.size() < 3) break;

            // Header is little-endian, exactly as the reference implementation.
            int header = (pending.get(1) & 0xFF) | ((pending.get(2) & 0xFF) << 8);
            int declaredSize = header & 0x03FF;
            int expectedLength = declaredSize + 4;

            // A valid non-fragmented SPP message has at least ID + CRC (3 bytes).
            if (declaredSize < 3 || expectedLength > 4096) {
                pending.remove(0);
                continue;
            }
            if (pending.size() < expectedLength) break;

            if ((pending.get(expectedLength - 1) & 0xFF) != 0xDD) {
                // Header-derived boundary is authoritative; if it does not end
                // in DD, resynchronize at the next FD rather than trusting a DD
                // that may be inside the payload.
                pending.remove(0);
                continue;
            }

            byte[] frame = new byte[expectedLength];
            for (int i = 0; i < expectedLength; i++) frame[i] = pending.get(i);
            pending.subList(0, expectedLength).clear();

            int messageId = frame[3] & 0xFF;
            boolean response = (header & 0x1000) != 0;
            boolean fragment = (header & 0x2000) != 0;
            String shape = shapeKey(frame, header, messageId);
            String diff = diffAgainstPreviousSameShape(frame, shape);
            int[] vars = variableOffsetsAgainstPreviousSameShape(frame, shape);
            Frame f = new Frame(frame, header, messageId, response, fragment,
                    vars, checksumAnalysis(frame), shape, diff);
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

    private String shapeKey(byte[] a, int header, int messageId) {
        return String.format(Locale.US, "FD-H%04X-ID%02X-L%d", header, messageId, a.length);
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
                if (count >= 32) { s.append(",..."); break; }
            }
        }
        return count == 0 ? "identical" : s.toString();
    }

    /** Verify the exact CRC16-CCITT variant used by GalaxyBudsClient. */
    private String checksumAnalysis(byte[] a) {
        if (a.length < 8) return "too_short";
        int crcOffset = a.length - 3;
        int receivedLo = a[crcOffset] & 0xFF;
        int receivedHi = a[crcOffset + 1] & 0xFF;
        int receivedLE = receivedLo | (receivedHi << 8);

        // CRC is calculated over message ID + payload, excluding FD/header/CRC/DD.
        int calculated = crc16Ccitt(a, 3, crcOffset - 3);
        if (receivedLE == calculated) return String.format(Locale.US, "CRC16_CCITT_LE_MATCH=0x%04X", calculated);

        // Also test the swapped representation seen in some diagnostic dumps.
        int receivedBE = (receivedLo << 8) | receivedHi;
        if (receivedBE == calculated) return String.format(Locale.US, "CRC16_CCITT_BE_MATCH=0x%04X", calculated);

        return String.format(Locale.US, "CRC_MISMATCH recvLE=0x%04X calc=0x%04X", receivedLE, calculated);
    }

    // Same table/polynomial as GalaxyBudsClient Crc16.crc16_ccitt: poly 0x1021,
    // initial 0, non-reflected. The Buds implementation writes the resulting
    // short as little-endian on Android/Windows little-endian targets.
    private static int crc16Ccitt(byte[] a, int off, int len) {
        int crc = 0;
        for (int i = off; i < off + len; i++) {
            crc ^= (a[i] & 0xFF) << 8;
            for (int j = 0; j < 8; j++) {
                crc = ((crc & 0x8000) != 0)
                        ? ((crc << 1) ^ 0x1021) & 0xFFFF
                        : (crc << 1) & 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }
}
