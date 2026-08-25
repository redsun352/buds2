package com.yigit.buds2blescanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Passive Samsung Galaxy Buds SPP/RFCOMM analyzer.
 *
 * Frame format documented by GalaxyBudsClient:
 *   FD | uint16 LE header | msgId | payload | CRC16 | DD
 *
 * The low 10 bits of the header contain MsgId + payload + CRC size.
 * Therefore total wire-frame length is declaredSize + 4.
 * FD/DD are NOT payload delimiters; only the header-derived length is authoritative.
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
        public final int crcReceived;
        public final int crcCalculated;
        public final boolean crcValid;
        public final int[] variableOffsets;
        public final String checksumNote;
        public final String shapeKey;
        public final String diffNote;
        public final String messageName;
        public final String decodedInfo;

        Frame(byte[] bytes, int header, int messageId, boolean response, boolean fragment,
              int crcReceived, int crcCalculated, boolean crcValid,
              int[] variableOffsets, String checksumNote, String shapeKey,
              String diffNote, String messageName, String decodedInfo) {
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
            this.crcReceived = crcReceived;
            this.crcCalculated = crcCalculated;
            this.crcValid = crcValid;
            this.variableOffsets = variableOffsets;
            this.checksumNote = checksumNote;
            this.shapeKey = shapeKey;
            this.diffNote = diffNote;
            this.messageName = messageName;
            this.decodedInfo = decodedInfo;
        }

        public String summary() {
            StringBuilder s = new StringBuilder();
            s.append("FRAME len=").append(length)
             .append(" header=").append(String.format(Locale.US, "%04X", header))
             .append(" size=").append(declaredSize)
             .append(" msgId=").append(String.format(Locale.US, "%02X", messageId))
             .append(" (").append(messageName).append(")")
             .append(" payload=").append(payloadLength)
             .append(" response=").append(response)
             .append(" fragment=").append(fragment)
             .append(" start=").append(String.format(Locale.US, "%02X", start))
             .append(" end=").append(String.format(Locale.US, "%02X", end))
             .append(" shape=").append(shapeKey)
             .append(" checksum=").append(checksumNote);
            if (diffNote != null && !diffNote.isEmpty()) s.append(" diff=").append(diffNote);
            if (decodedInfo != null && !decodedInfo.isEmpty()) s.append(" decode=").append(decodedInfo);
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

    /** Feed arbitrary RFCOMM read chunks; supports partial and multiple frames. */
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
            if (pending.size() < 3) break;

            int header = (pending.get(1) & 0xFF) | ((pending.get(2) & 0xFF) << 8);
            int declaredSize = header & 0x03FF;
            int expectedLength = declaredSize + 4;

            // A valid non-fragmented SPP frame has msgId + CRC in declaredSize.
            if (declaredSize < 3 || expectedLength > 4096) {
                pending.remove(0);
                continue;
            }
            if (pending.size() < expectedLength) break;

            // IMPORTANT: never search for DD. It is checked only at the
            // header-derived final byte, so DD inside payload is legal.
            int finalByte = pending.get(expectedLength - 1) & 0xFF;
            if (finalByte != 0xDD) {
                // Keep the complete candidate in the buffer only if a later
                // FD could form a better candidate. Otherwise resynchronize
                // by one byte. This prevents a payload DD from truncating a frame.
                int nextFd = indexOfFrom(0xFD, 1);
                if (nextFd >= 0) {
                    pending.subList(0, nextFd).clear();
                    continue;
                }
                pending.remove(0);
                continue;
            }

            byte[] frame = new byte[expectedLength];
            for (int i = 0; i < expectedLength; i++) frame[i] = pending.get(i);
            pending.subList(0, expectedLength).clear();

            int messageId = frame[3] & 0xFF;
            boolean response = (header & 0x1000) != 0;
            boolean fragment = (header & 0x2000) != 0;
            CrcResult crc = checksum(frame);
            String shape = shapeKey(frame, messageId);
            String diff = diffAgainstPreviousSameShape(frame, shape);
            int[] vars = variableOffsetsAgainstPreviousSameShape(frame, shape);
            String name = messageName(messageId);
            String decoded = decodeKnownMessage(messageId, frame);
            Frame f = new Frame(frame, header, messageId, response, fragment,
                    crc.received, crc.calculated, crc.valid, vars, crc.note,
                    shape, diff, name, decoded);
            history.add(f);
            frameNumber++;
            out.add(f);
        }
        return out;
    }

    public synchronized int frameCount() { return frameNumber; }

    private int indexOf(int value) { return indexOfFrom(value, 0); }

    private int indexOfFrom(int value, int from) {
        for (int i = Math.max(0, from); i < pending.size(); i++) {
            if ((pending.get(i) & 0xFF) == value) return i;
        }
        return -1;
    }

    private String shapeKey(byte[] a, int messageId) {
        return String.format(Locale.US, "FD-ID%02X-PAYLOAD%d", messageId, Math.max(0, a.length - 7));
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
        // Exclude CRC and postamble: those change as a consequence of payload changes.
        int compareEnd = Math.max(0, a.length - 3);
        for (int i = 0; i < compareEnd; i++) if (a[i] != p.bytes[i]) v.add(i);
        int[] out = new int[v.size()];
        for (int i = 0; i < v.size(); i++) out[i] = v.get(i);
        return out;
    }

    private String diffAgainstPreviousSameShape(byte[] a, String shape) {
        Frame p = previousSameShape(shape);
        if (p == null || p.bytes.length != a.length) return "none";
        int count = 0;
        StringBuilder s = new StringBuilder();
        int compareEnd = Math.max(0, a.length - 3);
        for (int i = 0; i < compareEnd; i++) {
            if (a[i] != p.bytes[i]) {
                if (count == 0) s.append("offsets="); else s.append(',');
                s.append(i).append(':')
                 .append(String.format(Locale.US, "%02X", p.bytes[i] & 0xFF))
                 .append('>')
                 .append(String.format(Locale.US, "%02X", a[i] & 0xFF));
                if (++count >= 32) { s.append(",..."); break; }
            }
        }
        return count == 0 ? "identical_payload" : s.toString();
    }

    private static final class CrcResult {
        final int received, calculated;
        final boolean valid;
        final String note;
        CrcResult(int received, int calculated, boolean valid, String note) {
            this.received = received; this.calculated = calculated; this.valid = valid; this.note = note;
        }
    }

    /** CRC-16/XMODEM-style CCITT used by the Buds protocol notes: poly 0x1021, init 0. */
    private static CrcResult checksum(byte[] a) {
        if (a.length < 8) return new CrcResult(-1, -1, false, "too_short");
        int crcOffset = a.length - 3;
        int lo = a[crcOffset] & 0xFF;
        int hi = a[crcOffset + 1] & 0xFF;
        int received = lo | (hi << 8);
        int calculated = crc16Ccitt(a, 3, crcOffset - 3);
        boolean valid = received == calculated;
        String note = valid
                ? String.format(Locale.US, "CRC16_CCITT_LE_OK=0x%04X", calculated)
                : String.format(Locale.US, "CRC_FAIL recvLE=0x%04X calc=0x%04X", received, calculated);
        return new CrcResult(received, calculated, valid, note);
    }

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

    private static String messageName(int id) {
        switch (id) {
            case 0x41: return "METERING_REPORT";
            case 0x51: return "RESP";
            case 0x60: return "STATUS_UPDATED";
            case 0x61: return "EXTENDED_STATUS_UPDATED";
            case 0x62: return "CONNECTION_UPDATED";
            case 0x63: return "VERSION_INFO";
            case 0x64: return "SET_HOT_COMMAND";
            case 0x6C: return "PAUSE_MEDIA_WHEN_ONE_BUD_REMOVED";
            case 0x6F: return "SET_ANC_WITH_ONE_EARBUD";
            case 0x70: return "MAIN_CHANGE";
            case 0x71: return "PROFILE_CONTROL";
            case 0x72: return "PAIRING_MODE";
            case 0x77: return "MULTIPOINT_INFO";
            case 0x78: return "NOISE_CONTROLS_UPDATE";
            case 0x79: return "NOISE_CONTROLS";
            case 0x7B: return "SET_DETECT_CONVERSATIONS";
            case 0x7C: return "SET_DETECT_CONVERSATIONS_DURATION";
            case 0x7D: return "SET_SPATIAL_AUDIO";
            case 0x80: return "SET_AMBIENT_MODE";
            case 0x81: return "AMBIENT_MODE_UPDATED";
            case 0x82: return "CUSTOMIZE_AMBIENT_SOUND";
            case 0x83: return "NOISE_REDUCTION_LEVEL";
            case 0x84: return "AMBIENT_VOLUME";
            case 0x85: return "ADJUST_SOUND_SYNC";
            case 0x86: return "EQUALIZER";
            case 0x87: return "GAME_MODE";
            case 0x88: return "MANAGER_INFO";
            case 0x8B: return "SET_SIDETONE";
            case 0x90: return "LOCK_TOUCHPAD";
            case 0x91: return "TOUCH_UPDATED";
            case 0x92: return "SET_TOUCHPAD_OPTION";
            case 0x93: return "SET_TOUCHPAD_OTHER_OPTION";
            case 0x94: return "BATTERY_TYPE";
            case 0x9F: return "PASS_THROUGH";
            case 0xA0: return "FIND_MY_EARBUDS_START";
            case 0xA1: return "FIND_MY_EARBUDS_STOP";
            case 0xA2: return "MUTE_EARBUD";
            case 0xA3: return "MUTE_EARBUD_STATUS_UPDATED";
            case 0xA7: return "UPDATE_TIME";
            case 0xAB: return "SELF_TEST";
            case 0xB0: return "FOTA_V1_SESSION";
            case 0xB1: return "FOTA_V1_CONTROL";
            case 0xB2: return "FOTA_V1_DOWNLOAD_DATA";
            case 0xB3: return "FOTA_V1_UPDATED";
            case 0xF1: return "DEBUG_ERROR_CODE";
            case 0xF2: return "DEBUG_EVENT";
            default: return String.format(Locale.US, "UNKNOWN_0x%02X", id);
        }
    }

    private static String decodeKnownMessage(int id, byte[] frame) {
        int payloadStart = 4;
        int payloadEnd = frame.length - 3;
        if (payloadEnd <= payloadStart) return "";
        switch (id) {
            case 0x61: {
                int n = payloadEnd - payloadStart;
                if (n < 12) return "EXT_STATUS payload=" + n;
                return String.format(Locale.US,
                        "MR=%d ear=%d batL=%d%% batR=%d%% coupled=%d main=%d wearing=%d ambient=%d voiceFocus=%d ambientVolRaw=%d eq=%d eqType=%d",
                        u8(frame, payloadStart), u8(frame, payloadStart + 1),
                        u8(frame, payloadStart + 2), u8(frame, payloadStart + 3),
                        u8(frame, payloadStart + 4), u8(frame, payloadStart + 5),
                        u8(frame, payloadStart + 6), u8(frame, payloadStart + 7),
                        u8(frame, payloadStart + 8), u8(frame, payloadStart + 9),
                        u8(frame, payloadStart + 10), u8(frame, payloadStart + 11));
            }
            case 0x60: {
                int n = payloadEnd - payloadStart;
                if (n < 6) return "STATUS payload=" + n;
                return String.format(Locale.US,
                        "ear=%d batL=%d%% batR=%d%% coupled=%d main=%d wearing=%d",
                        u8(frame, payloadStart), u8(frame, payloadStart + 1),
                        u8(frame, payloadStart + 2), u8(frame, payloadStart + 3),
                        u8(frame, payloadStart + 4), u8(frame, payloadStart + 5));
            }
            case 0x51:
                if (payloadEnd - payloadStart >= 2)
                    return "action=" + hex(frame[payloadStart]) + " result=" + u8(frame, payloadStart + 1);
                return "RESP";
            default:
                return "";
        }
    }

    private static int u8(byte[] a, int i) { return a[i] & 0xFF; }
    private static String hex(byte b) { return String.format(Locale.US, "%02X", b & 0xFF); }
}
