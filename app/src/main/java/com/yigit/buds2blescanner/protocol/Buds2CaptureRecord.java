package com.yigit.buds2blescanner.protocol;

/** Single timestamped capture event suitable for JSON export. */
public final class Buds2CaptureRecord {
    public final long timestampMs;
    public final String direction;
    public final String type;
    public final byte[] data;
    public final int messageId;
    public final boolean crcValid;

    public Buds2CaptureRecord(long timestampMs, String direction, String type,
                              byte[] data, int messageId, boolean crcValid) {
        this.timestampMs = timestampMs;
        this.direction = direction;
        this.type = type;
        this.data = data == null ? new byte[0] : data.clone();
        this.messageId = messageId;
        this.crcValid = crcValid;
    }

    public String toJsonLine() {
        return "{\"ts\":" + timestampMs
                + ",\"direction\":\"" + escape(direction)
                + "\",\"type\":\"" + escape(type)
                + "\",\"messageId\":" + messageId
                + ",\"crcValid\":" + crcValid
                + ",\"hex\":\"" + Buds2Protocol.hex(data) + "\"}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
