package com.yigit.buds2blescanner.pro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Read-only AYF1 firmware knowledge base for Galaxy Buds2 SM-R177. */
public final class Ayf1FirmwareProfile {
    public static final String MODEL = "SM-R177";
    public static final String FIRMWARE = "R177XXU0AYF1";
    public static final String BUILD_DATE = "Jun 10 2025 04:31:53";
    public static final long FLASH_BASE = 0x28000000L;
    public static final long FLASH_SIZE = 0x800000L;
    public static final long OTA_CODE_OFFSET = 0x18000L;
    public static final long OTA_REMAP_OFFSET = 0x320000L;
    public static final long IMAGE_CRC32 = 0xF589A6CBL;

    private static final Map<String, String> FEATURES;
    private static final Map<String, String> MESSAGES;

    static {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("ANC", "TwaAudio_ANCSoundOn/TwaAudio_ANCSoundOff/TwaAudioProd_ANCLeakdetect");
        f.put("AMBIENT", "TwaAudio_AmbientSoundOn/TwaAudio_AmbientSoundOff/TwaAudio_AmbientSoundSetLevel");
        f.put("EQ", "TwaAudio_EQSet");
        f.put("TOUCH", "TwfSession_SetTouchControl/TwfSession_GetTouchControl");
        f.put("FIND_MY_EARBUDS", "TwfFindMyEarbuds_Start/TwfFindMyEarbuds_Stop");
        f.put("SPATIAL_AUDIO", "TWU_MSG_ID_SET_SPATIAL_AUDIO_SYNC");
        f.put("DIAGNOSTICS", "TWU_MSG_ID_SELF_TEST/TWU_MSG_ID_EARBUDS_FIT_TEST");
        f.put("CONVERSATION_DETECTION", "TWU_MSG_ID_SET_DETECT_CONVERSATION");
        f.put("SIDETONE", "TWU_MSG_ID_SET_SIDETONE");
        f.put("ONE_EARBUD_ANC", "TWU_MSG_ID_SET_ANC_WITH_ONE_EARBUD");
        FEATURES = Collections.unmodifiableMap(f);

        Map<String, String> m = new LinkedHashMap<>();
        m.put("SET_EQUALIZER_MODE", "TWU_MSG_ID_SET_EQUALIZER_MODE");
        m.put("SET_AMBIENT_MODE", "TWU_MSG_ID_SET_AMBIENT_MODE");
        m.put("SET_NOISE_CONTROLS", "TWU_MSG_ID_SET_NOISE_CONTROLS");
        m.put("SET_TOUCHPAD_OPTION", "TWU_MSG_ID_SET_TOUCHPAD_OPTION");
        m.put("SET_TOUCH_CONFIG", "TWU_MSG_ID_SET_TOUCH_CONFIG");
        m.put("AMBIENT_VOLUME", "TWU_MSG_ID_AMBIENT_VOLUME");
        m.put("ANC_LEVEL", "TWU_MSG_ID_ACTIVE_NOISE_CANCELING_LEVEL");
        m.put("CUSTOM_AMBIENT", "TWU_MSG_ID_SET_CUSTOM_AMBIENT");
        m.put("SIDETONE", "TWU_MSG_ID_SET_SIDETONE");
        m.put("ANC_WITH_ONE_EARBUD", "TWU_MSG_ID_SET_ANC_WITH_ONE_EARBUD");
        m.put("FIND_MY_EARBUDS_START", "TWU_MSG_ID_FIND_MY_EARBUDS_START");
        m.put("FIND_MY_EARBUDS_STOP", "TWU_MSG_ID_FIND_MY_EARBUDS_STOP");
        m.put("SELF_TEST", "TWU_MSG_ID_SELF_TEST");
        m.put("FIT_TEST", "TWU_MSG_ID_EARBUDS_FIT_TEST");
        m.put("SPATIAL_AUDIO_SYNC", "TWU_MSG_ID_SET_SPATIAL_AUDIO_SYNC");
        MESSAGES = Collections.unmodifiableMap(m);
    }

    private Ayf1FirmwareProfile() {}

    public static Map<String, String> features() { return FEATURES; }
    public static Map<String, String> messages() { return MESSAGES; }
    public static Set<String> featureNames() { return FEATURES.keySet(); }

    /** Never treat firmware strings as proof of a TX wire payload. */
    public static boolean isKnownFirmware(String model, String version) {
        return MODEL.equalsIgnoreCase(model) && FIRMWARE.equalsIgnoreCase(version);
    }
}
