package com.pancake.surviving_the_aftermath.common.capability;

import com.google.common.collect.Sets;
import com.pancake.surviving_the_aftermath.common.init.ModCapability;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import java.util.Set;

public class AftermathStageCap {
    private final Set<String> stages = Sets.newHashSet();

    public ListTag serializeNBT() {
        ListTag listTag = new ListTag();
        for (String stage : stages) {
            listTag.add(StringTag.valueOf(stage));
        }
        return listTag;
    }

    public void deserializeNBT(ListTag nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            stages.add(nbt.getString(i));
        }
    }

    public Set<String> getStages() {
        return stages;
    }

    public static void addStage(Player player, String stage) {
        AftermathStageCap.get(player).ifPresent(stageCap -> stageCap.getStages().add(stage));
    }
    public static void addStage(Level level, String stage) {
        AftermathStageCap.get(level).ifPresent(stageCap -> stageCap.getStages().add(stage));
    }

    public static void removeStage(Player player, String stage) {
        AftermathStageCap.get(player).ifPresent(stageCap -> stageCap.getStages().remove(stage));
    }
    public static void removeStage(Level level, String stage) {
        AftermathStageCap.get(level).ifPresent(stageCap -> stageCap.getStages().remove(stage));
    }

    public static boolean hasStage(Player player, String stage) {
        return AftermathStageCap.get(player).map(stageCap -> stageCap.getStages().contains(stage)).orElse(false);
    }
    public static boolean hasStage(Level level, String stage) {
        return AftermathStageCap.get(level).map(stageCap -> stageCap.getStages().contains(stage)).orElse(false);
    }

    public static Optional<AftermathStageCap> get(Player player) {
        return Optional.of(player.getData(ModCapability.STAGE_CAP));
    }
    public static Optional<AftermathStageCap> get(Level level) {
        return Optional.of(level.getData(ModCapability.STAGE_CAP));
    }

}
