package com.yigit.buds2blescanner.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Curated studio-oriented profiles inspired by common tuning goals.
 * These are original profiles, not copies of proprietary preset tables. */
public final class ProfessionalSoundProfiles {
    private static final float[] F = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};
    private static final float[] Q = {0.707f,0.707f,0.707f,0.9f,1.0f,1.0f,1.0f,1.0f,0.9f,0.707f};

    private ProfessionalSoundProfiles() {}

    public static List<SoundProfile> all() {
        List<SoundProfile> p = new ArrayList<>();
        p.add(profile("Studio Neutral", 0, 0,0,0,0,0,0,0,0,0,0));
        p.add(profile("Warm Reference", -2, 1.5f,1.2f,0.8f,0.5f,0,-0.2f,-0.4f,-0.2f,-0.5f,-1));
        p.add(profile("Bright Reference", -2, -0.5f,-0.3f,0,0,0,0.2f,0.8f,1.5f,1.2f,0.8f));
        p.add(profile("Deep Bass", -5, 5,4,3,1.5f,0,-0.5f,-1,-1,-0.5f,0));
        p.add(profile("Bass Head", -6, 6,5,3.5f,1,0,-1,-1.5f,-1,-0.5f,0));
        p.add(profile("Vocal Focus", -2, -1,-1,-0.5f,0,0.5f,1.5f,2.5f,1.5f,-0.5f,-1));
        p.add(profile("Podcast", -2, -1,-0.5f,0,0.5f,1.5f,2,2,-1,-2,-3));
        p.add(profile("Rock", -3, 2,2,1,0,-0.5f,0.5f,1.5f,1,0.5f,1));
        p.add(profile("Jazz", -2, 1.5f,1,0.5f,0,0.5f,1,1,1.2f,0.5f,0));
        p.add(profile("Classical", -2, 1,0.5f,0,0,0,0.5f,1,1.5f,1,0.5f));
        p.add(profile("Electronic", -5, 4,3,1.5f,-0.5f,0,0.5f,1,1.5f,1,0.5f));
        p.add(profile("Cinema", -4, 3,2,1,0,0.5f,1,1.5f,0.5f,-1,-1));
        p.add(profile("Gaming", -3, 2,1,0,0,0.5f,1.5f,3,2,-0.5f,-1));
        p.add(profile("Night", -1, 0,0,0,0,0.5f,0.5f,0,-1,-2,-2));
        return Collections.unmodifiableList(p);
    }

    private static SoundProfile profile(String name, float preamp, float... g) {
        return new SoundProfile(name, preamp, F, g, Q);
    }
}
