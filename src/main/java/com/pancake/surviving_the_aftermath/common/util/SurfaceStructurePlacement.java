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

    public static final int CITY_BLEND_RADIUS = 24;

    public static BoundingBox cityTerrainBounds(BoundingBox box) {
        return new BoundingBox(box.minX() - CITY_BLEND_RADIUS, box.minY(), box.minZ() - CITY_BLEND_RADIUS,
                box.maxX() + CITY_BLEND_RADIUS, box.maxY(), box.maxZ() + CITY_BLEND_RADIUS);
    }

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
                    // A shallow stream can be bridged by the city's solid foundation; oceans cannot.
                    boolean city = template.getSize().getX() * template.getSize().getZ() >= 4096;
                    return surface == floor || (city && surface - floor <= 3) ? surface : INVALID_GROUND;
                });
    }

    /** Checks the rotated footprint and entrance border. Large cities use an eight-block grid to bound noise-generation cost. */
    public static Optional<BlockPos> plan(StructureTemplate template, Rotation rotation, int centerX, int centerZ,
                                          int groundOffset, int minY, int maxY, ToIntBiFunction<Integer, Integer> ground) {
        var size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) return Optional.empty();
        var settings = new StructurePlaceSettings().setRotation(rotation);
        var local = template.getBoundingBox(settings, BlockPos.ZERO);
        var anchor = new BlockPos(centerX - Math.floorDiv(local.minX() + local.maxX(), 2), 0,
                centerZ - Math.floorDiv(local.minZ() + local.maxZ(), 2));
        var bounds = template.getBoundingBox(settings, anchor);
        boolean city = size.getX() * size.getZ() >= 4096;
        int relief = city ? 24 : Math.min(6, Math.max(1, size.getY() - 1));
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        Map<Long, Integer> sampled = new HashMap<>();
        // Cheap early rejection of oceans and steep hills before querying the finer footprint grid.
        int resolution = city ? 8 : size.getX() * size.getZ() >= 256 ? 2 : 1;
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
        // Cities are graded into the site instead of perched on its highest corner.
        int elevation = city ? sampled.values().stream().sorted().skip(sampled.size() / 2).findFirst().orElseThrow() : highest;
        return Optional.of(new BlockPos(anchor.getX(), elevation + groundOffset, anchor.getZ()));
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
            @Override public com.mojang.serialization.MapCodec<? extends StructureProcessor> codec() { return com.mojang.serialization.MapCodec.unit(this); }
            @Override public StructureTemplate.StructureBlockInfo process(LevelReader level, BlockPos origin,
                    BlockPos reference, StructureTemplate.StructureBlockInfo local,
                    StructureTemplate.StructureBlockInfo world, StructurePlaceSettings placement, StructureTemplate template) {
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
                fillFoundation(level, cursor, x, z, groundY, lowerBound);
            }
        }
    }

    private static boolean solidSupport(LevelReader level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static void fillFoundation(WorldGenLevel level, BlockPos.MutableBlockPos cursor,
                                       int x, int z, int groundY, int lowerBound) {
        int solidRun = 0;
        for (int y = groundY; y >= lowerBound; y--) {
            cursor.set(x, y, z);
            var state = level.getBlockState(cursor);
            if (state.hasBlockEntity()) break;
            if (solidSupport(level, cursor)) {
                // A dirt/stone cap is NOT proof of support. Inspect the shallow 32-block band
                // and only terminate in a continuous solid base below it, preserving deep caves.
                if (++solidRun >= 4 && y <= groundY - 32) break;
                continue;
            }
            solidRun = 0;
            var fill = y == groundY ? Blocks.GRASS_BLOCK : groundY - y < 4 ? Blocks.DIRT : Blocks.STONE;
            level.setBlock(cursor, fill.defaultBlockState(), 2);
        }
    }

    /** Entire footprint plus an explicit apron; each column depends only on its own original terrain. */
    public static void gradeCity(WorldGenLevel level, BoundingBox footprint, BoundingBox chunk, int groundY) {
        var area = cityTerrainBounds(footprint);
        int lowerBound = Math.max(level.getMinY(), chunk.minY());
        if (groundY < lowerBound || groundY > chunk.maxY()) return;
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = Math.max(area.minX(), chunk.minX()); x <= Math.min(area.maxX(), chunk.maxX()); x++) {
            for (int z = Math.max(area.minZ(), chunk.minZ()); z <= Math.min(area.maxZ(), chunk.maxZ()); z++) {
                int distance = Math.max(Math.max(footprint.minX() - x, x - footprint.maxX()),
                        Math.max(footprint.minZ() - z, z - footprint.maxZ()));
                int top = Math.min(chunk.maxY(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                int naturalY = top;
                while (naturalY > lowerBound) {
                    cursor.set(x, naturalY, z);
                    if (solidSupport(level, cursor)) break;
                    naturalY--;
                }
                // Preserve the untouched outside edge, including vegetation.
                if (distance >= CITY_BLEND_RADIUS) continue;
                double fraction = Math.max(0, distance - 2) / (double) (CITY_BLEND_RADIUS - 2);
                double blend = fraction * fraction * (3 - 2 * fraction);
                int targetY = groundY + (int) Math.round((naturalY - groundY) * blend);
                for (int y = targetY + 1; y <= top; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).hasBlockEntity()) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }
                fillFoundation(level, cursor, x, z, targetY, lowerBound);
                cursor.set(x, targetY, z);
                if (!level.getBlockState(cursor).hasBlockEntity()) level.setBlock(cursor, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
    }

}
