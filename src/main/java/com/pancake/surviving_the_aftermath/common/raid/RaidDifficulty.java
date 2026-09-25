package com.pancake.surviving_the_aftermath.common.raid;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Chosen by the activating tool, independently of the world's difficulty. */
public enum RaidDifficulty implements StringRepresentable {
    EASY("easy", "easy", 5), NORMAL("normal", "common", 9), HARD("hard", "hard", 13);

    public static final Codec<RaidDifficulty> CODEC = StringRepresentable.fromEnum(RaidDifficulty::values);
    private final String id;
    private final String moduleName;
    private final int waves;

    RaidDifficulty(String id, String moduleName, int waves) {
        this.id = id;
        this.moduleName = moduleName;
        this.waves = waves;
    }

    @Override public String getSerializedName() { return id; }
    public String moduleName() { return moduleName; }
    public int waves() { return waves; }
    public String translationKey() { return "difficulty.surviving_the_aftermath." + id; }
}
