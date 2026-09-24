package com.pancake.surviving_the_aftermath.api.module;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.api.IModule;
import com.pancake.surviving_the_aftermath.common.init.ModuleRegistry;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.random.Weighted;

import java.util.List;
import java.util.function.Supplier;

public interface IWeightedModule<T> extends IModule<IWeightedModule<T>> {
    Supplier<Codec<IWeightedModule<?>>> CODEC = () -> ModuleRegistry.WEIGHTED_REGISTRY.byNameCodec()
            .dispatch("weighted", IWeightedModule::type, value -> com.pancake.surviving_the_aftermath.common.util.CodecUtils.mapCodec(value.codec()));
    WeightedList<T> getWeightedList();
    void add(T t, int weight);
    void remove(T t);

    List<Weighted<T>> getList();
}
