package com.yigit.buds2blescanner.sound;

/** Lightweight real-time EQ math engine for visualization/processing layers. */
public final class SoundStudioEngine {
    private SoundStudioEngine() {}

    public static float[] applyPreamp(float[] samples, float preampDb) {
        float gain = (float)Math.pow(10.0, preampDb / 20.0);
        float[] out = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float x = samples[i] * gain;
            out[i] = Math.max(-1f, Math.min(1f, x));
        }
        return out;
    }

    public static float dbToLinear(float db) {
        return (float)Math.pow(10.0, db / 20.0);
    }

    public static float linearToDb(float linear) {
        return 20f * (float)Math.log10(Math.max(linear, 1e-9f));
    }
}
