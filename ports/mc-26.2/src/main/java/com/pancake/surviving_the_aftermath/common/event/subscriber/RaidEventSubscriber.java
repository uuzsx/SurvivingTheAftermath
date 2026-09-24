package com.pancake.surviving_the_aftermath.common.event.subscriber;


import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.common.event.AftermathEvent;
import com.pancake.surviving_the_aftermath.common.init.ModSoundEvents;
import com.pancake.surviving_the_aftermath.common.item.DiamondFlintAndSteelItem;
import com.pancake.surviving_the_aftermath.common.util.RaidPortal;
import com.pancake.surviving_the_aftermath.common.raid.NetherRaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;



@net.neoforged.fml.common.EventBusSubscriber
public class RaidEventSubscriber {
    @SubscribeEvent
    public static void onIgnition(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player
                && (event.getItemStack().is(Items.FLINT_AND_STEEL) || event.getItemStack().is(Items.FIRE_CHARGE))) {
            if (RaidPortal.isArena(level, event.getPos())) {
                player.sendSystemMessage(Component.translatable(DiamondFlintAndSteelItem.REQUIRED), true);
            }
        }
    }

    public static final String NETHER_RAID_START = "message.surviving_the_aftermath.nether_raid.start";
    public static final String NETHER_RAID_VICTORY = "message.surviving_the_aftermath.nether_raid.victory";
    @SubscribeEvent
    public static void netherRaid(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        BlockPos pos = entity.blockPosition();
        if (level instanceof ServerLevel serverLevel) {
            if (RaidPortal.isArena(serverLevel, pos)) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlock(BlockEvent.PortalSpawnEvent event) {
        LevelAccessor level = event.getLevel();
        if (level instanceof ServerLevel serverLevel && RaidPortal.isArena(serverLevel, event.getPos())) {
            // Vanilla fire (including fire charges/lava) cannot activate dungeon portals.
            // The diamond tool creates the portal directly after a successful encounter start.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRaidStart(AftermathEvent.Start event) {
        if (!(event.getAftermath() instanceof NetherRaid)) return;
        event.getPlayers().forEach(uuid -> {
            Player player = event.getLevel().getPlayerByUUID(uuid);
            if (player != null) player.sendSystemMessage(Component.translatable(NETHER_RAID_START));
        });
        event.getLevel().playSound(null, event.getAftermath().getStartPos(),
                SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.NEUTRAL, 3.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onRaidVictory(AftermathEvent.Victory event) {
        if (!(event.getAftermath() instanceof NetherRaid)) return;
        event.getLevel().playSound(null, event.getAftermath().getStartPos(),
                ModSoundEvents.ORCHELIAS_VOX.get(), SoundSource.NEUTRAL, 3.0F, 1.0F);
    }

    @SubscribeEvent
    public static void joinRaid(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof MagmaCube magmaCube) {
            AftermathManager.getInstance().getAftermathMap().values().stream()
                    .filter(aftermath -> aftermath instanceof NetherRaid)
                    .map(aftermath -> (NetherRaid) aftermath)
                    .filter(raid -> raid.level == entity.level())
                    .forEach(raid -> { if (raid.join(entity)) raid.insertTag(magmaCube); });
        }
    }
}
