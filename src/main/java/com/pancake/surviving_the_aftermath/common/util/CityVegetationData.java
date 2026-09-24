package com.pancake.surviving_the_aftermath.common.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-dimension repair history; accessed only from the server thread. */
public final class CityVegetationData extends SavedData {
    private static final String KEY = "aftermath_city_leaves_v1";
    private final LongOpenHashSet cleaned = new LongOpenHashSet();

    public static CityVegetationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isCleaned(long chunk) { return cleaned.contains(chunk); }

    public void setCleaned(long chunk, boolean value) {
        if (value ? cleaned.add(chunk) : cleaned.remove(chunk)) setDirty();
    }

    public static CityVegetationData load(CompoundTag tag) {
        CityVegetationData data = new CityVegetationData();
        for (long chunk : tag.getLongArray("Chunks").orElse(new long[0])) data.cleaned.add(chunk);
        return data;
    }

    public CompoundTag write(CompoundTag tag) {
        tag.putLongArray("Chunks", cleaned.toLongArray());
        return tag;
    }

    private static final net.minecraft.world.level.saveddata.SavedDataType<CityVegetationData> TYPE =
            new net.minecraft.world.level.saveddata.SavedDataType<>(
                    com.pancake.surviving_the_aftermath.SurvivingTheAftermath.asResource(KEY),
                    CityVegetationData::new, CompoundTag.CODEC.xmap(CityVegetationData::load, data -> data.write(new CompoundTag())));
}
