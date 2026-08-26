package com.yigit.buds2blescanner.pro;

/** Decoders for status messages documented by GalaxyBudsClient and matching SM-R177 captures. */
public final class Buds2StatusDecoder {
    private Buds2StatusDecoder() {}

    public static void apply(Buds2Message m, Buds2DeviceState state) {
        if (m == null || state == null || !m.crcValid) return;
        byte[] p = m.payload;
        if (m.messageId == 0x60 && p.length >= 6) {
            state.setBattery(u8(p,1), u8(p,2), -1);
            state.setCoupled(u8(p,3) != 0);
            state.setClassicMain(u8(p,4) != 0);
            state.setWearing(u8(p,5));
        } else if (m.messageId == 0x61 && p.length >= 12) {
            state.setBattery(u8(p,2), u8(p,3), -1);
            state.setCoupled(u8(p,4) != 0);
            state.setClassicMain(u8(p,5) != 0);
            state.setWearing(u8(p,6));
            state.setAmbient(u8(p,7) != 0, u8(p,9));
            state.setEq(u8(p,10), u8(p,11));
        }
    }
    private static int u8(byte[] p, int i) { return p[i] & 255; }
}
