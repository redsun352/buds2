package com.yigit.buds2blescanner.pro;

public final class CaptureRecord {
    public enum Direction { RX, TX }
    public final long timestampMs;
    public final Direction direction;
    public final int messageId;
    public final boolean crcValid;
    public final String hex;
    public CaptureRecord(long timestampMs, Direction direction, int messageId, boolean crcValid, String hex) {
        this.timestampMs=timestampMs; this.direction=direction; this.messageId=messageId; this.crcValid=crcValid; this.hex=hex;
    }
}
