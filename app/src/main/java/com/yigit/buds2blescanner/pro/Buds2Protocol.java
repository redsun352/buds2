package com.yigit.buds2blescanner.pro;

import java.util.Arrays;

/**
 * Buds2/SM-R177 RFCOMM stream parser.
 *
 * Research basis: GalaxyBudsClient documents the Buds-family RFComm frame as
 * FD + 2-byte header + message id + payload + CRC16-CCITT + DD for the
 * older/Buds+ style framing. Our SM-R177 captures use that FD/DD framing,
 * but the capture-derived header encoding must be retained rather than
 * assuming the newer FE/EE format.
 *
 * The parser therefore validates framing and exposes header bytes, message id
 * and payload without guessing undocumented fields. CRC validation is opt-in
 * because the exact checksum placement/endianness for this SM-R177 capture
 * must be verified against TX/RX pairs before commands are trusted.
 */
public final class Buds2Protocol {
    public static final int START = 0xFD;
    public static final int END = 0xDD;
    private Buds2Protocol() {}

    public static ProtocolFrame parse(byte[] frame) {
        if (frame == null || frame.length < 7 || (frame[0] & 0xFF) != START
                || (frame[frame.length - 1] & 0xFF) != END) {
            return new ProtocolFrame(frame, -1, false);
        }
        int id = frame.length > 3 ? frame[3] & 0xFF : -1;
        // Do not claim CRC validity until the SM-R177 checksum layout is
        // verified against captures. Preserve the raw frame instead.
        return new ProtocolFrame(Arrays.copyOf(frame, frame.length), id, false);
    }

    public static int messageId(byte[] frame) {
        return frame != null && frame.length > 3 && (frame[0] & 0xFF) == START
                ? frame[3] & 0xFF : -1;
    }

    public static byte[] payload(byte[] frame) {
        if (frame == null || frame.length < 7 || (frame[0] & 0xFF) != START
                || (frame[frame.length - 1] & 0xFF) != END) return new byte[0];
        return Arrays.copyOfRange(frame, 4, frame.length - 3);
    }

    public static int crc16Ccitt(byte[] data, int off, int len) {
        int crc = 0xFFFF;
        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                crc = (crc & 0x8000) != 0
                        ? ((crc << 1) ^ 0x1021) & 0xFFFF
                        : (crc << 1) & 0xFFFF;
            }
        }
        return crc & 0xFFFF;
    }

    public static String hex(byte[] data) {
        if (data == null) return "";
        StringBuilder s = new StringBuilder(data.length * 3);
        for (byte b : data) s.append(String.format("%02X ", b & 0xFF));
        return s.toString().trim();
    }
}
