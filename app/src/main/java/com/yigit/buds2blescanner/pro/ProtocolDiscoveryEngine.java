package com.yigit.buds2blescanner.pro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline/online protocol discovery engine for SM-R177 captures.
 * It never assigns semantic names from guesses. It discovers structural fields,
 * RX/TX correlations and state transitions from observed frames.
 */
public final class ProtocolDiscoveryEngine {
    public static final class Observation {
        public final long timestampMs;
        public final ProtocolAnalyzer.Direction direction;
        public final int messageId;
        public final int sequence;
        public final byte[] payload;
        public final byte[] raw;
        public Observation(ProtocolAnalyzer.FrameEvent f) {
            timestampMs = f.timestampMs;
            direction = f.direction;
            messageId = f.messageId;
            sequence = f.sequence;
            payload = Arrays.copyOf(f.payload, f.payload.length);
            raw = Arrays.copyOf(f.raw, f.raw.length);
        }
    }

    public static final class FieldMap {
        public final int offset;
        public final int width;
        public final boolean constant;
        public final List<Integer> distinctValues;
        public final int observations;
        public FieldMap(int offset, int width, boolean constant, List<Integer> values, int observations) {
            this.offset = offset; this.width = width; this.constant = constant;
            this.distinctValues = Collections.unmodifiableList(new ArrayList<>(values));
            this.observations = observations;
        }
    }

    public static final class Correlation {
        public final long txTimestampMs;
        public final long rxTimestampMs;
        public final int txMessageId;
        public final int rxMessageId;
        public final long deltaMs;
        public Correlation(long tx, long rx, int txId, int rxId) {
            txTimestampMs = tx; rxTimestampMs = rx; txMessageId = txId; rxMessageId = rxId;
            deltaMs = rx - tx;
        }
    }

    private final List<Observation> observations = new ArrayList<>();
    private final Map<Integer, List<Observation>> byMessage = new LinkedHashMap<>();

    public synchronized void observe(ProtocolAnalyzer.FrameEvent frame) {
        Observation o = new Observation(frame);
        observations.add(o);
        byMessage.computeIfAbsent(o.messageId, k -> new ArrayList<>()).add(o);
    }

    public synchronized void observeAll(List<ProtocolAnalyzer.FrameEvent> frames) {
        if (frames == null) return;
        for (ProtocolAnalyzer.FrameEvent f : frames) observe(f);
    }

    public synchronized List<Observation> observations() {
        return Collections.unmodifiableList(new ArrayList<>(observations));
    }

    /** Finds structural byte fields. A field is constant only when every observed payload has the same value. */
    public synchronized Map<Integer, List<FieldMap>> discoverFields() {
        Map<Integer, List<FieldMap>> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Observation>> entry : byMessage.entrySet()) {
            List<Observation> list = entry.getValue();
            int max = 0;
            for (Observation o : list) max = Math.max(max, o.payload.length);
            List<FieldMap> fields = new ArrayList<>();
            for (int offset = 0; offset < max; offset++) {
                List<Integer> values = new ArrayList<>();
                boolean complete = true;
                for (Observation o : list) {
                    if (offset >= o.payload.length) { complete = false; break; }
                    int v = o.payload[offset] & 0xFF;
                    if (!values.contains(v) && values.size() < 32) values.add(v);
                }
                if (complete) fields.add(new FieldMap(offset, 1, values.size() <= 1, values, list.size()));
            }
            result.put(entry.getKey(), fields);
        }
        return result;
    }

    /** Correlates each TX frame with the first RX frame following it within the supplied window. */
    public synchronized List<Correlation> correlate(long windowMs) {
        List<Correlation> result = new ArrayList<>();
        List<Observation> tx = new ArrayList<>();
        List<Observation> rx = new ArrayList<>();
        for (Observation o : observations) {
            if (o.direction == ProtocolAnalyzer.Direction.TX) tx.add(o); else rx.add(o);
        }
        for (Observation t : tx) {
            Observation best = null;
            for (Observation r : rx) {
                long d = r.timestampMs - t.timestampMs;
                if (d < 0 || d > windowMs) continue;
                if (best == null || r.timestampMs < best.timestampMs) best = r;
            }
            if (best != null) result.add(new Correlation(t.timestampMs, best.timestampMs, t.messageId, best.messageId));
        }
        return result;
    }

    /** Returns a JSON-ready, deterministic map without semantic guessing. */
    public synchronized String toJson() {
        StringBuilder s = new StringBuilder();
        s.append("{\"version\":1,\"model\":\"SM-R177\",\"observations\":").append(observations.size()).append(",\"messages\":[");
        boolean firstMessage = true;
        Map<Integer, List<FieldMap>> fields = discoverFields();
        for (Map.Entry<Integer, List<Observation>> e : byMessage.entrySet()) {
            if (!firstMessage) s.append(','); firstMessage = false;
            int id = e.getKey();
            s.append("{\"id\":").append(id).append(",\"count\":").append(e.getValue().size()).append(",\"fields\":[");
            boolean firstField = true;
            for (FieldMap f : fields.get(id)) {
                if (!firstField) s.append(','); firstField = false;
                s.append("{\"offset\":").append(f.offset)
                  .append(",\"width\":1,\"constant\":").append(f.constant)
                  .append(",\"observations\":").append(f.observations).append("}");
            }
            s.append("]}");
        }
        s.append("]}");
        return s.toString();
    }

    public synchronized void reset() { observations.clear(); byMessage.clear(); }
}
