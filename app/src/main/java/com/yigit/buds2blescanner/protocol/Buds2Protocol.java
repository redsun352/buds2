package com.yigit.buds2blescanner.protocol;

import java.util.Locale;

/**
 * Buds2 protocol primitives shared by transport, decoder and capture layers.
 * This class deliberately does not invent command payloads: TX commands must
 * be added only after they have been verified against a capture.
 */
public final class Buds2Protocol {
    public static final int FRAME_START = 0xFD;
    public static final int FRAME_END = 0xDD;
    public static final int SPP_HEADER_SIZE = 3;
    public static final int CRC_SIZE = 2;
    public static final int TRAILER_SIZE = 3;
    public static final int MAX_FRAME_SIZE = 4096;

    private Buds2Protocol() {}

    public static int u8(byte[] b, int i) { return b[i] & 0xFF; }

    public static int header(byte[] b) {
        return u8(b, 1) | (u8(b, 2) << 8);
    }

    public static int declaredSize(byte[] b) { return header(b) & 0x03FF; }

    public static int messageId(byte[] b) { return u8(b, 3); }

    public static boolean isResponse(byte[] b) { return (header(b) & 0x1000) != 0; }

    public static boolean isFragment(byte[] b) { return (header(b) & 0x2000) != 0; }

    public static int crc16Ccitt(byte[] b, int off, int len) {
        int crc = 0;
        for (int i = off; i < off + len; i++) {
            crc ^= (b[i] & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc & 0x8000) != 0)
                        ? ((crc << 1) ^ 0x1021) & 0xFFFF
                        : (crc << 1) & 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }

    public static boolean hasValidCrc(byte[] frame) {
        if (!isCompleteFrame(frame)) return false;
        int crcPos = frame.length - 3;
        int received = u8(frame, crcPos) | (u8(frame, crcPos + 1) << 8);
        int calculated = crc16Ccitt(frame, 3, crcPos - 3);
        return received == calculated;
    }

    public static boolean isCompleteFrame(byte[] frame) {
        if (frame == null || frame.length < 8 || u8(frame, 0) != FRAME_START
                || u8(frame, frame.length - 1) != FRAME_END) return false;
        int declared = declaredSize(frame);
        return declared >= 3 && declared + 4 == frame.length && frame.length <= MAX_FRAME_SIZE;
    }

    public static byte[] buildVerifiedFrame(int messageId, byte[] payload, int flags) {
        if (messageId < 0 || messageId > 255) throw new IllegalArgumentException("messageId");
        if (payload == null) payload = new byte[0];
        int declared = 1 + payload.length + CRC_SIZE;
        int total = declared + 4;
        if (total > MAX_FRAME_SIZE) throw new IllegalArgumentException("payload too large");
        byte[] out = new byte[total];
        out[0] = (byte) FRAME_START;
        int h = (declared & 0x03FF) | (flags & 0xF000);
        out[1] = (byte) h;
        out[2] = (byte) (h >>> 8);
        out[3] = (byte) messageId;
        System.arraycopy(payload, 0, out, 4, payload.length);
        int crcPos = total - 3;
        int crc = crc16Ccitt(out, 3, crcPos - 3);
        out[crcPos] = (byte) crc;
        out[crcPos + 1] = (byte) (crc >>> 8);
        out[crcPos + 2] = (byte) FRAME_END;
        return out;
    }

    public static String hex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder s = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) s.append(String.format(Locale.US, "%02X ", b & 0xFF));
        return s.toString().trim();
    }
}
