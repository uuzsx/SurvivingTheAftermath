package com.pancake.surviving_the_aftermath.common.event.tracker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pancake.surviving_the_aftermath.api.*;
import com.pancake.surviving_the_aftermath.api.base.BaseAftermath;
import com.pancake.surviving_the_aftermath.api.base.BaseTracker;
import com.pancake.surviving_the_aftermath.common.event.AftermathEvent;
import com.pancake.surviving_the_aftermath.common.init.ModAftermathModule;
import com.pancake.surviving_the_aftermath.common.init.ModMobEffects;
import com.pancake.surviving_the_aftermath.common.raid.api.IRaid;
import com.pancake.surviving_the_aftermath.common.util.CodecUtils;
import com.pancake.surviving_the_aftermath.common.util.PlayerRecovery;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.*;

public class RaidPlayerBattleTracker extends BaseTracker {
    public static final Codec<RaidPlayerBattleTracker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.setOf(CodecUtils.UUID_CODEC).fieldOf("players").forGetter(RaidPlayerBattleTracker::getPlayers),
            Codec.unboundedMap(CodecUtils.UUID_CODEC, Codec.INT).fieldOf("death_map").forGetter(RaidPlayerBattleTracker::getDeathMap),
            Codec.unboundedMap(CodecUtils.UUID_CODEC, Codec.LONG).fieldOf("escape_map").forGetter(RaidPlayerBattleTracker::getEscapeMap),
            Codec.unboundedMap(CodecUtils.UUID_CODEC, CodecUtils.setOf(CodecUtils.UUID_CODEC)).fieldOf("spectator_map").forGetter(RaidPlayerBattleTracker::getSpectatorMap),
            Codec.unboundedMap(CodecUtils.UUID_CODEC, Codec.INT).optionalFieldOf("original_modes", Map.of()).forGetter(t -> t.originalModes),
            CodecUtils.setOf(CodecUtils.UUID_CODEC).optionalFieldOf("pending_respawns", Set.of()).forGetter(t -> t.pendingRespawns)
    ).apply(instance, RaidPlayerBattleTracker::new));
    public static final String IDENTIFIER = "player_battle_tracker";
    public static final String PLAYER_BATTLE_PERSONAL_FAIL = "message.surviving_the_aftermath.tracker.personal_fail";
    public static final String PLAYER_BATTLE_ESCAPE = "message.surviving_the_aftermath.tracker.escape";
    private static final int MAX_DEATH_COUNT = 3;
    private final Set<UUID> players;
    private final Map<UUID, Integer> deathMap;
    private final Map<UUID, Long> escapeMap;
    private final Map<UUID, Set<UUID>> spectatorMap;
    private final Map<UUID, Integer> originalModes;
    private final Set<UUID> pendingRespawns;

    public RaidPlayerBattleTracker(Set<UUID> players, Map<UUID, Integer> deaths, Map<UUID, Long> escapes, Map<UUID, Set<UUID>> spectators) {
        this(players, deaths, escapes, spectators, Map.of());
    }
    public RaidPlayerBattleTracker(Set<UUID> players, Map<UUID, Integer> deaths, Map<UUID, Long> escapes,
                                   Map<UUID, Set<UUID>> spectators, Map<UUID, Integer> modes) {
        this(players, deaths, escapes, spectators, modes, Set.of());
    }
    public RaidPlayerBattleTracker(Set<UUID> players, Map<UUID, Integer> deaths, Map<UUID, Long> escapes,
                                   Map<UUID, Set<UUID>> spectators, Map<UUID, Integer> modes, Set<UUID> pendingRespawns) {
        this.players = new LinkedHashSet<>(players);
        deathMap = new HashMap<>(deaths);
        escapeMap = new HashMap<>(escapes);
        spectatorMap = new HashMap<>();
        spectators.forEach((id, watchers) -> spectatorMap.put(id, new HashSet<>(watchers)));
        originalModes = new HashMap<>(modes);
        this.pendingRespawns = new HashSet<>(pendingRespawns);
    }
    public RaidPlayerBattleTracker() { this(Set.of(), Map.of(), Map.of(), Map.of(), Map.of()); }

    @SubscribeEvent
    public void updatePlayer(AftermathEvent.Ongoing event) {
        if (!Objects.equals(uuid, event.getAftermath().getUUID())) return;
        for (UUID id : new HashSet<>(players)) {
            if (!event.getPlayers().contains(id)) {
                players.remove(id);
                ServerPlayer player = event.getLevel().getServer().getPlayerList().getPlayer(id);
                // A disconnect is not a death. Actual deaths are handled by LivingDeathEvent.
                if (player != null && player.isAlive() && !player.isSpectator())
                    escapeMap.putIfAbsent(id, event.getLevel().getGameTime());
            }
        }
        for (UUID id : event.getPlayers()) {
            players.add(id);
            escapeMap.remove(id);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        manager.getAftermath(uuid).filter(a -> a instanceof BaseAftermath).map(a -> (BaseAftermath) a).ifPresent(a -> {
            if (combatActive(a) && a.level == player.level() && a.getPlayers().contains(player.getUUID())) {
                deathMap.merge(player.getUUID(), 1, Integer::sum);
                pendingRespawns.add(player.getUUID());
                players.remove(player.getUUID());
                escapeMap.remove(player.getUUID());
            }
        });
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !pendingRespawns.remove(player.getUUID())) return;
        manager.getAftermath(uuid).filter(a -> a instanceof BaseAftermath && a instanceof IRaid).ifPresent(aftermath -> {
            BaseAftermath battle = (BaseAftermath) aftermath;
            if (!combatActive(battle)) { onEnd(battle.level); return; }
            List<ServerPlayer> survivors = players.stream().map(id -> battle.level.getServer().getPlayerList().getPlayer(id))
                    .filter(p -> p != null && p.level() == battle.level && p.isAlive() && !p.isSpectator() && p != player).toList();
            if (!survivors.isEmpty()) {
                player.sendSystemMessage(Component.translatable(PLAYER_BATTLE_PERSONAL_FAIL), true);
                setSpectator(player, survivors.get(battle.level.getRandom().nextInt(survivors.size())), battle.level);
                return;
            }
            if (deathMap.get(player.getUUID()) >= MAX_DEATH_COUNT || !spectatorMap.isEmpty()) {
                battle.lose();
                return;
            }
            // Keep the normal respawn location for a remaining attempt. Death is not an escape;
            // arena membership will be updated if the player returns to the encounter.
            escapeMap.remove(player.getUUID());
        });
    }

    @SubscribeEvent
    public void onPlayerEscape(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID playerId = player.getUUID();
        if (!escapeMap.containsKey(playerId)) return;
        manager.getAftermath(uuid).filter(a -> a instanceof BaseAftermath && a instanceof IRaid).ifPresent(aftermath -> {
            BaseAftermath battle = (BaseAftermath) aftermath;
            if (!combatActive(battle)) { onEnd(battle.level); return; }
            long time = battle.level.getGameTime() - escapeMap.get(playerId);
            double distance = player.level() == battle.level ? Math.sqrt(battle.getStartPos().distSqr(player.blockPosition())) : Double.POSITIVE_INFINITY;
            if (time > 20 * 5) {
                player.addEffect(new MobEffectInstance(ModMobEffects.COWARDICE, 45 * 60 * 20));
                if (distance > 120) {
                    player.addEffect(new MobEffectInstance(ModMobEffects.COWARDICE, 45 * 60 * 20, 1));
                    escapeMap.remove(playerId);
                    restorePlayerGameMode(battle.level);
                }
            } else player.sendSystemMessage(Component.translatable(PLAYER_BATTLE_ESCAPE, 20 * 5 - time), true);
        });
    }

    private static boolean combatActive(IAftermath battle) {
        return switch (battle.getState()) {
            case START, READY, ONGOING -> true;
            default -> false;
        };
    }

    @SubscribeEvent
    public void onVictory(AftermathEvent.Victory event) {
        if (Objects.equals(uuid, event.getAftermath().getUUID())) onEnd(event.getLevel());
    }

    @SubscribeEvent
    public void onCelebrating(AftermathEvent.Celebrating event) {
        // Also finish combat state restored from an older save already dispensing rewards.
        if (Objects.equals(uuid, event.getAftermath().getUUID())) onEnd(event.getLevel());
    }

    private void setSpectator(ServerPlayer player, ServerPlayer target, ServerLevel level) {
        originalModes.putIfAbsent(player.getUUID(), player.gameMode.getGameModeForPlayer().getId());
        PlayerRecovery.mark(player, uuid, GameType.byId(originalModes.get(player.getUUID())));
        Set<UUID> moving = spectatorMap.remove(player.getUUID());
        Set<UUID> watchers = spectatorMap.computeIfAbsent(target.getUUID(), id -> new HashSet<>());
        if (moving != null) watchers.addAll(moving);
        watchers.add(player.getUUID());
        for (UUID id : watchers) {
            ServerPlayer watcher = level.getServer().getPlayerList().getPlayer(id);
            if (watcher != null) {
                if (watcher.level() != level) watcher.teleportTo(level, target.getX(), target.getY(), target.getZ(), java.util.Set.of(), watcher.getYRot(), watcher.getXRot(), true);
                watcher.setGameMode(GameType.SPECTATOR);
                watcher.setCamera(target);
            }
        }
    }

    public void restorePlayerGameMode(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Set<UUID> watchers = new HashSet<>(originalModes.keySet());
        spectatorMap.values().forEach(watchers::addAll);
        for (UUID id : watchers) PlayerRecovery.restore(serverLevel.getServer(), id,
                GameType.byId(originalModes.getOrDefault(id, GameType.SURVIVAL.getId())));
        spectatorMap.clear();
        originalModes.clear();
    }
    @Override public void onEnd(ServerLevel level) {
        restorePlayerGameMode(level);
        players.clear(); deathMap.clear(); escapeMap.clear(); pendingRespawns.clear();
    }
    @Override public Codec<? extends ITracker> codec() { return CODEC; }
    @Override public ITracker type() { return ModAftermathModule.RAID_PLAYER_BATTLE_TRACKER.get(); }
    public Set<UUID> getPlayers() { return players; }
    public Map<UUID, Integer> getDeathMap() { return deathMap; }
    public Map<UUID, Long> getEscapeMap() { return escapeMap; }
    public Map<UUID, Set<UUID>> getSpectatorMap() { return spectatorMap; }
}
