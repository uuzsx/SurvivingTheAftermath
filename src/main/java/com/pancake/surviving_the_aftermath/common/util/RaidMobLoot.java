package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.common.raid.BaseRaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

@EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class RaidMobLoot {
    // Deliberately persists after encounter cleanup: escaped/unloaded dungeon mobs must not become loot farms.
    private static final String NO_LOOT = "aftermath_no_loot";

    public static void mark(Mob mob) {
        mob.getPersistentData().putBoolean(NO_LOOT, true);
        mob.setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0);
    }

    public static boolean isDungeonMob(Entity entity) {
        return entity instanceof Mob && (entity.getPersistentData().getBoolean(NO_LOOT)
                || "enemies".equals(entity.getPersistentData().getString("raid")));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void loaded(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && isDungeonMob(event.getEntity())) mark((Mob) event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if (isDungeonMob(event.getEntity())) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void experience(LivingExperienceDropEvent event) {
        if (isDungeonMob(event.getEntity())) {
            event.setDroppedExperience(0);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void split(MobSplitEvent event) {
        if (!isDungeonMob(event.getParent())) return;
        var raidId = event.getParent().getPersistentData().hasUUID("raid_uuid") ? java.util.Optional.of(event.getParent().getPersistentData().getUUID("raid_uuid")) : java.util.Optional.<java.util.UUID>empty();
        for (Mob child : event.getChildren()) {
            if (child == null) continue;
            mark(child);
            com.pancake.surviving_the_aftermath.common.raid.RaidCombat.inheritEffects(event.getParent(), child);
            raidId.flatMap(AftermathManager.getInstance()::getAftermath).ifPresent(encounter -> {
                if (encounter instanceof BaseRaid raid && raid.join(child)) raid.insertTag(child);
            });
        }
    }
}
