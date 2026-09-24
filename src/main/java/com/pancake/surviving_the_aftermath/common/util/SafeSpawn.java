package com.pancake.surviving_the_aftermath.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.AABB;
import java.util.*;

public final class SafeSpawn {
    public static boolean placeMob(ServerLevel level, Mob mob, Collection<BlockPos> anchors, BlockPos center, int radius) {
        List<BlockPos> positions = new ArrayList<>(anchors.isEmpty() ? List.of(center) : anchors);
        Collections.shuffle(positions, new Random(level.random.nextLong()));
        int lift = mob instanceof Ghast ? 20 : 0;
        for (int ring = 0; ring <= 8; ring++) {
            for (BlockPos anchor : positions) {
                for (int x = -ring; x <= ring; x++) {
                    for (int z = -ring; z <= ring; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                        for (int y : new int[]{0, 1, -1, 2, -2}) {
                            BlockPos pos = anchor.offset(x, lift + y, z);
                            if (pos.distSqr(center) >= (double) radius * radius) continue;
                            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                            if (isSafe(level, mob)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isSafe(ServerLevel level, Entity entity) {
        AABB box = entity.getBoundingBox();
        return box.minY >= level.getMinBuildHeight() && box.maxY < level.getMaxBuildHeight()
                && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ))
                && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ))
                && level.getWorldBorder().isWithinBounds(box) && level.noCollision(entity, box)
                && !level.containsAnyLiquid(box);
    }
}
