package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class PlayerRecovery extends SavedData {
    private static final String KEY = "aftermath_spectator_recovery";
    private final Map<UUID, Integer> pending = new HashMap<>();
    private static PlayerRecovery get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlayerRecovery::load, PlayerRecovery::new, KEY);
    }
    private static PlayerRecovery load(CompoundTag tag) {
        PlayerRecovery data = new PlayerRecovery();
        for (String key : tag.getAllKeys()) {
            try { data.pending.put(UUID.fromString(key), tag.getInt(key)); }
            catch (IllegalArgumentException ignored) { SurvivingTheAftermath.LOGGER.warn("Invalid pending player recovery id {}", key); }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        pending.forEach((id, mode) -> tag.putInt(id.toString(), mode));
        return tag;
    }
    public static void mark(ServerPlayer player, UUID battle, GameType originalMode) {
        CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag recovery = new CompoundTag();
        recovery.putUUID("battle", battle);
        recovery.putInt("mode", originalMode.getId());
        persistent.put(KEY, recovery);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
    }
    public static void restore(MinecraftServer server, UUID playerId, GameType mode) {
        PlayerRecovery data = get(server);
        data.pending.put(playerId, mode.getId());
        data.setDirty();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) apply(player);
    }
    private static void apply(ServerPlayer player) {
        PlayerRecovery data = get(player.server);
        CompoundTag persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag marker = persistent.getCompound(KEY);
        Integer mode = data.pending.remove(player.getUUID());
        if (mode == null && marker.hasUUID("battle")
                && AftermathManager.getInstance().getAftermath(marker.getUUID("battle")).isEmpty()) mode = marker.getInt("mode");
        if (mode != null) {
            player.setCamera(player);
            player.setGameMode(GameType.byId(mode));
            persistent.remove(KEY);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
            data.setDirty();
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) apply(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) apply(player);
    }
}
