package com.pancake.surviving_the_aftermath.common.event.tracker;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.api.ITracker;
import com.pancake.surviving_the_aftermath.api.base.BaseTracker;
import com.pancake.surviving_the_aftermath.common.config.AftermathConfig;
import com.pancake.surviving_the_aftermath.common.init.ModAftermathModule;
import com.pancake.surviving_the_aftermath.common.raid.BaseRaid;
import com.pancake.surviving_the_aftermath.common.util.BattleEntityState;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class RaidMobBattleTracker extends BaseTracker {
    @SubscribeEvent public void onLivingRestrictedRange(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide() || !BattleEntityState.belongsTo(mob, uuid)) return;
        manager.getAftermath(uuid).filter(a -> a instanceof BaseRaid).map(a -> (BaseRaid) a).ifPresent(raid -> {
            if (AftermathConfig.enableMobBattleTrackerRestrictedRange.get()
                    && mob.distanceToSqr(Vec3.atCenterOf(raid.getStartPos())) > (double) raid.getRadius() * raid.getRadius()) {
                mob.getPersistentData().store("restricted_range", net.minecraft.core.BlockPos.CODEC, raid.getStartPos());
            } else mob.getPersistentData().remove("restricted_range");
        });
    }
    @Override public Codec<? extends ITracker> codec() { return com.mojang.serialization.MapCodec.unit(RaidMobBattleTracker::new).codec(); }
    @Override public ITracker type() { return ModAftermathModule.RAID_MOB_BATTLE_TRACKER.get(); }
}
