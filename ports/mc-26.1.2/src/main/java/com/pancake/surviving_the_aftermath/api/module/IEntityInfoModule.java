package com.pancake.surviving_the_aftermath.api.module;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.api.IModule;
import com.pancake.surviving_the_aftermath.common.init.ModuleRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import java.util.Optional;

import java.util.List;
import java.util.function.Supplier;

public interface IEntityInfoModule extends IModule<IEntityInfoModule> {
    Supplier<Codec<IEntityInfoModule>> CODEC = () -> ModuleRegistry.ENTITY_INFO_REGISTRY.byNameCodec()
            .dispatch("entity_info", IEntityInfoModule::type, value -> com.pancake.surviving_the_aftermath.common.util.CodecUtils.mapCodec(value.codec()));
    List<Optional<Entity>> spawnEntity(Level level);
    default List<Optional<Entity>> spawnEntity(Level level, net.minecraft.core.BlockPos origin) {
        return spawnEntity(level);
    }
}
