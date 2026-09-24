package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.common.init.ModStructurePieceTypes;
import com.pancake.surviving_the_aftermath.common.structure.AbstractStructure;
import com.pancake.surviving_the_aftermath.common.util.SurfaceStructurePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@net.neoforged.neoforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TerrainPlacementGameTests {
    private static void check(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
    private static List<String> templates() {
        var ids = new ArrayList<>(List.of("city", "nether_invasion_portal", "house_of_sakura", "camp", "logs", "tent", "brick_well", "cobblestone_pile", "construction1", "construction2"));
        for (int i = 1; i <= 6; i++) { ids.add("burnt_structure" + i); ids.add("wagon_cargo" + i); }
        return ids;
    }

    @GameTest(template = "stability_empty", timeoutTicks = 400)
    public static void terrainPlansCheckFootprint(GameTestHelper h) {
        for (String id : templates()) for (Rotation rotation : Rotation.values()) {
            var template = h.getLevel().getStructureManager().getOrCreate(SurvivingTheAftermath.asResource(id));
            int offset = SurfaceStructurePlacement.groundOffset(id);
            var calls = new AtomicInteger();
            var origin = SurfaceStructurePlacement.plan(template, rotation, -103, 207, offset, -64, 320,
                    (x, z) -> { calls.incrementAndGet(); return 70; }).orElseThrow();
            var box = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), origin);
            check(origin.getY() == 70 + offset, id + ": wrong floor height");
            check(calls.get() <= Math.min(1400, (box.getXSpan() + 4) * (box.getZSpan() + 4)), id + ": too many terrain queries");
            check(SurfaceStructurePlacement.plan(template, rotation, -103, 207, offset, -64, 320,
                    (x, z) -> x == -103 && z == 207 ? 90 : 70).isEmpty(), id + ": interior spike accepted");
            check(SurfaceStructurePlacement.plan(template, rotation, -103, 207, offset, -64, 320,
                    (x, z) -> x == box.maxX() + 2 && z == box.maxZ() + 2 ? SurfaceStructurePlacement.INVALID_GROUND : 70).isEmpty(), id + ": wet entrance border accepted");
            check(SurfaceStructurePlacement.plan(template, rotation, -103, 207, offset, -64, 72,
                    (x, z) -> 70).isEmpty(), id + ": build ceiling exceeded");
            check(SurfaceStructurePlacement.plan(template, rotation, -103, 207, offset, -64, 320,
                    (x, z) -> -64).isEmpty(), id + ": void accepted");
        }
        h.succeed();
    }

    private static void placeChunk(GameTestHelper h, String id, Rotation rotation, int index, boolean reload) {
        var level = h.getLevel();
        var template = level.getStructureManager().getOrCreate(SurvivingTheAftermath.asResource(id));
        var center = h.absolutePos(new BlockPos(4096 + index * 128, 10, 4096));
        int offset = SurfaceStructurePlacement.groundOffset(id);
        var origin = SurfaceStructurePlacement.plan(template, rotation, center.getX(), center.getZ(), offset,
                level.getMinBuildHeight(), level.getMaxBuildHeight(), (x, z) -> center.getY()).orElseThrow();
        var piece = new AbstractStructure.Piece(ModStructurePieceTypes.NETHER_RAID.get(), level.getStructureManager(),
                SurvivingTheAftermath.asResource(id), origin, rotation);
        if (reload) {
            var context = StructurePieceSerializationContext.fromLevel(level);
            var tag = piece.createTag(context);
            check(tag.getBoolean("SurfaceGrounded"), "Ground placement flag was not saved");
            piece = new AbstractStructure.Piece(ModStructurePieceTypes.NETHER_RAID.get(), context, tag);
        }
        var chunkPos = new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
        var chunk = new BoundingBox(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunkPos.getMaxBlockZ());
        var bounds = piece.getBoundingBox();
        int minX = Math.max(bounds.minX(), chunk.minX()), maxX = Math.min(bounds.maxX(), chunk.maxX());
        int minZ = Math.max(bounds.minZ(), chunk.minZ()), maxZ = Math.min(bounds.maxZ(), chunk.maxZ());
        int groundY = origin.getY() - offset;
        // A sloping site with a gap below the floor and a hill/vegetation occupying authored air.
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            level.setBlock(new BlockPos(x, groundY - 4, z), Blocks.STONE.defaultBlockState(), 18);
            for (int y = groundY - 3; y <= bounds.maxY() + 3; y++) {
                level.setBlock(new BlockPos(x, y, z), (y <= groundY ? Blocks.AIR : Blocks.DIRT).defaultBlockState(), 18);
            }
        }
        var guard = new BlockPos(chunk.maxX() + 1, groundY - 1, center.getZ());
        level.setBlock(guard, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(24), chunk, chunkPos, origin);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            for (int y = bounds.maxY() + 1; y <= bounds.maxY() + 3; y++) {
                check(level.getBlockState(new BlockPos(x, y, z)).isAir(), id + ": overburden above the roof remained");
            }
        }
        int cleared = 0;
        for (var info : template.filterBlocks(origin, piece.placeSettings(), Blocks.AIR)) {
            if (!chunk.isInside(info.pos()) || info.pos().getY() <= groundY) continue;
            check(level.getBlockState(info.pos()).isAir(), id + ": terrain remained in template air at " + info.pos());
            cleared++;
        }
        check(cleared > 0, id + ": no interior air exercised");
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            for (int y = groundY - 3; y < groundY; y++) {
                var pos = new BlockPos(x, y, z);
                check(level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP), id + ": unsupported foundation at " + pos);
            }
        }
        check(level.getBlockState(guard).is(Blocks.DIAMOND_BLOCK), "Placement wrote into adjacent chunk");
    }

    @GameTest(template = "stability_empty", timeoutTicks = 400)
    public static void terrainCityAndRaidClearAndSupport(GameTestHelper h) {
        placeChunk(h, "city", Rotation.CLOCKWISE_90, 0, false);
        placeChunk(h, "nether_invasion_portal", Rotation.COUNTERCLOCKWISE_90, 1, false);
        h.succeed();
    }

    @GameTest(template = "stability_empty", timeoutTicks = 400)
    public static void terrainPlacementSurvivesPieceReload(GameTestHelper h) {
        int index = 2;
        for (Rotation rotation : Rotation.values()) placeChunk(h, "camp", rotation, index++, true);
        placeChunk(h, "wagon_cargo1", Rotation.CLOCKWISE_90, index, true);
        h.succeed();
    }

    @GameTest(template = "stability_empty", timeoutTicks = 400)
    public static void terrainLegacyPiecesKeepOldPlacement(GameTestHelper h) {
        var level = h.getLevel();
        var context = StructurePieceSerializationContext.fromLevel(level);
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        var template = level.getStructureManager().getOrCreate(SurvivingTheAftermath.asResource("camp"));
        var piece = new AbstractStructure.Piece(ModStructurePieceTypes.NETHER_RAID.get(), level.getStructureManager(),
                SurvivingTheAftermath.asResource("camp"), pos, Rotation.CLOCKWISE_90);
        var tag = piece.createTag(context);
        tag.remove("SurfaceGrounded");
        var legacy = new AbstractStructure.Piece(ModStructurePieceTypes.NETHER_RAID.get(), context, tag);
        check(legacy.templatePosition().equals(pos), "Existing saved piece was moved");
        check(legacy.placeSettings().getProcessors().contains(BlockIgnoreProcessor.STRUCTURE_AND_AIR), "Existing piece silently switched placement rules");
        check(legacy.getRotation() == Rotation.CLOCKWISE_90, "Existing saved piece lost its rotation");
        h.succeed();
    }
    @GameTest(template = "stability_empty", timeoutTicks = 400)
    public static void terrainRealNoiseSiteSelection(GameTestHelper h) {
        var level = h.getLevel();
        var lookup = level.registryAccess();
        var noise = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var biome = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                new net.minecraft.world.level.biome.FixedBiomeSource(biome), noise);
        long seed = 20260924L;
        var state = net.minecraft.world.level.levelgen.RandomState.create(noise.value(),
                lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed);
        for (String id : List.of("city", "nether_invasion_portal")) {
            var template = level.getStructureManager().getOrCreate(SurvivingTheAftermath.asResource(id));
            boolean found = false;
            long started = System.nanoTime();
            for (int i = 0; i < 32; i++) {
                var chunk = i == 0 ? (id.equals("city") ? new ChunkPos(440, 262) : new ChunkPos(1914, -224))
                        : new ChunkPos((i * 731 + 17) % 4096 - 2048, (i * 1279 + 43) % 4096 - 2048);
                var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                        lookup, generator, generator.getBiomeSource(), state, level.getStructureManager(), seed, chunk, level, b -> true);
                var planned = SurfaceStructurePlacement.findOrigin(context, template, Rotation.CLOCKWISE_90, 0);
                if (planned.isEmpty()) continue;
                var forbidden = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(
                        lookup, generator, generator.getBiomeSource(), state, level.getStructureManager(), seed, chunk, level, b -> false);
                check(SurfaceStructurePlacement.findOrigin(forbidden, template, Rotation.CLOCKWISE_90, 0).isEmpty(), "Excluded surface biome accepted");
                var pos = planned.get();
                var box = template.getBoundingBox(new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90), pos);
                for (int x : new int[]{box.minX(), box.getCenter().getX(), box.maxX()}) {
                    for (int z : new int[]{box.minZ(), box.getCenter().getZ(), box.maxZ()}) {
                        int surface = generator.getFirstOccupiedHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, state);
                        int floor = generator.getFirstOccupiedHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, state);
                        check(surface == floor && pos.getY() >= surface && pos.getY() - floor <= (id.equals("city") ? 12 : 6),
                                "Real terrain placement submerged, buried or too far above terrain");
                    }
                }
                System.out.println("TERRAIN NOISE CHECK: " + id + ", candidates=" + (i + 1)
                        + ", origin=" + pos + ", elapsed_ms=" + ((System.nanoTime() - started) / 1_000_000));
                found = true;
                break;
            }
            check(found, "No usable real-terrain site found for " + id);
        }
        h.succeed();
    }
}
