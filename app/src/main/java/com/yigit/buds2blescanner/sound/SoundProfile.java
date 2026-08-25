package com.yigit.buds2blescanner.sound;

import java.util.Arrays;

/** Professional, device-agnostic EQ profile. Values are DSP targets; they are not
 * claimed to be native Buds2 firmware presets unless the protocol supports them. */
public final class SoundProfile {
    public final String name;
    public final float preampDb;
    public final float[] frequenciesHz;
    public final float[] gainsDb;
    public final float[] q;

    public SoundProfile(String name, float preampDb, float[] frequenciesHz, float[] gainsDb, float[] q) {
        if (frequenciesHz.length != gainsDb.length || gainsDb.length != q.length) {
            throw new IllegalArgumentException("EQ arrays must have equal length");
        }
        this.name = name;
        this.preampDb = preampDb;
        this.frequenciesHz = Arrays.copyOf(frequenciesHz, frequenciesHz.length);
        this.gainsDb = Arrays.copyOf(gainsDb, gainsDb.length);
        this.q = Arrays.copyOf(q, q.length);
    }

    public int bandCount() { return gainsDb.length; }
}
