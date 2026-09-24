package com.pancake.surviving_the_aftermath.common.mixin;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseGeneratorAccessor {
    @Accessor("globalFluidPicker") Supplier<Aquifer.FluidPicker> aftermath$fluidPicker();
}
