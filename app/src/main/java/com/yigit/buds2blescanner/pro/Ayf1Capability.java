package com.yigit.buds2blescanner.pro;

/** Capability state derived from AYF1 evidence. */
public final class Ayf1Capability {
    public enum State { FIRMWARE_PRESENT, PROTOCOL_UNVERIFIED, WIRE_VERIFIED }
    public final String id;
    public final State state;
    public final String[] evidence;

    public Ayf1Capability(String id, State state, String... evidence) {
        this.id = id;
        this.state = state;
        this.evidence = evidence == null ? new String[0] : evidence.clone();
    }

    public boolean canTransmit() {
        return state == State.WIRE_VERIFIED;
    }
}
