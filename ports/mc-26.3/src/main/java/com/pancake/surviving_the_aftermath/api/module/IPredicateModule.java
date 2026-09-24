package com.pancake.surviving_the_aftermath.api.module;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.api.IModule;
import com.pancake.surviving_the_aftermath.common.init.ModuleRegistry;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

public interface IPredicateModule extends IModule<IPredicateModule> {
    Supplier<Codec<IPredicateModule>> CODEC = () -> ModuleRegistry.PREDICATE_REGISTRY.byNameCodec()
            .dispatch("entity_info", IPredicateModule::type, value -> com.pancake.surviving_the_aftermath.common.util.CodecUtils.mapCodec(value.codec()));

    void apply(LivingEntity livingEntity);
}
