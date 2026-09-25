package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.common.raid.BaseRaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;

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

    /** Called for the actual children of a splitting slime, never for nearby wild mobs. */
    public static void inherit(Mob parent, Mob child) {
        if (!isDungeonMob(parent)) return;
        mark(child);
        com.pancake.surviving_the_aftermath.common.raid.RaidCombat.inheritEffects(parent, child);
        if (parent.getPersistentData().hasUUID("raid_uuid")) {
            AftermathManager.getInstance().getAftermath(parent.getPersistentData().getUUID("raid_uuid")).ifPresent(encounter -> {
                if (encounter instanceof BaseRaid raid && raid.join(child)) raid.insertTag(child);
            });
        }
    }
}
