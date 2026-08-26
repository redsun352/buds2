package com.yigit.buds2blescanner.pro;

public final class ProtocolFrame {
    public final byte[] raw;
    public final int messageId;
    public final boolean crcValid;
    public ProtocolFrame(byte[] raw, int messageId, boolean crcValid) {
        this.raw = raw; this.messageId = messageId; this.crcValid = crcValid;
    }
}
