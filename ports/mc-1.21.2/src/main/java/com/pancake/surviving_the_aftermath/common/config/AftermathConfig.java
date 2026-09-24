package com.pancake.surviving_the_aftermath.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class AftermathConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public final static ModConfigSpec.BooleanValue enableMobBattleTrackerHighlight;
    public final static ModConfigSpec.BooleanValue enableMobBattleTrackerRestrictedRange;
    public final static ModConfigSpec.BooleanValue enableSpawnPointStructure;


    static {
        BUILDER.comment("Config");

        enableMobBattleTrackerHighlight = BUILDER.comment("Enable MobBattleTracker Highlight")
                .define("enableMobBattleTrackerHighlight", true);

        enableMobBattleTrackerRestrictedRange = BUILDER.comment("Enable MobBattleTracker RestrictedRange")
                .define("enableMobBattleTrackerRestrictedRange", true);

        enableSpawnPointStructure = BUILDER.comment("Enable SpawnPoint Structure")
                .define("enableSpawnPointStructure", true);
    }

    static {
        SPEC = BUILDER.build();
    }
}
