package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.common.util.CityVegetationData;
import com.pancake.surviving_the_aftermath.common.init.ModStructurePieceTypes;
import com.pancake.surviving_the_aftermath.common.init.ModStructures;
import com.pancake.surviving_the_aftermath.common.structure.AbstractStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import java.util.List;

@net.neoforged.neoforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class CityVegetationGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
    private record Site(ServerLevel level, Structure city, ChunkPos start, AbstractStructure.Piece piece) {
        void reference(BlockPos pos) {
            CityVegetationData.get(level).setCleaned(chunkKey(pos), false);
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4).addReferenceForStructure(city,
                    ((start.getMinBlockX() >> 4) & 0xffffffffL) | ((long) (start.getMinBlockZ() >> 4) << 32));
        }
    }
    private static Site site(GameTestHelper h, int index, boolean oldSave) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(16384 + index * 512, 90, 16384));
        var city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        var piece = new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(), level.getStructureManager(),
                SurvivingTheAftermath.asResource("city"), origin, Rotation.NONE);
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        var tag = piece.createTag(serialization);
        if (oldSave) tag.remove("CityTerrainBlend");
        piece = new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(), serialization, tag);
        var start = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
        level.getChunk(start.getMinBlockX() >> 4, start.getMinBlockZ() >> 4).setStartForStructure(city,
                new StructureStart(city, start, 0, new PiecesContainer(List.of(piece))));
        return new Site(level, city, start, piece);
    }
    private static long chunkKey(BlockPos pos) {
        return ((pos.getX() >> 4) & 0xffffffffL) | ((long) (pos.getZ() >> 4) << 32);
    }
    private static void leaf(ServerLevel level, BlockPos pos, boolean persistent) {
        level.setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, persistent)
                .setValue(BlockStateProperties.DISTANCE, 1), 2);
    }
    private static void loaded(ServerLevel level, BlockPos pos, boolean fresh) {
        var chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Load(chunk, fresh));
    }

    @GameTest(template = "stability_empty", timeoutTicks = 600)
    public static void cityLateCanopiesAreRemoved(GameTestHelper h) {
        var site = site(h, 0, false);
        var level = site.level();
        // A canopy extends back into a city's already-processed chunk from its east neighbor.
        var canopy = site.piece().templatePosition().offset(18, 27, 18);
        site.reference(canopy);
        var neighbor = canopy.offset(16, 0, 0);
        site.reference(neighbor);
        leaf(level, canopy, false);
        loaded(level, canopy, true);
        check(!level.getBlockState(canopy).isAir(), "Chunk load callback edited the world before FULL promotion");
        h.startSequence().thenWaitUntil(() -> check(level.getBlockState(canopy).isAir(), "Initial canopy remained"))
                .thenExecute(() -> {
                    // Arrives after the first cleanup, just as a later decoration pass would.
                    leaf(level, canopy, false);
                    loaded(level, neighbor, true);
                    check(!level.getBlockState(canopy).isAir(), "Neighbor load cleanup was not deferred");
                }).thenWaitUntil(() -> check(level.getBlockState(canopy).isAir(), "Neighbor's late canopy remained above the roof"))
                .thenSucceed();
    }

    @GameTest(template = "stability_empty", timeoutTicks = 600)
    public static void citySavedCanopiesKeepPlayerDecorations(GameTestHelper h) {
        var site = site(h, 1, true);
        var level = site.level();
        var natural = site.piece().templatePosition().offset(18, 25, 18);
        var placed = natural.offset(1, 0, 0);
        var beam = natural.below();
        var forest = new BlockPos(site.piece().getBoundingBox().maxX() + 1, natural.getY(), natural.getZ());
        for (var pos : List.of(natural, placed, forest)) site.reference(pos);
        level.setBlock(beam, Blocks.OAK_LOG.defaultBlockState(), 2);
        leaf(level, natural, false);
        leaf(level, placed, true);
        leaf(level, forest, false);
        // Repair an existing city's chunk from disk, including leaves which stay attached to
        // the city's decorative wooden beams and therefore never naturally decay.
        loaded(level, natural, false);
        loaded(level, forest, false);
        h.startSequence().thenWaitUntil(() -> check(level.getBlockState(natural).isAir(), "Saved city's natural foliage remained"))
                .thenExecute(() -> {
                    check(level.getBlockState(placed).is(Blocks.OAK_LEAVES), "Player's persistent decoration was deleted");
                    check(level.getBlockState(forest).is(Blocks.OAK_LEAVES), "Forest outside the city's saved bounds was deleted");
                    check(level.getBlockState(beam).is(Blocks.OAK_LOG), "City's wooden beam was deleted");
                    var data = CityVegetationData.get(level);
                    check(data.isDirty(), "Cleanup marker was not marked for saving");
                    check(CityVegetationData.load(data.write(new net.minecraft.nbt.CompoundTag()))
                            .isCleaned(chunkKey(natural)), "Cleanup marker did not survive serialization");
                }).thenIdle(80).thenExecute(() -> {
                    // Later player landscaping must not be repeatedly erased on ordinary reload.
                    leaf(level, natural, false);
                    loaded(level, natural, false);
                }).thenIdle(10).thenExecute(() -> {
                    check(level.getBlockState(natural).is(Blocks.OAK_LEAVES), "Ordinary reload repeated the one-time cleanup");
                }).thenSucceed();
    }
}
