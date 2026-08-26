package com.yigit.buds2blescanner.pro;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Incremental parser for RFCOMM byte streams. It never assumes one read == one frame. */
public final class Buds2StreamParser {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public synchronized List<Buds2Message> feed(byte[] bytes) {
        List<Buds2Message> out = new ArrayList<>();
        if (bytes == null || bytes.length == 0) return out;
        buffer.write(bytes, 0, bytes.length);
        byte[] b = buffer.toByteArray();
        int cursor = 0;
        while (true) {
            while (cursor < b.length && (b[cursor] & 0xFF) != Buds2Protocol.START) cursor++;
            if (cursor + 7 > b.length) break;
            int header = ((b[cursor + 1] & 255) << 8) | (b[cursor + 2] & 255);
            int low = header & 0xFF;
            int expectedTotal = low + 4;
            if (expectedTotal < 7 || expectedTotal > 4096) { cursor++; continue; }
            if (cursor + expectedTotal > b.length) break;
            if ((b[cursor + expectedTotal - 1] & 0xFF) != Buds2Protocol.END) { cursor++; continue; }
            byte[] frame = Arrays.copyOfRange(b, cursor, cursor + expectedTotal);
            ProtocolFrame pf = Buds2Protocol.parse(frame);
            out.add(new Buds2Message(pf.raw == null ? -1 : Buds2Protocol.header(pf.raw), pf.messageId,
                    Buds2Protocol.payload(pf.raw), Buds2Protocol.storedCrcLe(pf.raw), pf.crcValid));
            cursor += expectedTotal;
        }
        buffer.reset();
        if (cursor < b.length) buffer.write(b, cursor, b.length - cursor);
        return out;
    }

    public synchronized void clear() { buffer.reset(); }
}
