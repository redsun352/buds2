package com.yigit.buds2blescanner.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of commands whose wire format is known/verified.
 * Unknown commands are intentionally rejected instead of sending guessed bytes.
 */
public final class Buds2CommandRegistry {
    private final Map<String, CommandSpec> commands = new LinkedHashMap<>();

    public Buds2CommandRegistry() {
        // Read/notification message IDs observed in captures.
        register("STATUS_UPDATED", 0x60, false);
        register("EXTENDED_STATUS_UPDATED", 0x61, false);
        register("CONNECTION_UPDATED", 0x62, false);
        register("VERSION_INFO", 0x63, false);
        register("METERING_REPORT", 0x41, false);
        register("NOISE_CONTROLS_UPDATE", 0x78, false);
        register("NOISE_CONTROLS", 0x79, false);
        register("AMBIENT_MODE_UPDATED", 0x81, false);
        register("EQUALIZER", 0x86, false);
        register("GAME_MODE", 0x87, false);
        register("TOUCH_UPDATED", 0x91, false);
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
            this.name = name;
            this.messageId = messageId;
            this.txVerified = txVerified;
        }
    }
}
