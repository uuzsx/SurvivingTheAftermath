package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.ModStructures;
import com.pancake.surviving_the_aftermath.common.network.AftermathNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** One spatial playback per building, with a lifetime independent of reward dispensing. */
@EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class RaidMusic {
    public static final int RADIUS = 48;
    // ceil(269.598956916 seconds * 20), from the bundled Ogg's sample count.
    public static final int DURATION_TICKS = 5392;
    private static final Map<ServerLevel, Map<BlockPos, Playback>> PLAYING = new IdentityHashMap<>();

    private record Playback(UUID id, long started, Map<UUID, ServerPlayer> listeners) {}

    public static BlockPos center(ServerLevel level, BlockPos origin) {
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.NETHER_RAID);
        var start = level.structureManager().getStructureAt(origin, structure);
        return start.isValid() ? start.getBoundingBox().getCenter() : origin.immutable();
    }

    public static Optional<UUID> playbackId(ServerLevel level, BlockPos origin) {
        var playback = PLAYING.getOrDefault(level, Map.of()).get(center(level, origin));
        return playback == null ? Optional.empty() : Optional.of(playback.id());
    }

    public static void start(ServerLevel level, BlockPos origin, UUID encounterId) {
        BlockPos center = center(level, origin);
        var existing = PLAYING.getOrDefault(level, Map.of()).get(center);
        if (existing != null && existing.id().equals(encounterId)) return;
        stop(level, origin);
        var listeners = nearby(level, center);
        if (listeners.isEmpty()) return;
        var playback = new Playback(encounterId, level.getGameTime(), new HashMap<>());
        PLAYING.computeIfAbsent(level, ignored -> new HashMap<>()).put(center, playback);
        sync(level, center, playback, listeners);
    }

    public static void stop(ServerLevel level, BlockPos origin) {
        var sessions = PLAYING.get(level);
        if (sessions == null) return;
        var playback = sessions.remove(center(level, origin));
        if (playback != null) stopListeners(playback);
        if (sessions.isEmpty()) PLAYING.remove(level);
    }

    private static List<ServerPlayer> nearby(ServerLevel level, BlockPos center) {
        return level.players().stream().filter(player -> !player.isRemoved()
                && player.distanceToSqr(Vec3.atCenterOf(center)) < RADIUS * RADIUS).toList();
    }

    private static void sync(ServerLevel level, BlockPos center, Playback playback, List<ServerPlayer> nearby) {
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : nearby) {
            present.add(player.getUUID());
            if (playback.listeners().putIfAbsent(player.getUUID(), player) == null) {
                AftermathNetwork.playMusic(player, playback.id(), center, (int) (level.getGameTime() - playback.started()));
            }
        }
        playback.listeners().entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) return false;
            AftermathNetwork.stopMusic(entry.getValue(), playback.id());
            return true;
        });
    }

    private static void stopListeners(Playback playback) {
        playback.listeners().values().forEach(player -> AftermathNetwork.stopMusic(player, playback.id()));
        playback.listeners().clear();
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) tick(level);
    }

    public static void tick(ServerLevel level) {
        var sessions = PLAYING.get(level);
        if (sessions == null) return;
        sessions.entrySet().removeIf(entry -> {
            var playback = entry.getValue();
            var listeners = nearby(level, entry.getKey());
            if (listeners.isEmpty() || level.getGameTime() - playback.started() >= DURATION_TICKS) {
                stopListeners(playback);
                return true; // No dormant session remains that could restart when players return.
            }
            sync(level, entry.getKey(), playback, listeners);
            return false;
        });
        if (sessions.isEmpty()) PLAYING.remove(level);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var sessions = PLAYING.remove(level);
            if (sessions != null) sessions.values().forEach(RaidMusic::stopListeners);
        }
    }
}
