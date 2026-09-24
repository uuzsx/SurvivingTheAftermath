package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.common.util.RaidStructureTransformation;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.List;

@net.neoforged.neoforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class StairTransformationGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
    private static RandomSource selection(boolean change) {
        return new LegacyRandomSource(0) { @Override public float nextFloat() { return change ? 0.95F : 0.5F; } };
    }
    private static StructureTemplate template(ServerLevel level, BlockPos source, Block material) {
        level.setBlock(source, material.defaultBlockState(), 18);
        var template = new StructureTemplate();
        template.fillFromWorld(level, source, new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 18);
        return template;
    }
    private static void place(ServerLevel level, BlockPos pos, StructureTemplate template,
                              Rotation rotation, boolean change, boolean knownShape) {
        var settings = RaidStructureTransformation.settings(rotation, selection(change)).setKnownShape(knownShape);
        template.placeInWorld(level, pos, pos, settings, RandomSource.create(17), 2);
    }
    private static void geometry(BlockState expected, BlockState actual, String context) {
        check(actual.getBlock() instanceof StairBlock, context + ": stair disappeared");
        check(expected.getValue(StairBlock.FACING).equals(actual.getValue(StairBlock.FACING)), context + ": FACING changed");
        check(expected.getValue(StairBlock.HALF).equals(actual.getValue(StairBlock.HALF)), context + ": HALF changed");
        check(expected.getValue(StairBlock.SHAPE).equals(actual.getValue(StairBlock.SHAPE)), context + ": SHAPE changed");
        check(expected.getValue(StairBlock.WATERLOGGED).equals(actual.getValue(StairBlock.WATERLOGGED)), context + ": WATERLOGGED changed");
    }
    @GameTest(template = "stability_empty")
    public static void skippedBlocksKeepWorldState(GameTestHelper h) {
        var level = h.getLevel();
        var source = h.absolutePos(new BlockPos(1, 3, 1));
        var target = h.absolutePos(new BlockPos(5, 3, 5));
        var nether = template(level, source, Blocks.NETHER_BRICK_STAIRS);
        for (Rotation rotation : Rotation.values()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) for (Half half : Half.values()) {
                var original = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
                level.setBlock(target, original, 18);
                for (int wave = 0; wave < 11; wave++) {
                    place(level, target, nether, rotation, false, false);
                    check(level.getBlockState(target).equals(original), "Skipped stair rotated: " + rotation + ", wave " + wave);
                }
            }
            for (var state : List.of(Blocks.OBSIDIAN.defaultBlockState(),
                    Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X),
                    Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.Z))) {
                level.setBlock(target, state, 18);
                place(level, target, nether, rotation, true, false);
                check(level.getBlockState(target).equals(state), "Protected portal frame/axis changed: " + rotation);
            }
        }
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 18);
        h.succeed();
    }
    @GameTest(template = "stability_empty")
    public static void convertedStairsKeepProperties(GameTestHelper h) {
        var level = h.getLevel();
        var source = h.absolutePos(new BlockPos(1, 3, 1));
        var target = h.absolutePos(new BlockPos(5, 3, 5));
        var first = template(level, source, Blocks.NETHER_BRICK_STAIRS);
        var second = template(level, source, Blocks.RED_NETHER_BRICK_STAIRS);
        for (Rotation rotation : Rotation.values()) for (Direction facing : Direction.Plane.HORIZONTAL)
            for (Half half : Half.values()) for (StairsShape shape : StairsShape.values()) for (boolean water : new boolean[]{false, true}) {
                var original = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing)
                        .setValue(StairBlock.HALF, half).setValue(StairBlock.SHAPE, shape).setValue(StairBlock.WATERLOGGED, water);
                level.setBlock(target, original, 18);
                for (int wave = 0; wave < 11; wave++) {
                    // Preserve authored corner states here; the next fixture separately exercises normal neighbor updates.
                    place(level, target, wave % 2 == 0 ? first : second, rotation, true, true);
                    var actual = level.getBlockState(target);
                    check(actual.is(wave % 2 == 0 ? Blocks.NETHER_BRICK_STAIRS : Blocks.RED_NETHER_BRICK_STAIRS), "Material did not transform");
                    geometry(original, actual, "Converted " + rotation + "/" + facing + "/" + shape + ", wave " + wave);
                }
            }
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 18);
        h.succeed();
    }
    @GameTest(template = "stability_empty")
    public static void mixedWavesKeepStairGeometry(GameTestHelper h) {
        var level = h.getLevel();
        var source = h.absolutePos(new BlockPos(1, 3, 1));
        var target = h.absolutePos(new BlockPos(5, 3, 5));
        var nether = template(level, source, Blocks.NETHER_BRICK_STAIRS);
        for (Rotation rotation : Rotation.values()) for (Direction facing : Direction.Plane.HORIZONTAL)
            for (Half half : Half.values()) for (StairsShape shape : StairsShape.values()) {
                for (Direction side : Direction.Plane.HORIZONTAL) level.setBlock(target.relative(side), Blocks.AIR.defaultBlockState(), 18);
                var original = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
                if (shape != StairsShape.STRAIGHT) {
                    boolean outer = shape == StairsShape.OUTER_LEFT || shape == StairsShape.OUTER_RIGHT;
                    boolean left = shape == StairsShape.OUTER_LEFT || shape == StairsShape.INNER_LEFT;
                    var neighbor = original.setValue(StairBlock.FACING, left ? facing.getCounterClockWise() : facing.getClockWise());
                    level.setBlock(target.relative(outer ? facing : facing.getOpposite()), neighbor, 18);
                }
                original = Block.updateFromNeighbourShapes(original, level, target);
                check(original.getValue(StairBlock.SHAPE) == shape, "Invalid natural corner fixture");
                level.setBlock(target, original, 18);
                for (int wave = 0; wave < 11; wave++) {
                    place(level, target, nether, rotation, wave % 2 == 1, false);
                    geometry(original, level.getBlockState(target), "Mixed waves " + rotation + "/" + facing + "/" + shape + ", wave " + wave);
                    if (wave >= 1) check(level.getBlockState(target).is(Blocks.NETHER_BRICK_STAIRS), "Nether material reverted");
                }
            }
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 18);
        for (Direction side : Direction.Plane.HORIZONTAL) level.setBlock(target.relative(side), Blocks.AIR.defaultBlockState(), 18);
        h.succeed();
    }
}
