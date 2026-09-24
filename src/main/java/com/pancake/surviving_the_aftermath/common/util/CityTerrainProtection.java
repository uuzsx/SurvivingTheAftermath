package com.pancake.surviving_the_aftermath.common.util;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Conservative, bounded reads of existing terrain; never loads another chunk. */
public final class CityTerrainProtection {
    private CityTerrainProtection() {}
    public static final int TRANSITION_RADIUS = 8;
    public static final int MAX_EDGE_CHANGE = 2;
    private static final int BUFFER = 2;

    public static int distance(BoundingBox footprint, int x, int z) {
        return Math.max(Math.max(footprint.minX() - x, x - footprint.maxX()),
                Math.max(footprint.minZ() - z, z - footprint.maxZ()));
    }

    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }

    private static boolean replaceableTerrain(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()
                || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) return false;
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK);
    }

    /** Snapshot obstacles before grading, so write order cannot alter the protected margin. */
    public static Set<Long> protectedApron(WorldGenLevel level, BoundingBox footprint, BoundingBox chunk,
                                          int groundY, int minY, List<BoundingBox> buildings) {
        var protectedColumns = new HashSet<Long>();
        var cursor = new BlockPos.MutableBlockPos();
        int minX = Math.max(footprint.minX() - TRANSITION_RADIUS, chunk.minX()) - BUFFER;
        int maxX = Math.min(footprint.maxX() + TRANSITION_RADIUS, chunk.maxX()) + BUFFER;
        int minZ = Math.max(footprint.minZ() - TRANSITION_RADIUS, chunk.minZ()) - BUFFER;
        int maxZ = Math.min(footprint.maxZ() + TRANSITION_RADIUS, chunk.maxZ()) + BUFFER;
        for (int x=minX;x<=maxX;x++) for (int z=minZ;z<=maxZ;z++) {
            if (distance(footprint,x,z)<=0) continue;
            boolean obstacle = CityStructureAvoidance.protectedColumn(buildings,x,z) || !level.hasChunk(x>>4,z>>4);
            if (!obstacle) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
                for (int y=top;y>=Math.max(minY,Math.min(top-4,groundY-16));y--) {
                    if (!replaceableTerrain(level.getBlockState(cursor.set(x,y,z)))) { obstacle=true;break; }
                }
            }
            if (obstacle) for (int dx=-BUFFER;dx<=BUFFER;dx++) for (int dz=-BUFFER;dz<=BUFFER;dz++)
                protectedColumns.add(key(x+dx,z+dz));
        }
        return protectedColumns;
    }

    public static boolean protectedColumn(Set<Long> columns,int x,int z) { return columns.contains(key(x,z)); }

    /** Preserve connected crowns of trees rooted outside the city, including overhanging leaves.
     * Authored wooden beams inside the city do not count as an external tree. Unknown neighbors
     * are preserved; their later load queues another foliage check. */
    public static boolean externalTree(LevelReader level, BlockPos start, BoundingBox footprint) {
        if (distance(footprint,start.getX(),start.getZ()) < -7) return false;
        record Visit(BlockPos pos,int depth) {}
        var queue = new ArrayDeque<Visit>();
        var visited = new HashSet<BlockPos>();
        queue.add(new Visit(start.immutable(),0));visited.add(start.immutable());
        boolean unknown=false;
        while (!queue.isEmpty()) {
            var visit=queue.removeFirst();
            for (var direction:Direction.values()) {
                var next=visit.pos().relative(direction);
                if (!visited.add(next)) continue;
                if (!level.hasChunk(next.getX()>>4,next.getZ()>>4)) { unknown=true;continue; }
                var state=level.getBlockState(next);
                if (state.is(BlockTags.LOGS) && distance(footprint,next.getX(),next.getZ())>0
                        && rootedOutside(level,next,footprint)) return true;
                if (visit.depth()<6 && (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)))
                    queue.addLast(new Visit(next,visit.depth()+1));
            }
        }
        return unknown;
    }

    private static boolean rootedOutside(LevelReader level,BlockPos log,BoundingBox footprint) {
        var pending=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();
        pending.add(log);seen.add(log);
        while(!pending.isEmpty()) {
            var pos=pending.removeFirst();var below=level.getBlockState(pos.below());
            if(below.is(BlockTags.DIRT) || below.is(BlockTags.SAND) || below.is(BlockTags.BASE_STONE_OVERWORLD)
                    || below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.PODZOL) || below.is(Blocks.MYCELIUM)
                    || below.is(Blocks.GRAVEL))return true;
            // An unusually large/modded tree is preserved rather than searched without a bound.
            if(seen.size()>256)return true;
            for(var direction:Direction.values()) {
                var next=pos.relative(direction);
                if(distance(footprint,next.getX(),next.getZ())<=0 || !seen.add(next))continue;
                if(!level.hasChunk(next.getX()>>4,next.getZ()>>4))return true;
                if(level.getBlockState(next).is(BlockTags.LOGS))pending.addLast(next);
            }
        }
        return false;
    }
}
