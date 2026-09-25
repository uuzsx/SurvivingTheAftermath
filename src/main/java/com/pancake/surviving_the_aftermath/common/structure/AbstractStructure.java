package com.pancake.surviving_the_aftermath.common.structure;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractStructure extends Structure {
    public static final ResourceLocation STRUCTURE_SUPPLIES = SurvivingTheAftermath.asResource("chests/structure_supplies");
    private static final String LOOT_PROCESSED = "surviving_the_aftermath:structure_loot_processed";


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
        for (var piece : pPieces.pieces()) {
            if (!(piece instanceof TemplateStructurePiece templatePiece)) continue;
            // The piece settings may still carry the last postProcess chunk clip.
            // Enumerate the complete template and apply this call's bounds below.
            var settings = templatePiece.placeSettings().copy().setBoundingBox(null);
            long structureSeed = mix(pLevel.getSeed() ^ templatePiece.templatePosition().asLong()
                    ^ (long) settings.getRotation().ordinal() * 0x9e3779b97f4a7c15L);
            // Select from the whole transformed template before checking this chunk's bounds.
            // Selection therefore survives chunk-order changes and saved-start reloads.
            Set<Long> rewardedBarrels = this.location().getPath().equals("city")
                    ? selectedCityBarrels(templatePiece, pLevel.getSeed()) : Set.of();
            for (var block : List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL)) {
                for (var info : templatePiece.template().filterBlocks(templatePiece.templatePosition(), settings, block)) {
                    BlockPos pos = info.pos();
                    if (!pBoundingBox.isInside(pos) || !pLevel.getBlockState(pos).is(block)) continue;
                    if (!(pLevel.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container)) continue;
                    if (container.getPersistentData().contains(LOOT_PROCESSED)) continue;
                    boolean rewarded = block != Blocks.BARREL || !this.location().getPath().equals("city")
                            || rewardedBarrels.contains(pos.asLong());
                    // An authored table or fixed inventory wins. Mark even those containers so
                    // an emptied chest cannot receive new loot if afterPlace is called again.
                    if (rewarded && !container.saveWithoutMetadata().contains("LootTable")
                            && container.isEmpty()) {
                        container.setLootTable(STRUCTURE_SUPPLIES, mix(structureSeed ^ pos.asLong()));
                    }
                    container.getPersistentData().putBoolean(LOOT_PROCESSED, true);
                    container.setChanged();
                }
            }
        }
	}

    public static Set<Long> selectedCityBarrels(TemplateStructurePiece piece, long worldSeed) {
        // Vanilla postProcess temporarily clips the piece settings to the active chunk.
        // Selection must ignore that clip, while placement itself keeps using it.
        var allTemplateSettings = piece.placeSettings().copy().setBoundingBox(null);
        var infos = piece.template().filterBlocks(piece.templatePosition(), allTemplateSettings, Blocks.BARREL);
        List<BlockPos> positions = new ArrayList<>(infos.size());
        for (var info : infos) positions.add(info.pos());
        long structureSeed = mix(worldSeed ^ piece.templatePosition().asLong()
                ^ (long) piece.placeSettings().getRotation().ordinal() * 0x9e3779b97f4a7c15L);
        return selectBarrels(positions, structureSeed);
    }

    public static Set<Long> selectBarrels(List<BlockPos> templatePositions, long structureSeed) {
        List<BlockPos> positions = new ArrayList<>(templatePositions);
        positions.sort(Comparator.<BlockPos>comparingLong(pos -> mix(structureSeed ^ pos.asLong()))
                .thenComparingLong(BlockPos::asLong));
        int count = Math.round(positions.size() / 10.0f); // 209 authored city barrels -> 21
        Set<Long> selected = new HashSet<>(count);
        for (int i = 0; i < count; i++) selected.add(positions.get(i).asLong());
        return selected;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
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
