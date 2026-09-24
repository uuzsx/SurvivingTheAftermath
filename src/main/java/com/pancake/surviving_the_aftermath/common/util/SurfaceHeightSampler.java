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
        var generator = (NoiseBasedChunkGenerator)context.chunkGenerator();
        var settings = generator.generatorSettings().value();
        var noise = settings.noiseSettings().clampToHeightAccessor(context.heightAccessor());
        int width = noise.getCellWidth(), height = noise.getCellHeight(), cells = 16 / width;
        int[] result = new int[256];
        Arrays.fill(result,SurfaceStructurePlacement.INVALID_GROUND);
        if (noise.height() <= 0) return result;
        var sampler = new Interpolator(cells,context.randomState(),x0,z0,noise,settings,
                ((NoiseGeneratorAccessor)generator).aftermath$fluidPicker().get());
        var surface = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
        var floor = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
        sampler.initializeForFirstCellX();
        try {
            for(int cx=0;cx<cells;cx++) {
                sampler.advanceCellX(cx);
                for(int cz=0;cz<cells;cz++) {
                    boolean[] found = new boolean[width*width]; int remaining = found.length;
                    for(int cy=noise.height()/height-1;cy>=0 && remaining>0;cy--) {
                        sampler.selectCellYZ(cy,cz);
                        for(int dy=height-1;dy>=0 && remaining>0;dy--) {
                            int y=(Math.floorDiv(noise.minY(),height)+cy)*height+dy;
                            sampler.updateForY(y,dy/(double)height);
                            for(int dx=0;dx<width;dx++) {
                                int x=x0+cx*width+dx;
                                sampler.updateForX(x,dx/(double)width);
                                for(int dz=0;dz<width;dz++) {
                                    if(found[dx*width+dz])continue;
                                    int z=z0+cz*width+dz;
                                    sampler.updateForZ(z,dz/(double)width);
                                    var state=sampler.state();
                                    if(state==null)state=settings.defaultBlock();
                                    if(surface.test(state)) {
                                        found[dx*width+dz]=true;remaining--;
                                        if(floor.test(state))result[(x&15)*16+(z&15)]=y;
                                    }
                                }
                            }
                        }
                    }
                }
                sampler.swapSlices();
            }
        } finally { sampler.stopInterpolation(); }
        return result;
    }

    private enum EmptyBeard implements DensityFunctions.BeardifierOrMarker {
        INSTANCE;
        public double compute(DensityFunction.FunctionContext context) { return 0; }
        public double minValue() { return 0; }
        public double maxValue() { return 0; }
    }

    private static final class Interpolator extends NoiseChunk {
        Interpolator(int cells,RandomState random,int x,int z,NoiseSettings noise,NoiseGeneratorSettings settings,Aquifer.FluidPicker fluids) {
            super(cells,random,x,z,noise,EmptyBeard.INSTANCE,settings,fluids,Blender.empty());
        }
        net.minecraft.world.level.block.state.BlockState state() { return getInterpolatedState(); }
    }
}
