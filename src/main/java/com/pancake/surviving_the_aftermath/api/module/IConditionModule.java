package com.pancake.surviving_the_aftermath.api.module;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.api.IModule;
import com.pancake.surviving_the_aftermath.common.init.ModuleRegistry;

import java.util.function.Supplier;

public interface IConditionModule extends IModule<IConditionModule> {
    Supplier<Codec<IConditionModule>> CODEC = () -> ModuleRegistry.CONDITION_REGISTRY.byNameCodec()
            .dispatch("condition", IConditionModule::type, value -> com.pancake.surviving_the_aftermath.common.util.CodecUtils.mapCodec(value.codec()));

}
