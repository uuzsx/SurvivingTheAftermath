package com.pancake.surviving_the_aftermath.api.base;

import com.pancake.surviving_the_aftermath.api.*;
import com.pancake.surviving_the_aftermath.api.module.IAftermathModule;
import com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader;
import com.pancake.surviving_the_aftermath.common.network.AftermathNetwork;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.util.AftermathEventUtil;
import com.pancake.surviving_the_aftermath.common.util.BattleEntityState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Predicate;

public abstract class BaseAftermath implements IAftermath {
    public ServerLevel level;
    public IAftermathModule module;
    public AftermathState state = AftermathState.START;
    protected Set<UUID> players = new HashSet<>();
    protected List<ITracker> trackers = new ArrayList<>();
    protected final ServerBossEvent progress = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    protected UUID uuid = UUID.randomUUID();
    protected float progressPercent = progress.getProgress();

    public BaseAftermath(AftermathState state, IAftermathModule module, Set<UUID> players, float progressPercent, List<ITracker> trackers) {
        this.state = state;
        this.module = module;
        this.players = new HashSet<>(players);
        this.progressPercent = progressPercent;
        this.trackers = new ArrayList<>(trackers);
    }
    public BaseAftermath(ServerLevel level) { this.level = level; }
    public BaseAftermath(BaseRaidModule module, ServerLevel level) { this.level = level; this.module = module; }
    public BaseAftermath() {}

    protected void init() {
        updatePlayers();
        bindTrackers();
        if (!AftermathEventUtil.start(this, players, level)) end();
    }

    @Override
    public boolean isCreate(Level level, BlockPos pos, @Nullable Player player) {
        if (module == null) {
            List<IAftermathModule> available = AftermathModuleLoader.AFTERMATH_MODULE_MAP.get(getRegistryName()).stream()
                    .filter(candidate -> candidate.isCreate(level, pos, player)).toList();
            if (available.isEmpty()) return false;
            module = available.get(level.random.nextInt(available.size()));
        }
        return module.isCreate(level, pos, player);
    }

    @Override public IAftermath Create() { init(); return this; }

    @Override
    public void tick() {
        if (isEnd()) return;
        updatePlayers();
        updateInsertTag();
        if (state == AftermathState.VICTORY) {
            progressPercent = 0;
            if (!AftermathEventUtil.celebrating(this, players, level)) end();
        }
        updateProgress();
    }

    public void updateInsertTag() {
        players.forEach(id -> {
            if (level.getEntity(id) instanceof LivingEntity entity) insertTag(entity);
        });
    }

    @Override public void updateProgress() { progress.setProgress(Math.max(0, Math.min(1, progressPercent))); }

    public void updatePlayers() {
        Set<ServerPlayer> old = new HashSet<>(progress.getPlayers());
        Set<ServerPlayer> current = new HashSet<>(level.getPlayers(validPlayer()));
        for (ServerPlayer player : old) {
            if (!current.contains(player)) {
                progress.removePlayer(player);
                AftermathNetwork.removeBar(player, progress.getId());
                BattleEntityState.clear(player, uuid);
            }
        }
        for (ServerPlayer player : current) {
            if (!old.contains(player)) {
                progress.addPlayer(player);
                AftermathNetwork.sendBar(player, progress.getId(), getBarsResource(), getBarsOffset());
            }
        }
        players.clear();
        current.forEach(player -> players.add(player.getUUID()));
    }

    public Predicate<? super ServerPlayer> validPlayer() { return player -> player.isAlive() && !player.isSpectator(); }
    @Override public void createRewards() {}
    protected abstract void bindTrackers();
    protected void addTrackers(ITracker tracker) { trackers.add(tracker); }
    @Override public List<ITracker> getTrackers() { return trackers; }
    @Override public IAftermathModule getModule() { return module; }
    @Override public UUID getUUID() { return uuid; }
    public Set<UUID> getPlayers() { return players; }
    public float getProgressPercent() { return progressPercent; }
    @Override public AftermathState getState() { return state; }
    @Override public void setLevel(ServerLevel level) { this.level = level; }
    @Override public void setState(AftermathState state) { this.state = state; }
    @Override public boolean isEnd() { return state == AftermathState.END; }

    @Override
    public void restore(ServerLevel level, UUID savedId) {
        this.level = level;
        this.uuid = savedId;
        trackers.forEach(tracker -> tracker.setUUID(savedId));
    }

    public IAftermathModule getRandomAftermathModule() {
        List<IAftermathModule> modules = new ArrayList<>(AftermathModuleLoader.AFTERMATH_MODULE_MAP.get(getRegistryName()));
        return modules.isEmpty() ? null : modules.get(level.random.nextInt(modules.size()));
    }

    public void end() {
        if (isEnd()) return;
        // Finish state restoration even when the encounter was cancelled before registration.
        trackers.forEach(tracker -> tracker.onEnd(level));
        AftermathEventUtil.end(this, players, level);
        for (ServerPlayer player : List.copyOf(progress.getPlayers())) AftermathNetwork.removeBar(player, progress.getId());
        progress.removeAllPlayers();
        for (Entity entity : level.getAllEntities()) BattleEntityState.clear(entity, uuid);
        players.clear();
    }

    @Override public void lose() {
        if (isEnd()) return;
        AftermathEventUtil.lose(this, players, level);
        end();
    }
    public abstract void insertTag(LivingEntity entity);
}
