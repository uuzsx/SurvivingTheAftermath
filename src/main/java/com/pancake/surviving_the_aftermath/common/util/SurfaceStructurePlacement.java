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
        if (!context.validBiome().test(context.biomeResolver().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(centerX),
                net.minecraft.core.QuartPos.fromBlock(centerY), net.minecraft.core.QuartPos.fromBlock(centerZ)))) {
            return Optional.empty();
        }
        // Try nearby clearings in deterministic order. Cache overlapping noise queries so the
        // stricter dry-site policy does not require excavating rivers or making cities scarce.
        Map<Long,Integer> heights = new HashMap<>();
        ToIntBiFunction<Integer,Integer> ground = (x,z) -> heights.computeIfAbsent(((long)x << 32) ^ (z & 0xffffffffL), key -> {
            var generator = context.chunkGenerator();
            int surface = generator.getFirstOccupiedHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState());
            int floor = generator.getFirstOccupiedHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            return surface == floor ? surface : INVALID_GROUND;
        });
        boolean city = template.getSize().getX() * template.getSize().getZ() >= 4096;
        int[][] offsets = city ? new int[][]{{0,0},{24,0},{-24,0},{0,24},{0,-24},{24,24},{24,-24},{-24,24},{-24,-24}}
                : new int[][]{{0,0}};
        for (var shift : offsets) {
            int x = centerX + shift[0], z = centerZ + shift[1];
            int y = ground.applyAsInt(x,z);
            if (y == INVALID_GROUND || !context.validBiome().test(context.biomeResolver().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(y), net.minecraft.core.QuartPos.fromBlock(z)))) continue;
            var candidate = plan(template,rotation,x,z,groundOffset,context.heightAccessor().getMinY(),(context.heightAccessor().getMinY() + context.heightAccessor().getHeight()),ground);
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
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
        boolean city = size.getX() * size.getZ() >= 4096;
        int relief = city ? 12 : Math.min(6, Math.max(1, size.getY() - 1));
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        Map<Long, Integer> sampled = new HashMap<>();
        // Cheap early rejection of oceans and steep hills before querying the finer footprint grid.
        int resolution = city ? 4 : size.getX() * size.getZ() >= 256 ? 2 : 1;
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
        return settings(rotation, grounded, groundOffset, null);
    }

    public static StructurePlaceSettings settings(Rotation rotation, boolean grounded, int groundOffset, StructureTemplate cityTemplate) {
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
                if (cityTemplate != null && local.state().isAir()) {
                    var existing = level.getBlockState(world.pos());
                    if ((existing.is(BlockTags.LEAVES) || existing.is(BlockTags.LOGS))
                            && CityTerrainProtection.externalTree(level, world.pos(), cityTemplate.getBoundingBox(placement, origin))) return null;
                }
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

    /** Solid city foundation with a small, shallow transition around protected natural features. */
    public static void gradeCity(WorldGenLevel level, BoundingBox footprint, BoundingBox chunk, int groundY) {
        gradeCity(level, footprint, chunk, groundY, java.util.List.of());
    }

    public static void gradeCity(WorldGenLevel level, BoundingBox footprint, BoundingBox chunk, int groundY,
                                 java.util.List<BoundingBox> protectedBuildings) {
        var area = cityTerrainBounds(footprint);
        int lowerBound = Math.max(level.getMinY(), chunk.minY());
        if (groundY < lowerBound || groundY > chunk.maxY()) return;
        var cursor = new BlockPos.MutableBlockPos();
        var protectedApron = CityTerrainProtection.protectedApron(level, footprint, chunk, groundY, lowerBound, protectedBuildings);
        for (int x = Math.max(area.minX(), chunk.minX()); x <= Math.min(area.maxX(), chunk.maxX()); x++) {
            for (int z = Math.max(area.minZ(), chunk.minZ()); z <= Math.min(area.maxZ(), chunk.maxZ()); z++) {
                // Preserve the entire building column, including doors and hollow rooms.
                if (CityStructureAvoidance.protectedColumn(protectedBuildings, x, z)) continue;
                int distance = CityTerrainProtection.distance(footprint, x, z);
                boolean outside = distance > 0;
                if (outside && (distance >= CityTerrainProtection.TRANSITION_RADIUS
                        || CityTerrainProtection.protectedColumn(protectedApron, x, z))) continue;
                int top = Math.min(chunk.maxY(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                int naturalY = top;
                while (naturalY > lowerBound) {
                    cursor.set(x, naturalY, z);
                    if (solidSupport(level, cursor)) break;
                    naturalY--;
                }
                int targetY = groundY;
                if (outside) {
                    double fraction = distance / (double) CityTerrainProtection.TRANSITION_RADIUS;
                    double blend = fraction * fraction * (3 - 2 * fraction);
                    targetY = groundY + (int) Math.round((naturalY - groundY) * blend);
                    targetY = Math.max(naturalY - CityTerrainProtection.MAX_EDGE_CHANGE,
                            Math.min(naturalY + CityTerrainProtection.MAX_EDGE_CHANGE, targetY));
                    if (targetY == naturalY) continue;
                }
                for (int y = targetY + 1; y <= top; y++) {
                    cursor.set(x, y, z);
                    var state = level.getBlockState(cursor);
                    if (!outside && (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS))
                            && CityTerrainProtection.externalTree(level, cursor, footprint)) continue;
                    if (!state.hasBlockEntity()) level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }
                if (outside) {
                    // Only alter the shallow surface; never fill caves or aquifers outside city walls.
                    for (int y = naturalY + 1; y <= targetY; y++)
                        level.setBlock(cursor.set(x,y,z), Blocks.DIRT.defaultBlockState(), 2);
                } else fillFoundation(level, cursor, x, z, targetY, lowerBound);
                cursor.set(x, targetY, z);
                if (!level.getBlockState(cursor).hasBlockEntity()) level.setBlock(cursor, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        }
    }

}
