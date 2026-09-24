package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.common.structure.CityStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Predicts neighboring structure starts without loading chunks or writing world blocks. */
public final class CityStructureAvoidance {
    private CityStructureAvoidance() {}
    public static final int CLEARANCE = 8;
    // Vanilla creates structure references from starts up to eight chunks away.
    private static final int START_REACH = 128;
    private static final int FOUNDATION_DEPTH = 36;

    public static Optional<StructureStart> findConflict(Structure.GenerationContext context, BoundingBox terrain) {
        var state = context.chunkGenerator().createState(context.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET),
                context.randomState(), context.seed());
        int minX = (terrain.minX() - CLEARANCE - START_REACH) >> 4;
        int maxX = (terrain.maxX() + CLEARANCE + START_REACH) >> 4;
        int minZ = (terrain.minZ() - CLEARANCE - START_REACH) >> 4;
        int maxZ = (terrain.maxZ() + CLEARANCE + START_REACH) >> 4;
        for (var holder : state.possibleStructureSets()) {
            var set = holder.value();
            if (set.structures().stream().noneMatch(e -> surface(e.structure().value()))) continue;
            var placement = set.placement();
            // Random-spread sets need one test per placement region, not per chunk.
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                for (int rx = Math.floorDiv(minX, spread.spacing()); rx <= Math.floorDiv(maxX, spread.spacing()); rx++) {
                    for (int rz = Math.floorDiv(minZ, spread.spacing()); rz <= Math.floorDiv(maxZ, spread.spacing()); rz++) {
                        var candidate = spread.getPotentialStructureChunk(context.seed(), rx * spread.spacing(), rz * spread.spacing());
                        int x = candidate.getMinBlockX() >> 4, z = candidate.getMinBlockZ() >> 4;
                        if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                        var conflict = at(context, state, set, x, z, terrain);
                        if (conflict.isPresent()) return conflict;
                    }
                }
            } else {
                for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                    var conflict = at(context, state, set, x, z, terrain);
                    if (conflict.isPresent()) return conflict;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean surface(Structure structure) {
        return !(structure instanceof CityStructure)
                && structure.step().ordinal() >= GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal();
    }

    private static Optional<StructureStart> at(Structure.GenerationContext context, ChunkGeneratorStructureState state,
                                              StructureSet set, int x, int z, BoundingBox terrain) {
        if (!set.placement().isStructureChunk(state, x, z)) return Optional.empty();
        var start = predict(context, set, new ChunkPos(x, z));
        return start.isValid() && overlaps(terrain, start.getBoundingBox()) ? Optional.of(start) : Optional.empty();
    }

    /** Same weighted fallback order and per-start random seed as ChunkGenerator.createStructures. */
    public static StructureStart predict(Structure.GenerationContext context, StructureSet set, ChunkPos chunk) {
        var options = new ArrayList<>(set.structures());
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(context.seed(), chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4);
        int total = options.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while (!options.isEmpty()) {
            int choice = random.nextInt(total), index = 0;
            for (var option : options) {
                choice -= option.weight();
                if (choice < 0) break;
                index++;
            }
            var selected = options.remove(index);
            total -= selected.weight();
            var structure = selected.structure().value();
            // City sets are separate by default. Never recursively preview another city.
            if (structure instanceof CityStructure) return StructureStart.INVALID_START;
            var preview = new Structure.GenerationContext(context.registryAccess(), context.chunkGenerator(),
                    context.biomeSource(), context.randomState(), context.structureTemplateManager(), context.seed(),
                    chunk, context.heightAccessor(), structure.biomes()::contains);
            var stub = structure.findValidGenerationPoint(preview);
            if (stub.isPresent()) {
                var start = new StructureStart(structure, chunk, 0, stub.get().getPiecesBuilder().build());
                if (start.isValid()) return start;
            }
        }
        return StructureStart.INVALID_START;
    }

    public static boolean overlaps(BoundingBox terrain, BoundingBox building) {
        return building.maxY() >= terrain.minY() - FOUNDATION_DEPTH
                && building.intersects(terrain.minX() - CLEARANCE, terrain.minZ() - CLEARANCE,
                        terrain.maxX() + CLEARANCE, terrain.maxZ() + CLEARANCE);
    }

    /** Also protects actual saved starts when old cities or changed datapacks are encountered. */
    public static List<BoundingBox> protectedBuildings(StructureManager structures, ChunkPos chunk, int groundY) {
        var boxes = new ArrayList<BoundingBox>();
        for (var start : structures.startsForStructure(chunk, s -> !(s instanceof CityStructure))) {
            if (start.isValid() && start.getBoundingBox().maxY() >= groundY - FOUNDATION_DEPTH) {
                boxes.add(start.getBoundingBox());
            }
        }
        return boxes;
    }

    public static boolean protectedColumn(List<BoundingBox> buildings, int x, int z) {
        for (var box : buildings) if (box.intersects(x, z, x, z)) return true;
        return false;
    }
}
