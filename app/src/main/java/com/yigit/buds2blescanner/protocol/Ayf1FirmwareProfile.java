package com.yigit.buds2blescanner.protocol;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only knowledge profile extracted from the user's SM-R177 AYF1 firmware. */
public final class Ayf1FirmwareProfile {
    public static final String MODEL = "SM-R177";
    public static final String PRODUCT = "Galaxy Buds2";
    public static final String VERSION = "R177XXU0AYF1";
    public static final String BUILD_DATE = "Jun 10 2025";
    public static final String REV_INFO = ":best2500p_ibrt_anc";
    public static final long FLASH_BASE = 0x28000000L;
    public static final long FLASH_SIZE = 0x800000L;
    public static final long OTA_CODE_OFFSET = 0x18000L;
    public static final long OTA_REMAP_OFFSET = 0x320000L;
    public static final long IMAGE_CRC32 = 0xF589A6CBL;

    private static final Map<String, List<String>> FEATURES;
    private static final Map<Integer, String> FIRMWARE_MESSAGES;

    static {
        Map<String, List<String>> f = new LinkedHashMap<>();
        f.put("ANC", Arrays.asList("TwaAudio_ANCSoundOn", "TwaAudio_ANCSoundOff", "TwaAudioProd_ANCSetFBGain", "TwaAudioProd_ANCSetFFGain", "TwaAudioProd_ANCLeakdetect"));
        f.put("AMBIENT", Arrays.asList("TwaAudio_AmbientSoundOn", "TwaAudio_AmbientSoundOff", "TwaAudio_AmbientSoundSetLevel", "TwaAudio_AmbientSoundSetTone"));
        f.put("EQ", Arrays.asList("TwaAudio_EQSet"));
        f.put("TOUCH", Arrays.asList("TwfSession_SetTouchControl", "TwfSession_GetTouchControl", "TwfSession_UpdateTouchControl", "TwfSession_SetTouchLockTimer"));
        f.put("FIND_MY_EARBUDS", Arrays.asList("TwfFindMyEarbuds_Start", "TwfFindMyEarbuds_Stop", "TwfFindMyEarbuds_SetVolume"));
        f.put("SPATIAL_AUDIO", Arrays.asList("TWU_MSG_ID_SET_SPATIAL_AUDIO_SYNC", "TWU_MSG_ID_AUDIO_SPATIAL_CONTROL"));
        f.put("DIAGNOSTICS", Arrays.asList("TWU_MSG_ID_SELF_TEST", "TWU_MSG_ID_EARBUDS_FIT_TEST", "TwaAudioProd_ANCLeakdetect", "TwaAudioProd_LoopbackCheckMicVP"));
        f.put("CONVERSATION", Arrays.asList("TWU_MSG_ID_SET_DETECT_CONVERSATION", "TWU_MSG_ID_SET_DETECT_CONVERSATION_DURATION"));
        f.put("ADVANCED_AUDIO", Arrays.asList("TWU_MSG_ID_SET_SIDETONE", "TWU_MSG_ID_SET_CUSTOM_AMBIENT", "TWU_MSG_ID_SET_ANC_WITH_ONE_EARBUD"));
        FEATURES = Collections.unmodifiableMap(f);

        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0x60, "STATUS_UPDATED");
        m.put(0x61, "EXTENDED_STATUS_UPDATED");
        m.put(0x63, "VERSION_INFO");
        m.put(0x78, "NOISE_CONTROLS_UPDATE");
        m.put(0x79, "NOISE_CONTROLS");
        m.put(0x81, "AMBIENT_MODE_UPDATED");
        m.put(0x84, "AMBIENT_VOLUME");
        m.put(0x86, "EQUALIZER");
        m.put(0x91, "TOUCH_UPDATED");
        m.put(0xA0, "FIND_MY_EARBUDS_START");
        m.put(0xA1, "FIND_MY_EARBUDS_STOP");
        m.put(0xAB, "SELF_TEST");
        FIRMWARE_MESSAGES = Collections.unmodifiableMap(m);
    }

    private Ayf1FirmwareProfile() {}
    public static Map<String, List<String>> features() { return FEATURES; }
    public static Map<Integer, String> firmwareMessages() { return FIRMWARE_MESSAGES; }
    public static boolean supportsFeature(String name) { return FEATURES.containsKey(name); }
    public static String summary() { return PRODUCT + " / " + MODEL + " / " + VERSION + " | build=" + BUILD_DATE + " | rev=" + REV_INFO; }
}
