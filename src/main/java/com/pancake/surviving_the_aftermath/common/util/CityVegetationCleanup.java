package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.common.structure.CityStructure;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;

/** Neighboring chunks can place foliage after this city's own decoration step. */
public final class CityVegetationCleanup {
    private CityVegetationCleanup() {}
    private static final Map<ServerLevel, LinkedHashMap<Long, Boolean>> PENDING = new WeakHashMap<>();
    private static long key(int x, int z) { return (x & 0xffffffffL) | ((long) z << 32); }

    /** Chunk load events may run before FULL; do not read or change the world here. */
    public static void queue(ServerLevel level, ChunkPos pos, boolean newChunk) {
        int x = pos.getMinBlockX() >> 4, z = pos.getMinBlockZ() >> 4;
        synchronized (PENDING) {
            var pending = PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
            pending.merge(key(x, z), newChunk, (a, b) -> a || b);
            // A later neighboring decoration pass may have written leaves into a loaded city.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) pending.merge(key(x + dx, z + dz), newChunk, (a, b) -> a || b);
            // Do not accumulate an unbounded backlog when rapidly exploring unloaded terrain.
            while (pending.size() > 8192) pending.remove(pending.keySet().iterator().next());
        }
    }

    public static void clear(ServerLevel level) {
        synchronized (PENDING) { PENDING.remove(level); }
    }

    /** Bounded work on the server thread; never request a new full chunk. */
    public static void tick(ServerLevel level) {
        int cities = 0;
        for (int inspected = 0; inspected < 32 && cities < 2; inspected++) {
            long next;
            boolean freshNeighbor;
            synchronized (PENDING) {
                var pending = PENDING.get(level);
                if (pending == null || pending.isEmpty()) return;
                var iterator = pending.entrySet().iterator();
                var entry = iterator.next();
                next = entry.getKey();
                freshNeighbor = entry.getValue();
                iterator.remove();
            }
            var chunk = level.getChunkSource().getChunkNow((int) next, (int) (next >> 32));
            if (chunk == null || chunk.getAllReferences().keySet().stream().noneMatch(s -> s instanceof CityStructure)) continue;
            var data = CityVegetationData.get(level);
            if (!freshNeighbor && data.isCleaned(next)) continue;
            if (clean(level, chunk)) {
                data.setCleaned(next, true);
                cities++;
            }
        }
    }

    private static boolean clean(ServerLevel level, LevelChunk chunk) {
        var cursor = new BlockPos.MutableBlockPos();
        for (var start : level.structureManager().startsForStructure(chunk.getPos(), s -> s instanceof CityStructure)) {
            for (var piece : start.getPieces()) {
                if (!(piece instanceof TemplateStructurePiece template)) continue;
                var footprint = template.template().getBoundingBox(template.placeSettings(), template.templatePosition());
                var area = piece.getBoundingBox();
                int minY = Math.max(level.getMinY(), footprint.minY() - SurfaceStructurePlacement.CITY_BLEND_RADIUS);
                for (int x = Math.max(area.minX(), chunk.getPos().getMinBlockX()); x <= Math.min(area.maxX(), chunk.getPos().getMaxBlockX()); x++) {
                    for (int z = Math.max(area.minZ(), chunk.getPos().getMinBlockZ()); z <= Math.min(area.maxZ(), chunk.getPos().getMaxBlockZ()); z++) {
                        int distance = Math.max(Math.max(footprint.minX() - x, x - footprint.maxX()),
                                Math.max(footprint.minZ() - z, z - footprint.maxZ()));
                        // This edge is deliberately untouched by gradeCity, including its trees.
                        if (distance >= SurfaceStructurePlacement.CITY_BLEND_RADIUS) continue;
                        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                        for (int y = minY; y <= top; y++) {
                            cursor.set(x, y, z);
                            var state = chunk.getBlockState(cursor);
                            if (!state.is(BlockTags.LEAVES) || state.hasBlockEntity()) continue;
                            // Player-placed decorative leaves must survive loading/reloading.
                            if (state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT)) continue;
                            // No drops. Neighbor updates also let orphaned foliage at the outside
                            // edge recalculate its normal leaf distance, without clearing the forest.
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
        return true;
    }
}
