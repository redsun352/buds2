package com.yigit.buds2blescanner.pro;

import java.util.ArrayList;
import java.util.List;

/** Converts AYF1 firmware knowledge into safe UI capabilities. */
public final class FirmwareFeatureRegistry {
    public enum Confidence { FIRMWARE_PRESENT, PROTOCOL_VERIFIED, CAPTURE_VERIFIED }

    public static final class Feature {
        public final String id;
        public final String label;
        public final String firmwareEvidence;
        public final Confidence confidence;

        Feature(String id, String label, String evidence) {
            this.id = id;
            this.label = label;
            this.firmwareEvidence = evidence;
            this.confidence = Confidence.FIRMWARE_PRESENT;
        }
    }

    private FirmwareFeatureRegistry() {}

    public static List<Feature> forAyf1() {
        List<Feature> result = new ArrayList<>();
        for (String id : Ayf1FirmwareProfile.featureNames()) {
            result.add(new Feature(id, humanize(id), Ayf1FirmwareProfile.features().get(id)));
        }
        return result;
    }

    private static String humanize(String value) {
        String[] parts = value.split("_");
        StringBuilder b = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return b.toString();
    }
}
