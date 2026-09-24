package com.pancake.surviving_the_aftermath.common.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import java.util.UUID;

public final class BattleEntityState {
    private static final String PREVIOUS_GLOW = "aftermath_previous_glow";
    public static boolean belongsTo(Entity entity, UUID id) {
        CompoundTag tag = entity.getPersistentData();
        return id != null && tag.read("raid_uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && id.equals(tag.read("raid_uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
    }
    public static void highlight(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        if (!tag.contains(PREVIOUS_GLOW)) tag.putBoolean(PREVIOUS_GLOW, entity.hasGlowingTag());
        entity.setGlowingTag(true);
    }
    public static void clear(Entity entity, UUID id) {
        if (!belongsTo(entity, id)) return;
        CompoundTag tag = entity.getPersistentData();
        entity.setGlowingTag(tag.getBooleanOr(PREVIOUS_GLOW, false));
        tag.remove(PREVIOUS_GLOW);
        tag.remove("restricted_range");
        tag.remove("nether_raid");
        tag.remove("raid");
        tag.remove("raid_uuid");
    }
}
