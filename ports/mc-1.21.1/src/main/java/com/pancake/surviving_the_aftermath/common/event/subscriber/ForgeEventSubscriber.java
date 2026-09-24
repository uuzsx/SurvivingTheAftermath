package com.pancake.surviving_the_aftermath.common.event.subscriber;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.api.IAftermath;
import com.pancake.surviving_the_aftermath.api.ITracker;
import com.pancake.surviving_the_aftermath.common.capability.AftermathCap;
import com.pancake.surviving_the_aftermath.common.config.AftermathConfig;
import com.pancake.surviving_the_aftermath.common.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent.CreateSpawnPosition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;


@EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID, bus = Bus.GAME)
public class ForgeEventSubscriber {
	@SubscribeEvent
	public static void changeSpawn(CreateSpawnPosition event) {
		if (event.getLevel() instanceof ServerLevel level) {
			ServerLevelData settings = event.getSettings();
			BlockPos pos = level.findNearestMapStructure(ModTags.NETHER_RAID,
					settings.getSpawnPos(), 100, false);
			if (pos != null && AftermathConfig.enableSpawnPointStructure.get()) {
				settings.setSpawn(new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()), pos.getZ()), 0);
				event.setCanceled(true);
			}
		}
	}
	@SubscribeEvent
	public static void onTickLevelTick(LevelTickEvent.Post event) {
		Level level = event.getLevel();
		if (!level.isClientSide()) {
			AftermathCap.get(level).ifPresent(AftermathCap::tick);
		}
	}
	@SubscribeEvent
	public static void onLevel(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        AftermathManager.getInstance().clear();
    }

    @SubscribeEvent
    public static void clearStaleBattleState(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        var entity = event.getEntity();
        if (event.getLevel().isClientSide() || !entity.getPersistentData().hasUUID("raid_uuid")) return;
        var id = entity.getPersistentData().getUUID("raid_uuid");
        if (AftermathManager.getInstance().getAftermath(id).isEmpty())
            com.pancake.surviving_the_aftermath.common.util.BattleEntityState.clear(entity, id);
    }
}
