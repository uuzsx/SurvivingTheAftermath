package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.common.mixin.NoiseGeneratorAccessor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntBiFunction;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Exact vanilla base heights, sharing noise interpolation across each sampled chunk.
 * The cache belongs to one placement attempt: no world, chunk or noise graph is retained. */
public final class SurfaceHeightSampler implements ToIntBiFunction<Integer,Integer> {
    private final Structure.GenerationContext context;
    private final Map<Long,int[]> chunks = new HashMap<>();

    public SurfaceHeightSampler(Structure.GenerationContext context) { this.context = context; }

    @Override public int applyAsInt(Integer x, Integer z) {
        // A custom generator may override base-height semantics. Respect its implementation.
        if (context.chunkGenerator().getClass() != NoiseBasedChunkGenerator.class) {
            var generator = context.chunkGenerator();
            int surface = generator.getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            int floor = generator.getFirstOccupiedHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            return surface == floor ? surface : SurfaceStructurePlacement.INVALID_GROUND;
        }
        long key = ((long)(x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
        return chunks.computeIfAbsent(key, ignored -> sample(x & ~15,z & ~15))[(x & 15) * 16 + (z & 15)];
    }

    private int[] sample(int x0,int z0) {
        var generator=(NoiseBasedChunkGenerator)context.chunkGenerator();
        var settings=generator.generatorSettings().value();
        var noise=settings.noiseSettings().clampToHeightAccessor(context.heightAccessor());
        int[] result=new int[256];Arrays.fill(result,SurfaceStructurePlacement.INVALID_GROUND);
        if(noise.height()<=0)return result;
        var volume=new net.minecraft.world.level.levelgen.densityfunction.DensityVolume(16,noise.height(),16,x0,noise.minY(),z0);
        // Both scoped density buffers and the pool acquired by NoiseChunk must be returned.
        try(var sampler=new NoiseChunk(context.randomState(),null,settings,
                ((NoiseGeneratorAccessor)generator).aftermath$fluidPicker().get(),Blender.empty(),volume)) {
            var density=sampler.cachingSamplers().get(settings.noiseRouter().finalDensity());
            try(var buffer=density.sampleVolume(volume)) {
                for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=volume.sizeY()-1;y>=0;y--) {
                    int blockY=volume.blockY(y);
                    var state=sampler.aquifer().computeSubstance(x0+x,blockY,z0+z,buffer.get(volume.indexUnchecked(x,y,z)));
                    if(state==null)state=settings.defaultBlock();
                    if(Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state)) {
                        if(Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state))result[x*16+z]=blockY;
                        break;
                    }
                }
            }
        }
        return result;
    }
}
