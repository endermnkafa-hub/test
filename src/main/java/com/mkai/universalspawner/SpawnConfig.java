package com.mkai.universalspawner;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SpawnConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue NORMAL_CHANCE;
    public static final ForgeConfigSpec.DoubleValue MAJOR_CHANCE;
    public static final ForgeConfigSpec.IntValue CHECK_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue SPAWN_RADIUS_MIN;
    public static final ForgeConfigSpec.IntValue SPAWN_RADIUS_MAX;
    public static final ForgeConfigSpec.IntValue MAX_NEARBY;
    public static final ForgeConfigSpec.BooleanValue ENABLED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Universal NPC Spawner - Mine Piece").push("spawn");
        ENABLED = b.comment("Enable natural Mine Piece character spawning.")
                .define("enabled", true);
        NORMAL_CHANCE = b.comment("Chance per spawn check for a normal candidate (0.0 - 1.0).")
                .defineInRange("normalChance", 0.22D, 0.0D, 1.0D);
        MAJOR_CHANCE = b.comment("Extra chance gate for rare main characters such as Luffy/Zoro/Sanji.")
                .defineInRange("majorCharacterChance", 0.04D, 0.0D, 1.0D);
        CHECK_INTERVAL_SECONDS = b.comment("Seconds between spawn checks per player.")
                .defineInRange("checkIntervalSeconds", 2, 1, 60);
        SPAWN_RADIUS_MIN = b.comment("Minimum distance from player for natural NPC spawn.")
                .defineInRange("radiusMin", 24, 8, 128);
        SPAWN_RADIUS_MAX = b.comment("Maximum distance from player for natural NPC spawn.")
                .defineInRange("radiusMax", 64, 16, 192);
        MAX_NEARBY = b.comment("Maximum Mine Piece NPCs within 64 blocks of a player.")
                .defineInRange("maxNearby", 10, 1, 100);
        b.pop();

        SPEC = b.build();
    }

    private SpawnConfig() {}
}
