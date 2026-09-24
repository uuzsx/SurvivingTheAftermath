package com.pancake.surviving_the_aftermath.common.event.tracker;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.api.ITracker;
import com.pancake.surviving_the_aftermath.api.base.BaseTracker;
import com.pancake.surviving_the_aftermath.common.config.AftermathConfig;
import com.pancake.surviving_the_aftermath.common.init.ModAftermathModule;
import com.pancake.surviving_the_aftermath.common.util.BattleEntityState;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class MobBattleTracker extends BaseTracker {
    @SubscribeEvent public void onLivingAddHighlight(LivingEvent.LivingTickEvent event) {
        Entity entity = event.getEntity();
        if (!entity.level().isClientSide && AftermathConfig.enableMobBattleTrackerHighlight.get()
                && BattleEntityState.belongsTo(entity, uuid)) BattleEntityState.highlight(entity);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void death(LivingDeathEvent event) { remove(event.getEntity()); }
    @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) remove(entity);
    }
    private void remove(Entity entity) {
        if (entity.level().isClientSide || !BattleEntityState.belongsTo(entity, uuid)) return;
        manager.getAftermath(uuid).ifPresent(a -> a.getEnemies().remove(entity.getUUID()));
    }
    @Override public Codec<? extends ITracker> codec() { return Codec.unit(MobBattleTracker::new); }
    @Override public ITracker type() { return ModAftermathModule.MOB_BATTLE_TRACKER.get(); }
}
