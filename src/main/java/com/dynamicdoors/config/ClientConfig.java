package com.dynamicdoors.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** CLIENT config (config/dynamicdoors-client.toml). Changes apply without a restart. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ANIMATE_DOORS;
    public static final ForgeConfigSpec.BooleanValue ANIMATE_TRAPDOORS;
    public static final ForgeConfigSpec.BooleanValue ANIMATE_FENCE_GATES;
    public static final ForgeConfigSpec.IntValue DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue ROUND_DOOR_DURATION_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Swing animations for vanilla and modded doors, trapdoors and fence gates.",
                "Purely visual: collision still changes the moment the block is toggled.").push("animations");
        ANIMATE_DOORS = b.comment("Animate doors (anything extending the vanilla door).")
                .define("doors", true);
        ANIMATE_TRAPDOORS = b.comment("Animate trapdoors.")
                .define("trapdoors", true);
        ANIMATE_FENCE_GATES = b.comment("Animate fence gates.")
                .define("fenceGates", true);
        DURATION_TICKS = b.comment("Length of a full swing in ticks (20 ticks = 1 second).")
                .defineInRange("durationTicks", 7, 1, 40);
        ROUND_DOOR_DURATION_TICKS = b.comment("Length of a full swing of the 3x3 round doors in ticks.")
                .defineInRange("roundDoorDurationTicks", 10, 1, 60);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {
    }

    /** Config values throw before the file is loaded; fall back to the defaults. */
    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }
}
