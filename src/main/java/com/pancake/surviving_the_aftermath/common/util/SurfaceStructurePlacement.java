package com.pancake.surviving_the_aftermath.common.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** One terrain decision per structure, saved as its template origin, never per chunk. */
public final class SurfaceStructurePlacement {
    private SurfaceStructurePlacement() {}
    public static final int INVALID_GROUND = Integer.MIN_VALUE;

    public static int groundOffset(String template) {
        // These templates start with objects resting above the soil, rather than an authored ground layer.
        return template.startsWith("wagon_cargo") || template.equals("logs") || template.equals("cobblestone_pile") ? 1 : 0;
    }

    public static Optional<BlockPos> findOrigin(Structure.GenerationContext context, StructureTemplate template,
                                               Rotation rotation, int groundOffset) {
        int centerX = context.chunkPos().getMiddleBlockX(), centerZ = context.chunkPos().getMiddleBlockZ();
        int centerY = context.chunkGenerator().getFirstOccupiedHeight(centerX, centerZ, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
        // Structure.findValidGenerationPoint also verifies the final height. Reject an ineligible
        // surface biome before doing the more expensive footprint terrain queries.
        if (!context.validBiome().test(context.biomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(centerX),
                net.minecraft.core.QuartPos.fromBlock(centerY), net.minecraft.core.QuartPos.fromBlock(centerZ), context.randomState().sampler()))) {
            return Optional.empty();
        }
        return plan(template, rotation, context.chunkPos().getMiddleBlockX(), context.chunkPos().getMiddleBlockZ(),
                groundOffset, context.heightAccessor().getMinY(), (context.heightAccessor().getMinY() + context.heightAccessor().getHeight()),
                (x, z) -> {
                    var generator = context.chunkGenerator();
                    int surface = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                            context.heightAccessor(), context.randomState());
                    int floor = generator.getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                            context.heightAccessor(), context.randomState());
                    return surface == floor ? floor : INVALID_GROUND;
                });
    }

    /** Checks the rotated footprint and entrance border. Large cities use a four-block grid to bound noise-generation cost. */
    public static Optional<BlockPos> plan(StructureTemplate template, Rotation rotation, int centerX, int centerZ,
                                          int groundOffset, int minY, int maxY, ToIntBiFunction<Integer, Integer> ground) {
        var size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) return Optional.empty();
        var settings = new StructurePlaceSettings().setRotation(rotation);
        var local = template.getBoundingBox(settings, BlockPos.ZERO);
        var anchor = new BlockPos(centerX - Math.floorDiv(local.minX() + local.maxX(), 2), 0,
                centerZ - Math.floorDiv(local.minZ() + local.maxZ(), 2));
        var bounds = template.getBoundingBox(settings, anchor);
        int relief = Math.min(size.getX() * size.getZ() >= 4096 ? 12 : 6, Math.max(1, size.getY() - 1));
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        Map<Long, Integer> sampled = new HashMap<>();
        // Cheap early rejection of oceans and steep hills before querying the finer footprint grid.
        int resolution = size.getX() * size.getZ() >= 4096 ? 4 : size.getX() * size.getZ() >= 256 ? 2 : 1;
        for (int step : new int[]{8, resolution}) {
            for (int x : samples(bounds.minX() - 2, bounds.maxX() + 2, centerX, step)) {
                for (int z : samples(bounds.minZ() - 2, bounds.maxZ() + 2, centerZ, step)) {
                    long key = ((long) x << 32) ^ (z & 0xffffffffL);
                    final int sampleX = x, sampleZ = z;
                    int y = sampled.computeIfAbsent(key, k -> ground.applyAsInt(sampleX, sampleZ));
                    if (y <= minY || y == INVALID_GROUND) return Optional.empty();
                    lowest = Math.min(lowest, y);
                    highest = Math.max(highest, y);
                    if (highest - lowest > relief || highest + groundOffset + size.getY() > maxY) return Optional.empty();
                }
            }
        }
        // Keep entrances above the surrounding terrain; small gaps are supported during placement.
        return Optional.of(new BlockPos(anchor.getX(), highest + groundOffset, anchor.getZ()));
    }

    private static java.util.SortedSet<Integer> samples(int min, int max, int center, int step) {
        var result = new java.util.TreeSet<Integer>();
        for (int value = min; value <= max; value += step) result.add(value);
        result.add(max);
        result.add(center);
        return result;
    }

    public static StructurePlaceSettings settings(Rotation rotation, boolean grounded, int groundOffset) {
        var settings = new StructurePlaceSettings().setRotation(rotation);
        if (!grounded) return settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        return settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).addProcessor(new StructureProcessor() {
            @Override protected StructureProcessorType<?> getType() { return null; }
            @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin,
                    BlockPos reference, StructureTemplate.StructureBlockInfo local,
                    StructureTemplate.StructureBlockInfo world, StructurePlaceSettings placement) {
                if (placement.getBoundingBox() != null && !placement.getBoundingBox().isInside(world.pos())) return null;
                // Keep outdoor soil at floor height; authored air above it must clear rooms and doors.
                if (local.state().isAir() && local.pos().getY() + groundOffset <= 0) return null;
                return world;
            }
        });
    }

    /** Vanilla-style foundations, limited to the current chunk to avoid cross-chunk generation writes. */
    public static void support(WorldGenLevel level, BoundingBox footprint, BoundingBox chunk, int groundY) {
        int lowerBound = Math.max(level.getMinY(), chunk.minY());
        if (groundY > chunk.maxY() || groundY < lowerBound) return;
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = Math.max(footprint.minX(), chunk.minX()); x <= Math.min(footprint.maxX(), chunk.maxX()); x++) {
            for (int z = Math.max(footprint.minZ(), chunk.minZ()); z <= Math.min(footprint.maxZ(), chunk.maxZ()); z++) {
                // Remove overburden/treetops above the template too, so a narrow feature between
                // terrain samples cannot leave a buried roof or disconnected tree canopy.
                int top = Math.min(chunk.maxY(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                for (int y = footprint.maxY() + 1; y <= top; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).hasBlockEntity()) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }
                for (int y = groundY; y >= lowerBound; y--) {
                    cursor.set(x, y, z);
                    var state = level.getBlockState(cursor);
                    if (state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                            && state.isFaceSturdy(level, cursor, Direction.UP)) break;
                    // Do not overwrite containers or other block entities if another structure overlaps.
                    if (state.hasBlockEntity()) break;
                    var fill = y == groundY ? Blocks.GRASS_BLOCK : groundY - y < 4 ? Blocks.DIRT : Blocks.STONE;
                    level.setBlock(cursor, fill.defaultBlockState(), 2);
                }
            }
        }
    }
}
