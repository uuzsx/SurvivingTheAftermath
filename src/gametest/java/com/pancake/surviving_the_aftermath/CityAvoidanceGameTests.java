package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.structure.AbstractStructure;
import com.pancake.surviving_the_aftermath.common.util.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

@net.minecraftforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class CityAvoidanceGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }

    @GameTest(template = "stability_empty", timeoutTicks = 600)
    public static void cityRejectsVillageCollision(GameTestHelper h) {
        var level = h.getLevel();
        var lookup = level.registryAccess();
        var manager = level.getStructureManager();
        var noise = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var source = MultiNoiseBiomeSource.createFromPreset(lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        var generator = new NoiseBasedChunkGenerator(source, noise);
        long seed = 0L;
        var randomState = RandomState.create(noise.value(), lookup.lookupOrThrow(Registries.NOISE), seed);
        var villages = lookup.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(BuiltinStructureSets.VILLAGES).value();
        var placement = (RandomSpreadStructurePlacement) villages.placement();
        var city = lookup.lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        var template = manager.getOrCreate(SurvivingTheAftermath.asResource("city"));
        long began = System.nanoTime();
        for (int radius = 0; radius <= 5; radius++) for (int rx = -radius; rx <= radius; rx++) for (int rz = -radius; rz <= radius; rz++) {
            if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) continue;
            var villageChunk = placement.getPotentialStructureChunk(seed, rx * placement.spacing(), rz * placement.spacing());
            var basis = new Structure.GenerationContext(lookup, generator, source, randomState, manager, seed, villageChunk, level, b -> true);
            var village = CityStructureAvoidance.predict(basis, villages, villageChunk);
            if (!village.isValid()) continue;
            // A different start chunk: center-distance-only checks would miss village outlying houses.
            for (int[] shift : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
                var cityChunk = new ChunkPos((villageChunk.getMinBlockX() >> 4) + shift[0], (villageChunk.getMinBlockZ() >> 4) + shift[1]);
                var context = new Structure.GenerationContext(lookup, generator, source, randomState, manager, seed, cityChunk, level, b -> true);
                var rotation = Rotation.getRandom(context.random());
                var origin = SurfaceStructurePlacement.findOrigin(context, template, rotation, 0);
                if (origin.isEmpty()) continue;
                var terrain = SurfaceStructurePlacement.cityTerrainBounds(template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), origin.get()));
                if (!CityStructureAvoidance.overlaps(terrain, village.getBoundingBox())) continue;
                var again = new Structure.GenerationContext(lookup, generator, source, randomState, manager, seed, cityChunk, level, b -> true);
                check(city.findValidGenerationPoint(again).isEmpty(), "City accepted a site overlapping a neighboring village");
                var conflict = CityStructureAvoidance.findConflict(again, terrain);
                check(conflict.isPresent(), "No nearby building was detected");
                check(CityStructureAvoidance.predict(basis, villages, villageChunk).getBoundingBox().equals(village.getBoundingBox()),
                        "Structure preview changed with call order");
                System.out.println("CITY AVOIDANCE CHECK: village=" + villageChunk + ", rejected_city=" + cityChunk
                        + ", elapsed_ms=" + (System.nanoTime() - began) / 1_000_000);
                h.succeed();
                return;
            }
        }
        throw new GameTestAssertException("No village/city collision fixture found");
    }

    @GameTest(template = "stability_empty", timeoutTicks = 600)
    public static void cityGradingPreservesRegisteredBuilding(GameTestHelper h) {
        var level = h.getLevel();
        var manager = level.getStructureManager();
        var origin = h.absolutePos(new BlockPos(30000, 80, 30000));
        var city = new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(), manager, SurvivingTheAftermath.asResource("city"), origin, Rotation.NONE);
        var footprint = city.template().getBoundingBox(city.placeSettings(), origin);
        var houseOrigin = new BlockPos(footprint.maxX() + 8, origin.getY() - 5, footprint.minZ() + 32);
        var house = new AbstractStructure.Piece(ModStructurePieceTypes.CONSTRUCTION_1.get(), manager,
                SurvivingTheAftermath.asResource("construction1"), houseOrigin, Rotation.NONE);
        var houseBounds = house.getBoundingBox();
        var chunk = new ChunkPos(houseOrigin.getX() >> 4, houseOrigin.getZ() >> 4);
        // The fixture lies outside the GameTest area's normal chunk tickets.
        level.setChunkForced(chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4, true);
        var writable = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), level.getMinBuildHeight() + level.getHeight() - 1, chunk.getMaxBlockZ());
        var building = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CONSTRUCTION_1).value();
        var registeredCity = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        var cityStart = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
        level.getChunk(chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4).setStartForStructure(building,
                new StructureStart(building, chunk, 0, new PiecesContainer(List.of(house))));
        level.getChunk(cityStart.getMinBlockX() >> 4, cityStart.getMinBlockZ() >> 4).setStartForStructure(registeredCity,
                new StructureStart(registeredCity, cityStart, 0, new PiecesContainer(List.of(city))));
        var loaded = level.getChunk(chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4);
        loaded.addReferenceForStructure(building, key(chunk));
        loaded.addReferenceForStructure(registeredCity, key(cityStart));

        // Fixture contains a roof, walls, empty interior, natural foliage and a stocked chest.
        for (int x = Math.max(houseBounds.minX(), writable.minX()); x <= Math.min(houseBounds.maxX(), writable.maxX()); x++) {
            for (int z = Math.max(houseBounds.minZ(), writable.minZ()); z <= Math.min(houseBounds.maxZ(), writable.maxZ()); z++) {
                for (int y = houseBounds.minY(); y <= houseBounds.maxY(); y++) {
                    var state = y == houseBounds.minY() ? Blocks.COBBLESTONE.defaultBlockState()
                            : y == houseBounds.maxY() ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlock(new BlockPos(x, y, z), state, 2);
                }
            }
        }
        var chestPos = houseOrigin.above();
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        ((ChestBlockEntity) level.getBlockEntity(chestPos)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        var leaf = houseOrigin.above(2);
        level.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false)
                .setValue(BlockStateProperties.DISTANCE, 1), 2);
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (int x = Math.max(houseBounds.minX(), writable.minX()); x <= Math.min(houseBounds.maxX(), writable.maxX()); x++)
            for (int z = Math.max(houseBounds.minZ(), writable.minZ()); z <= Math.min(houseBounds.maxZ(), writable.maxZ()); z++)
                for (int y = houseBounds.minY() - 1; y <= houseBounds.maxY() + 2; y++) {
                    var pos = new BlockPos(x, y, z);
                    before.put(pos, level.getBlockState(pos));
                }
        // Reload the city piece before placement to test the production path on old saved starts.
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        city = new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(), serialization, city.createTag(serialization));
        city.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(1), writable, chunk, origin);
        for (var entry : before.entrySet()) check(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                "City grading changed another building at " + entry.getKey());
        check(((ChestBlockEntity) level.getBlockEntity(chestPos)).getItem(0).getCount() == 7, "Grading changed chest contents");
        CityVegetationData.get(level).setCleaned(key(chunk), false);
        CityVegetationCleanup.queue(level, chunk, true);
        h.startSequence().thenWaitUntil(() -> check(CityVegetationData.get(level).isCleaned(key(chunk)), "Foliage pass did not run"))
                .thenExecute(() -> {
                    check(level.getBlockState(leaf).is(Blocks.OAK_LEAVES), "City cleanup deleted a neighboring building's foliage");
                    level.setChunkForced(chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4, false);
                    System.out.println("CITY BUILDING PROTECTION CHECK: preserved_blocks=" + before.size() + ", chest and foliage retained");
                }).thenSucceed();
    }

    private static long key(ChunkPos chunk) {
        return ((chunk.getMinBlockX() >> 4) & 0xffffffffL) | ((long) (chunk.getMinBlockZ() >> 4) << 32);
    }
}
