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

@net.minecraftforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
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
                    (x, z) -> x == -103 && z == 207 ? 110 : 70).isEmpty(), id + ": interior spike accepted");
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
                level.setBlock(new BlockPos(x, y, z), (y == groundY ? Blocks.GRASS_BLOCK : y < groundY ? Blocks.AIR : Blocks.DIRT).defaultBlockState(), 18);
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
                        check(id.equals("city") ? surface - floor <= 3 && Math.abs(pos.getY() - surface) <= 24 : surface == floor && pos.getY() >= surface && pos.getY() - floor <= 6,
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
    @GameTest(template = "stability_empty", timeoutTicks = 1200)
    public static void terrainFullCityFoundationAndTransition(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getStructureManager();
        var template = manager.getOrCreate(SurvivingTheAftermath.asResource("city"));
        var center = h.absolutePos(new BlockPos(8192, 80, 8192));
        int floor = center.getY();
        var origin = SurfaceStructurePlacement.plan(template, Rotation.CLOCKWISE_90, center.getX(), center.getZ(),
                0, level.getMinBuildHeight(), level.getMaxBuildHeight(), (x, z) -> floor).orElseThrow();
        var footprint = template.getBoundingBox(new StructurePlaceSettings().setRotation(Rotation.CLOCKWISE_90), origin);
        var piece = new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(), manager,
                SurvivingTheAftermath.asResource("city"), origin, Rotation.CLOCKWISE_90);
        var area = piece.getBoundingBox();
        check(area.getXSpan() == 128 && area.getZSpan() == 128, "City apron missing from structure references");
        // Finish all neighboring world generation before establishing a deterministic fixture.
        for (int cx = (area.minX() >> 4) - 2; cx <= (area.maxX() >> 4) + 2; cx++) {
            for (int cz = (area.minZ() >> 4) - 2; cz <= (area.maxZ() >> 4) + 2; cz++) level.getChunk(cx, cz);
        }
        // Sloping natural ground with a grass/stone shell above a 10-block-deep cavity.
        for (int x = area.minX(); x <= area.maxX(); x++) for (int z = area.minZ(); z <= area.maxZ(); z++) {
            int natural = floor + Math.floorDiv(x - center.getX(), 8);
            boolean inside = x >= footprint.minX() && x <= footprint.maxX() && z >= footprint.minZ() && z <= footprint.maxZ();
            int top = inside ? Math.max(floor, natural) : natural;
            int existing = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            for (int y = top + 1; y <= existing; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 18);
            for (int y = floor - 36; y <= top; y++) {
                var block = inside && y >= floor - 12 && y <= floor - 3 ? Blocks.AIR : y == top ? Blocks.GRASS_BLOCK : Blocks.STONE;
                level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), 18);
            }
        }
        // City generation runs after vegetation, and removes trees occupying its graded apron.
        int treeX = footprint.maxX() + 8, treeZ = center.getZ();
        int treeGround = floor + Math.floorDiv(treeX - center.getX(), 8);
        for (int y = treeGround + 1; y <= treeGround + 10; y++) level.setBlock(new BlockPos(treeX, y, treeZ), Blocks.OAK_LOG.defaultBlockState(), 18);
        level.setBlock(new BlockPos(treeX, treeGround + 11, treeZ), Blocks.OAK_LEAVES.defaultBlockState(), 18);
        var chunks = new ArrayList<ChunkPos>();
        for (int x = area.minX() >> 4; x <= area.maxX() >> 4; x++) for (int z = area.minZ() >> 4; z <= area.maxZ() >> 4; z++) chunks.add(new ChunkPos(x, z));
        java.util.Collections.shuffle(chunks, new java.util.Random(109));
        var context = StructurePieceSerializationContext.fromLevel(level);
        for (var pos : chunks) {
            var clip = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(), pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ());
            piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(109), clip, pos, origin);
            var saved = piece.createTag(context);
            check(saved.getBoolean("CityTerrainBlend"), "City grading mode was not persisted");
            piece = new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(), context, saved);
            check(piece.getBoundingBox().equals(area), "City apron was lost after placement/reload");
        }
        int checked = 0;
        for (int x = footprint.minX(); x <= footprint.maxX(); x++) for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
            for (int y = floor - 32; y < floor; y++) {
                var pos = new BlockPos(x, y, z);
                check(level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP), "Full city left a cavity at " + pos);
                checked++;
            }
        }
        for (int x = area.minX(); x <= area.maxX(); x++) for (int z = area.minZ(); z <= area.maxZ(); z++) {
            int distance = Math.max(Math.max(footprint.minX() - x, x - footprint.maxX()), Math.max(footprint.minZ() - z, z - footprint.maxZ()));
            if (distance <= 0) continue;
            int natural = floor + Math.floorDiv(x - center.getX(), 8);
            double f = Math.max(0, distance - 2) / 22.0;
            int expected = floor + (int) Math.round((natural - floor) * f * f * (3 - 2 * f));
            var surface = new BlockPos(x, expected, z);
            check(level.getBlockState(surface).is(Blocks.GRASS_BLOCK), "Apron has a seam at " + surface);
            check(level.getBlockState(surface.above()).isAir(), "Apron terrain was not cleared at " + surface + ": " + level.getBlockState(surface.above()) + ", distance=" + distance);
            check(level.getBlockState(surface.below()).isFaceSturdy(level, surface.below(), Direction.UP), "Unsupported apron at " + surface);
        }
        System.out.println("FULL CITY CHECK: chunks=" + chunks.size() + ", foundation_blocks=" + checked + ", shuffled placement and reload per chunk passed");
        h.succeed();
    }

    @GameTest(template = "stability_empty", timeoutTicks = 1200)
    public static void terrainCitySettingsAndGentleSlope(GameTestHelper h) {
        var level = h.getLevel();
        var lookup = level.registryAccess();
        var set = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET)
                .getOrThrow(com.pancake.surviving_the_aftermath.common.init.ModStructureSets.CITY_SET).value();
        check(set.placement() instanceof net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement, "Wrong city distribution");
        var spread = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement) set.placement();
        check(spread.spacing() == 32 && spread.separation() == 12, "City density data did not update");
        var city = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOrThrow(com.pancake.surviving_the_aftermath.common.init.ModStructures.CITY).value();
        check(city.step() == net.minecraft.world.level.levelgen.GenerationStep.Decoration.TOP_LAYER_MODIFICATION, "City must clear forest vegetation after it generates");
        check(city.terrainAdaptation() == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE, "City still uses floating beard_box terrain");
        for (var biome : List.of(net.minecraft.world.level.biome.Biomes.PLAINS, net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS,
                net.minecraft.world.level.biome.Biomes.FOREST, net.minecraft.world.level.biome.Biomes.BIRCH_FOREST)) {
            check(lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(biome)
                    .is(com.pancake.surviving_the_aftermath.common.init.ModTags.HAS_CITY), "City biome missing: " + biome);
        }
        var template = level.getStructureManager().getOrCreate(SurvivingTheAftermath.asResource("city"));
        for (Rotation rotation : Rotation.values()) {
            var pos = SurfaceStructurePlacement.plan(template, rotation, 0, 0, 0, -64, 320, (x, z) -> 80 + Math.floorDiv(x, 4)).orElseThrow();
            check(Math.abs(pos.getY() - 80) <= 1, "City perched on the highest point instead of grading into a gentle slope");
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty", timeoutTicks = 1200)
    public static void terrainCityFrequencySurvey(GameTestHelper h) {
        var level = h.getLevel();
        var lookup = level.registryAccess();
        var manager = level.getStructureManager();
        var noise = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var source = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(
                lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(source, noise);
        var city = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getOrThrow(com.pancake.surviving_the_aftermath.common.init.ModStructures.CITY).value();
        var spread = (net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)
                lookup.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET)
                        .getOrThrow(com.pancake.surviving_the_aftermath.common.init.ModStructureSets.CITY_SET).value().placement();
        for (long seed : new long[]{0, 42, 20260924}) {
        var state = net.minecraft.world.level.levelgen.RandomState.create(noise.value(),
                lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed);

            boolean found = false;
            int queried = 0;
            long started = System.nanoTime();
            search: for (int radius = 0; radius <= 6; radius++) {
                for (int rx = -radius; rx <= radius; rx++) for (int rz = -radius; rz <= radius; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) continue;
                    var candidate = spread.getPotentialStructureChunk(seed, rx * spread.spacing(), rz * spread.spacing());
                    queried++;
                    var context = new net.minecraft.world.level.levelgen.structure.Structure.GenerationContext(lookup, generator, generator.getBiomeSource(), state, manager, seed, candidate, level, b -> b.is(com.pancake.surviving_the_aftermath.common.init.ModTags.HAS_CITY));
                    var stub = city.findValidGenerationPoint(context);
                    if (stub.isEmpty()) continue;
                    var pos = stub.get().position();
                    System.out.println("CITY FREQUENCY SURVEY: seed=" + seed + ", tested_candidates=" + queried + ", origin=" + pos
                            + ", distance_from_0=" + Math.round(Math.hypot(pos.getX(), pos.getZ())) + ", elapsed_ms=" + (System.nanoTime() - started) / 1_000_000);
                    found = true;
                    break search;
                }
            }
            check(found, "No city found in survey window for seed " + seed);
        }
        h.succeed();
    }
}
