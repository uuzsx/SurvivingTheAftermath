package com.pancake.surviving_the_aftermath.api;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.common.init.ModuleRegistry;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;
import java.util.function.Supplier;

public interface ITracker extends ICodec<ITracker> {
    Supplier<Codec<ITracker>> CODEC = () -> ModuleRegistry.TRACKER_REGISTRY.byNameCodec()
            .dispatch("tracker", ITracker::type, value -> com.pancake.surviving_the_aftermath.common.util.CodecUtils.mapCodec(value.codec()));

    static void register(ITracker tracker) {
        NeoForge.EVENT_BUS.register(tracker);
    }
    static void unregister(ITracker tracker) {
        NeoForge.EVENT_BUS.unregister(tracker);
    }

    ITracker setUUID(UUID uuid);

    default void onEnd(net.minecraft.server.level.ServerLevel level) {}
}