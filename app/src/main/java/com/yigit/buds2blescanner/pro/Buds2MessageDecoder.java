package com.yigit.buds2blescanner.pro;

import java.util.Arrays;

/** Capture-derived, conservative SM-R177 message decoder. Unknown fields remain raw. */
public final class Buds2MessageDecoder {
    private Buds2MessageDecoder() {}

    public static DecodedMessage decode(ProtocolFrame frame) {
        if (frame == null) return new DecodedMessage(-1, new byte[0], "INVALID");
        String name;
        switch (frame.messageId) {
            case 0x60: name = "STATUS_UPDATED"; break;
            case 0x61: name = "EXTENDED_STATUS_UPDATED"; break;
            case 0x63: name = "STATUS_63"; break;
            case 0x41: name = "METERING"; break;
            case 0xF5: name = "TELEMETRY_F5"; break;
            case 0xF6: name = "TELEMETRY_F6"; break;
            default: name = String.format("UNKNOWN_%02X", frame.messageId & 0xFF);
        }
        return new DecodedMessage(frame.messageId, extractPayload(frame.raw), name);
    }

    private static byte[] extractPayload(byte[] raw) {
        if (raw == null || raw.length < 7) return new byte[0];
        return Arrays.copyOfRange(raw, 4, raw.length - 3);
    }

    public static final class DecodedMessage {
        public final int messageId;
        public final byte[] payload;
        public final String type;
        public DecodedMessage(int messageId, byte[] payload, String type) {
            this.messageId = messageId; this.payload = payload; this.type = type;
        }
    }
}
