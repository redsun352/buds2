package com.yigit.buds2blescanner.pro;

/**
 * Central safety gate for AYF1 commands. Firmware symbols alone are never
 * considered a valid wire command.
 */
public final class Ayf1CommandGate {
    private Ayf1CommandGate() {}

    public static boolean isCompatible(String model, String firmware) {
        return Ayf1FirmwareProfile.isKnownFirmware(model, firmware);
    }

    public static boolean canSend(String model, String firmware,
                                   Ayf1Capability.State evidence) {
        return isCompatible(model, firmware)
                && evidence == Ayf1Capability.State.WIRE_VERIFIED;
    }

    public static String reason(String model, String firmware,
                                Ayf1Capability.State evidence) {
        if (!isCompatible(model, firmware)) {
            return "Firmware/model mismatch: expected SM-R177 / R177XXU0AYF1";
        }
        if (evidence != Ayf1Capability.State.WIRE_VERIFIED) {
            return "Wire payload is not verified; firmware symbol evidence alone is insufficient";
        }
        return "OK";
    }
}
