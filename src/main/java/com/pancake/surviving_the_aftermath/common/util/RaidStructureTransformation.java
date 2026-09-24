package com.pancake.surviving_the_aftermath.common.util;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Placement rules shared by real wave transformations and their placement regression tests. */
public final class RaidStructureTransformation {
    private RaidStructureTransformation() {}

    public static StructurePlaceSettings settings(Rotation rotation, RandomSource random) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
                .addProcessor(new BlockIgnoreProcessor(ImmutableList.of(Blocks.AIR, Blocks.STRUCTURE_BLOCK, Blocks.NETHER_PORTAL, Blocks.OBSIDIAN)))
                .addProcessor(new StructureProcessor() {

                    @Override
                    @NotNull
                    protected StructureProcessorType<?> getType() {
                        return null;
                    }

                    @Override
                    public StructureTemplate.StructureBlockInfo process(@NotNull LevelReader levelReader,
                                                                        @NotNull BlockPos origin,
                                                                        @NotNull BlockPos referencePos,
                                                                        @NotNull StructureTemplate.StructureBlockInfo blockInfo,
                                                                        @NotNull StructureTemplate.StructureBlockInfo relativeBlockInfo,
                                                                        @NotNull StructurePlaceSettings settings,
                                                                        @Nullable StructureTemplate template) {
                        BlockState current = levelReader.getBlockState(relativeBlockInfo.pos());
                        if (current.is(Blocks.OBSIDIAN) || current.is(Blocks.NETHER_PORTAL) || random.nextFloat() < 0.9) {
                            // A world-space state returned here would be rotated again by placeInWorld.
                            // Null skips placement entirely, preserving unchanged blocks and their data.
                            return null;
                        }
                        BlockState replacement = relativeBlockInfo.state();
                        if (current.getBlock() instanceof StairBlock && replacement.getBlock() instanceof StairBlock) {
                            replacement = replacement.setValue(StairBlock.FACING, current.getValue(StairBlock.FACING))
                                    .setValue(StairBlock.HALF, current.getValue(StairBlock.HALF))
                                    .setValue(StairBlock.SHAPE, current.getValue(StairBlock.SHAPE))
                                    .setValue(StairBlock.WATERLOGGED, current.getValue(StairBlock.WATERLOGGED));
                            // Convert the desired world geometry back to template space. Minecraft
                            // applies mirror, then rotation, after all processors have finished.
                            Rotation inverse = switch (settings.getRotation()) {
                                case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
                                case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
                                default -> settings.getRotation();
                            };
                            replacement = replacement.rotate(inverse).mirror(settings.getMirror());
                            return new StructureTemplate.StructureBlockInfo(relativeBlockInfo.pos(), replacement, relativeBlockInfo.nbt());
                        }
                        return relativeBlockInfo;
                    }
                });
    }
}
