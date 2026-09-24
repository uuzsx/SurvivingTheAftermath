package com.pancake.surviving_the_aftermath.common.structure;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.ModStructurePieceTypes;
import com.pancake.surviving_the_aftermath.common.init.ModStructureTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class CityStructure extends AbstractStructure {

	public CityStructure(StructureSettings settings) {
		super(settings);
	}

    @Override
    protected Piece createPiece(StructureTemplateManager manager, BlockPos origin, Rotation rotation) {
        return new Piece(manager, origin, rotation);
    }

    @Override
    protected java.util.Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var candidate = super.findGenerationPoint(context);
        if (candidate.isEmpty()) return candidate;
        // Piece bounds already include the full 24-block grading apron.
        var terrain = candidate.get().getPiecesBuilder().build().calculateBoundingBox();
        return com.pancake.surviving_the_aftermath.common.util.CityStructureAvoidance.findConflict(context, terrain).isPresent()
                ? java.util.Optional.empty() : candidate;
    }

	@Override
	public StructureType<?> type() {
		return ModStructureTypes.CITY.get();
	}

	@Override
	public StructurePieceType pieceType() {
		return ModStructurePieceTypes.CITY.get();
	}

	@Override
	public Identifier location() {
		return SurvivingTheAftermath.asResource("city");
	}

	public static class Piece extends AbstractStructure.Piece {

        public Piece(StructureTemplateManager manager, BlockPos origin, Rotation rotation) {
            super(ModStructurePieceTypes.CITY.get(), manager, SurvivingTheAftermath.asResource("city"), origin, rotation);
        }

		public Piece(StructurePieceSerializationContext context, CompoundTag tag) {
			super(ModStructurePieceTypes.CITY.get(), context.structureTemplateManager(), tag);
		}

		@SuppressWarnings("deprecation")
		@Override
		public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
				RandomSource rand, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
			super.postProcess(level, structureManager, generator, rand, box, chunkPos, pos);
            // The grading apron participates in chunk generation but is not city housing.
            var footprint = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
            int minX = Math.max(footprint.minX(), box.minX()), maxX = Math.min(footprint.maxX(), box.maxX());
            int minZ = Math.max(footprint.minZ(), box.minZ()), maxZ = Math.min(footprint.maxZ(), box.maxZ());
            if (minX > maxX || minZ > maxZ) return;
            BlockPos spawnPos = new BlockPos(rand.nextInt(minX, maxX + 1), this.templatePosition.getY(), rand.nextInt(minZ, maxZ + 1));
            for (int y = spawnPos.getY() + 1; y < footprint.maxY(); y++) {
                var feet = new BlockPos(spawnPos.getX(), y, spawnPos.getZ());
                BlockState state1 = level.getBlockState(feet);
                BlockState state2 = level.getBlockState(feet.above());
				if (state1.isAir() && state2.isAir() && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
					Villager villager = EntityType.VILLAGER.create(level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
					if (villager == null) return;
                    villager.snapTo(spawnPos.getX() + 0.5D, y, spawnPos.getZ() + 0.5D);
                    if (!level.noCollision(villager)) continue;
					BuiltInRegistries.VILLAGER_TYPE.getRandom(rand).ifPresent((profession) ->
							villager.setVillagerData(villager.getVillagerData().withType(profession)));
					BuiltInRegistries.VILLAGER_PROFESSION.getRandom(rand).ifPresent((profession) ->
							villager.setVillagerData(villager.getVillagerData().withProfession(profession)));
                    // No workstation existed in the original design. Keep generated relic dealers from
                    // immediately losing their authored profession before the first trade.
                    if (villager.getVillagerData().profession().value() == com.pancake.surviving_the_aftermath.common.init.ModVillagers.RELIC_DEALER.get()) villager.setVillagerXp(1);
					level.addFreshEntity(villager);
					break;
				}
			}
		}

	}
}