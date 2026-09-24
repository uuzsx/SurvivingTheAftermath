package com.pancake.surviving_the_aftermath.api;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.module.IAftermathModule;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.*;

public class AftermathManager {
    private final Map<UUID, IAftermath> AFTERMATH_MAP = new LinkedHashMap<>();
    public final Multimap<ResourceLocation, IAftermathModule> AFTERMATH_MODULE_MAP = ArrayListMultimap.create();
    private static final AftermathManager INSTANCE = new AftermathManager();
    public static AftermathManager getInstance() { return INSTANCE; }
    private AftermathManager() {}

    public void tick() {
        // Event handlers may create another encounter while this one is ticking.
        for (IAftermath aftermath : List.copyOf(AFTERMATH_MAP.values())) {
            if (!aftermath.isEnd()) aftermath.tick();
            if (aftermath.isEnd() && AFTERMATH_MAP.remove(aftermath.getUUID(), aftermath)) {
                aftermath.getTrackers().forEach(ITracker::unregister);
            }
        }
    }

    private void add(IAftermath aftermath) {
        IAftermath old = AFTERMATH_MAP.put(aftermath.getUUID(), aftermath);
        if (old != null) old.getTrackers().forEach(ITracker::unregister);
        aftermath.getTrackers().forEach(tracker -> {
            tracker.setUUID(aftermath.getUUID());
            ITracker.register(tracker);
        });
    }

    public Map<UUID, IAftermath> getAftermathMap() { return AFTERMATH_MAP; }
    public Optional<IAftermath> getAftermath(UUID uuid) { return Optional.ofNullable(AFTERMATH_MAP.get(uuid)); }

    public boolean create(IAftermath aftermath, Level level, BlockPos pos, @Nullable ServerPlayer player) {
        if (!aftermath.isCreate(level, pos, player)) return false;
        IAftermath created = aftermath.Create();
        if (created.isEnd()) return false;
        add(created);
        return true;
    }

    public void create(ServerLevel level, CompoundTag tag) { create(level, UUID.randomUUID(), tag); }

    public void create(ServerLevel level, UUID savedId, CompoundTag tag) {
        IAftermath.CODEC.get().parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(SurvivingTheAftermath.LOGGER::error)
                .filter(aftermath -> !aftermath.isEnd())
                .ifPresent(aftermath -> {
                    aftermath.restore(level, savedId);
                    add(aftermath);
                });
    }

    public void clear() {
        AFTERMATH_MAP.values().forEach(a -> a.getTrackers().forEach(ITracker::unregister));
        AFTERMATH_MAP.clear();
        AFTERMATH_MODULE_MAP.clear();
    }

    public Multimap<ResourceLocation, IAftermathModule> getAftermathModuleMap() { return AFTERMATH_MODULE_MAP; }
    public void fillAftermathModuleMap(Multimap<ResourceLocation, IAftermathModule> map) {
        AFTERMATH_MODULE_MAP.clear();
        AFTERMATH_MODULE_MAP.putAll(map);
    }
}
