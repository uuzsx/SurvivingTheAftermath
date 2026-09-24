package com.pancake.surviving_the_aftermath.common.structure;

import com.pancake.surviving_the_aftermath.common.util.SurfaceStructurePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.Optional;

public abstract class AbstractStructure extends Structure {

    public AbstractStructure(StructureSettings settings) {
        super(settings);
    }

    // Use the same specialized piece for fresh generation and saved-start reloads.
    protected Piece createPiece(StructureTemplateManager manager, BlockPos origin, Rotation rotation) {
        return new Piece(this.pieceType(), manager, this.location(), origin, rotation);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        var template = context.structureTemplateManager().getOrCreate(this.location());
        int groundOffset = SurfaceStructurePlacement.groundOffset(this.location().getPath());
        return SurfaceStructurePlacement.findOrigin(context, template, rotation, groundOffset).map(origin ->
                new GenerationStub(new BlockPos(context.chunkPos().getMiddleBlockX(), origin.getY(), context.chunkPos().getMiddleBlockZ()),
                        pieces -> pieces.addPiece(this.createPiece(context.structureTemplateManager(), origin, rotation))));
    }
    
	@Override
	public void afterPlace(WorldGenLevel pLevel, StructureManager pStructureManager, ChunkGenerator pChunkGenerator,
			RandomSource pRandom, BoundingBox pBoundingBox, ChunkPos pChunkPos, PiecesContainer pPieces) {
        // Only fallback treasure chests authored into this template receive a table.
        // Decorative barrels and containers outside the template are left untouched.
        for (var piece : pPieces.pieces()) {
            if (!(piece instanceof TemplateStructurePiece templatePiece)) continue;
            var settings = templatePiece.placeSettings();
            for (var block : java.util.List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST)) {
                for (var info : templatePiece.template().filterBlocks(templatePiece.templatePosition(), settings, block)) {
                    if (!pBoundingBox.isInside(info.pos()) || !pLevel.getBlockState(info.pos()).is(block)) continue;
                    if (pLevel.getBlockEntity(info.pos()) instanceof RandomizableContainerBlockEntity chest
                            && !chest.saveWithoutMetadata().contains("LootTable") && chest.isEmpty()) {
                        chest.setLootTable(BuiltInLootTables.DESERT_PYRAMID, pRandom.nextLong());
                    }
                }
            }
        }
	}

    @Override
    public abstract StructureType<?> type();

    public abstract StructurePieceType pieceType();

    public abstract ResourceLocation location();

    public static class Piece extends TemplateStructurePiece {
        private final boolean grounded;
        private final boolean cityBlend;

        public Piece(StructurePieceType type, StructureTemplateManager structureTemplateManager, ResourceLocation location, BlockPos templatePosition, Rotation rotation) {
            super(type, 0, structureTemplateManager, location, location.toString(), SurfaceStructurePlacement.settings(rotation, true, SurfaceStructurePlacement.groundOffset(location.getPath()), location.getPath().equals("city") ? structureTemplateManager.getOrCreate(location) : null), templatePosition);
            this.grounded = true;
            this.cityBlend = location.getPath().equals("city");
            refreshBounds();
        }

        public Piece(StructurePieceType type, StructureTemplateManager structureManager, CompoundTag tag) {
            super(type, tag, structureManager, (location) -> SurfaceStructurePlacement.settings(Rotation.valueOf(tag.getString("rot")), tag.getBoolean("SurfaceGrounded"), SurfaceStructurePlacement.groundOffset(location.getPath()), location.getPath().equals("city") ? structureManager.getOrCreate(location) : null));
            this.grounded = tag.getBoolean("SurfaceGrounded");
            this.cityBlend = this.grounded && tag.getBoolean("CityTerrainBlend");
            refreshBounds();
        }

        public Piece(StructurePieceType type, StructurePieceSerializationContext context, CompoundTag tag) {
            this(type, context.structureTemplateManager(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                                RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos reference) {
            if (this.grounded) {
                int offset = SurfaceStructurePlacement.groundOffset(this.makeTemplateLocation().getPath());
                var footprint = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
                if (this.cityBlend) SurfaceStructurePlacement.gradeCity(level, footprint, chunk, this.templatePosition.getY() - offset,
                        com.pancake.surviving_the_aftermath.common.util.CityStructureAvoidance.protectedBuildings(structures, chunkPos, this.templatePosition.getY() - offset));
                else SurfaceStructurePlacement.support(level, footprint, chunk, this.templatePosition.getY() - offset);
            }
            try {
                if (this.template.getBoundingBox(this.placeSettings, this.templatePosition).intersects(chunk)) {
                    super.postProcess(level, structures, generator, random, chunk, chunkPos, reference);
                }
            } finally {
                // Vanilla replaces boundingBox with the template bounds. Retain the apron for
                // subsequent chunks, StructureStart references and saved/reloaded pieces.
                refreshBounds();
            }
        }

        private void refreshBounds() {
            var footprint = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
            this.boundingBox = this.cityBlend ? SurfaceStructurePlacement.cityTerrainBounds(footprint) : footprint;
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("rot", this.placeSettings.getRotation().name());
            tag.putBoolean("SurfaceGrounded", this.grounded);
            tag.putBoolean("CityTerrainBlend", this.cityBlend);
        }

        
    }
    
}
