package com.yigit.buds2blescanner.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry of known protocol families. TX remains disabled until a real TX payload is verified. */
public final class Buds2CommandRegistry {
    private final Map<String, CommandSpec> commands = new LinkedHashMap<>();

    public Buds2CommandRegistry() {
        register("STATUS_UPDATED", 0x60, false);
        register("EXTENDED_STATUS_UPDATED", 0x61, false);
        register("CONNECTION_UPDATED", 0x62, false);
        register("VERSION_INFO", 0x63, false);
        register("METERING_REPORT", 0x41, false);
        register("NOISE_CONTROLS_UPDATE", 0x78, false);
        register("NOISE_CONTROLS", 0x79, false);
        register("AMBIENT_MODE_UPDATED", 0x81, false);
        register("AMBIENT_VOLUME", 0x84, false);
        register("EQUALIZER", 0x86, false);
        register("GAME_MODE", 0x87, false);
        register("TOUCH_UPDATED", 0x91, false);
        register("FIND_MY_EARBUDS_START", 0xA0, false);
        register("FIND_MY_EARBUDS_STOP", 0xA1, false);
        register("SELF_TEST", 0xAB, false);

        // Firmware evidence only. These names are not marked TX-verified.
        register("SET_EQUALIZER_MODE", 0x86, false);
        register("SET_AMBIENT_MODE", 0x80, false);
        register("SET_TOUCHPAD_OPTION", 0x92, false);
        register("SET_TOUCH_CONFIG", 0x91, false);
        register("SET_SIDETONE", 0x8B, false);
        register("SET_ANC_WITH_ONE_EARBUD", 0x6F, false);
        register("SET_CUSTOM_AMBIENT", 0x82, false);
        register("SET_DETECT_CONVERSATION", 0x7B, false);
        register("SET_DETECT_CONVERSATION_DURATION", 0x7C, false);
        register("SET_SPATIAL_AUDIO", 0x7D, false);
    }

    private void register(String name, int id, boolean txVerified) {
        commands.put(name, new CommandSpec(name, id, txVerified));
    }
    public CommandSpec find(String name) { return commands.get(name); }
    public Map<String, CommandSpec> all() { return Collections.unmodifiableMap(commands); }

    public static final class CommandSpec {
        public final String name;
        public final int messageId;
        public final boolean txVerified;
        CommandSpec(String name, int messageId, boolean txVerified) {
            this.name = name; this.messageId = messageId; this.txVerified = txVerified;
        }
    }
}
