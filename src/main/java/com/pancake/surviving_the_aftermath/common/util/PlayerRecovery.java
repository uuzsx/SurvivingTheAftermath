package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class PlayerRecovery extends SavedData {
    private static final String KEY = "aftermath_spectator_recovery";
    private final Map<UUID, Integer> pending = new HashMap<>();
    private static PlayerRecovery get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedDataType<>(SurvivingTheAftermath.asResource(KEY), PlayerRecovery::new, CompoundTag.CODEC.xmap(PlayerRecovery::load, data -> data.save(new CompoundTag(), null))));
    }
    private static PlayerRecovery load(CompoundTag tag) {
        PlayerRecovery data = new PlayerRecovery();
        for (String key : tag.keySet()) {
            try { data.pending.put(UUID.fromString(key), tag.getIntOr(key, 0)); }
            catch (IllegalArgumentException ignored) { SurvivingTheAftermath.LOGGER.warn("Invalid pending player recovery id {}", key); }
        }
        return data;
    }
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        pending.forEach((id, mode) -> tag.putInt(id.toString(), mode));
        return tag;
    }
    public static void mark(ServerPlayer player, UUID battle, GameType originalMode) {
        CompoundTag persistent = player.getPersistentData().getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        CompoundTag recovery = new CompoundTag();
        recovery.store("battle", net.minecraft.core.UUIDUtil.CODEC, battle);
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
        PlayerRecovery data = get(player.level().getServer());
        CompoundTag persistent = player.getPersistentData().getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        CompoundTag marker = persistent.getCompoundOrEmpty(KEY);
        Integer mode = data.pending.remove(player.getUUID());
        if (mode == null && marker.read("battle", net.minecraft.core.UUIDUtil.CODEC).isPresent()
                && AftermathManager.getInstance().getAftermath(marker.read("battle", net.minecraft.core.UUIDUtil.CODEC).orElseThrow()).isEmpty()) mode = marker.getIntOr("mode", 0);
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
