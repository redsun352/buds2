package com.yigit.buds2blescanner.pro;

import java.util.Arrays;

/** Parsed SM-R177 RFCOMM message. Unknown payloads are preserved verbatim. */
public final class Buds2Message {
    public final int header;
    public final int messageId;
    public final byte[] payload;
    public final int storedCrcLe;
    public final boolean crcValid;
    public final int subtype;

    public Buds2Message(int header, int messageId, byte[] payload, int storedCrcLe, boolean crcValid) {
        this.header = header;
        this.messageId = messageId;
        this.payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
        this.storedCrcLe = storedCrcLe;
        this.crcValid = crcValid;
        this.subtype = this.payload.length > 0 ? this.payload[0] & 0xFF : -1;
    }

    public boolean is(int id) { return messageId == id; }
    public boolean isF6Subtype(int value) { return messageId == 0xF6 && subtype == value; }
}
