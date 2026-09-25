package com.pancake.surviving_the_aftermath.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Grounded doorway placement and a short, interruptible outward step, never a launch. */
public final class PortalEntrance {
    private static final String TICKS = "aftermath_entrance_ticks";
    private static final String DIRECTION = "aftermath_entrance_direction";
    private static final String PREVIOUS_AI = "aftermath_entrance_no_ai";
    public static final int WALK_TICKS = 20;
    private static final double STEP = 0.12;
    private PortalEntrance() {}

    public static boolean usesEntrance(Mob mob) { return mob instanceof AbstractPiglin || mob instanceof Hoglin; }

    public static Optional<Direction> place(ServerLevel level, Mob mob, Collection<BlockPos> portal, Vec3 target) {
        if (portal.isEmpty()) return Optional.empty();
        int floor = portal.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        var bottom = portal.stream().filter(p -> p.getY() == floor).toList();
        int minX = bottom.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = bottom.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = bottom.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = bottom.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        boolean widthX = maxX - minX >= maxZ - minZ;
        double cx = (minX + maxX + 1) / 2.0, cz = (minZ + maxZ + 1) / 2.0;
        Direction preferred = widthX ? (target.z >= cz ? Direction.SOUTH : Direction.NORTH)
                : (target.x >= cx ? Direction.EAST : Direction.WEST);
        var lanes = new ArrayList<>(bottom);
        // Pick among real doorway lanes, never scatter onto roofs or distant rings.
        Collections.shuffle(lanes, new Random(level.getRandom().nextLong()));
        double offset = Math.max(0, mob.getBbWidth() / 2.0 - 0.45);
        for (Direction direction : List.of(preferred, preferred.getOpposite())) {
            for (BlockPos lane : lanes) {
                if (!level.getBlockState(lane).is(Blocks.NETHER_PORTAL)) continue;
                double x = lane.getX() + .5 + direction.getStepX() * offset;
                double z = lane.getZ() + .5 + direction.getStepZ() * offset;
                // A two-wide doorway needs its exact middle for a full-grown hoglin.
                if (mob.getBbWidth() > 1 && (widthX ? maxX - minX : maxZ - minZ) == 1) {
                    if (widthX) x = cx; else z = cz;
                }
                double y = surface(level, mob, x, z, floor + .01);
                if (!Double.isFinite(y) || Math.abs(y - floor) > .01) continue;
                Vec3 origin = new Vec3(x, y, z);
                boolean clear = true;
                for (double distance = 0; distance <= 2.41; distance += .4) {
                    double px = x + direction.getStepX() * distance, pz = z + direction.getStepZ() * distance;
                    double nextY = surface(level, mob, px, pz, y + .6);
                    if (!Double.isFinite(nextY) || y - nextY > 1.01) { clear = false; break; }
                    mob.moveTo(px, nextY, pz);
                    if (!SafeSpawn.isSafe(level, mob)) { clear = false; break; }
                    y = nextY;
                }
                mob.moveTo(origin);
                if (!clear || !level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(.08),
                        e -> e != mob && e.isAlive() && !e.isSpectator()).isEmpty()) continue;
                mob.setDeltaMovement(Vec3.ZERO);
                mob.fallDistance = 0;
                mob.setOnGround(true);
                return Optional.of(direction);
            }
        }
        return Optional.empty();
    }

    private static double surface(ServerLevel level, Mob mob, double x, double z, double ceiling) {
        double half = mob.getBbWidth() / 2.0;
        AABB column = new AABB(x - half, ceiling - 3, z - half, x + half, ceiling + .001, z + half);
        double top = Double.NEGATIVE_INFINITY;
        for (var shape : level.getBlockCollisions(mob, column)) {
            for (AABB box : shape.toAabbs()) {
                if (box.maxY <= ceiling + .001 && box.maxY > top && box.maxX > column.minX
                        && box.minX < column.maxX && box.maxZ > column.minZ && box.minZ < column.maxZ) top = box.maxY;
            }
        }
        return top;
    }

    public static void begin(Mob mob, Direction direction) {
        CompoundTag tag = mob.getPersistentData();
        tag.putBoolean(PREVIOUS_AI, mob.isNoAi());
        tag.putInt(TICKS, WALK_TICKS);
        tag.putInt(DIRECTION, direction.get2DDataValue());
        mob.setNoAi(true);
        mob.getNavigation().stop();
        mob.setPortalCooldown();
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0;
        face(mob, direction);
    }

    public static boolean isWalking(Mob mob) { return mob.getPersistentData().contains(TICKS); }

    public static void tick(ServerLevel level, Mob mob) {
        if (!isWalking(mob)) return;
        CompoundTag tag = mob.getPersistentData();
        int remaining = tag.getInt(TICKS);
        if (remaining <= 0 || !mob.isAlive() || mob.hurtTime > 0) { finish(mob); return; }
        Direction direction = Direction.from2DDataValue(tag.getInt(DIRECTION));
        double dx = direction.getStepX() * STEP, dz = direction.getStepZ() * STEP;
        double floor = surface(level, mob, mob.getX() + dx, mob.getZ() + dz, mob.getY() + .6);
        if (!Double.isFinite(floor) || mob.getY() - floor > 1.01) { finish(mob); return; }
        var next = mob.getBoundingBox().move(dx, floor - mob.getY(), dz);
        // Use ordinary collision resolution, following the same slab/stair surface as placement.
        if (level.containsAnyLiquid(next) || level.getBlockCollisions(mob, next.deflate(.001)).iterator().hasNext()) { finish(mob); return; }
        face(mob, direction);
        mob.move(MoverType.SELF, new Vec3(dx, floor - mob.getY(), dz));
        mob.setDeltaMovement(Vec3.ZERO);
        mob.fallDistance = 0;
        mob.setOnGround(level.getBlockCollisions(mob, mob.getBoundingBox().move(0, -.05, 0)).iterator().hasNext());
        tag.putInt(TICKS, remaining - 1);
    }

    private static void face(Mob mob, Direction direction) {
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getStepX(), direction.getStepZ()));
        mob.setYRot(yaw);mob.setYBodyRot(yaw);mob.setYHeadRot(yaw);
    }

    public static void finish(Mob mob) {
        if (!isWalking(mob)) return;
        var tag = mob.getPersistentData();
        mob.setNoAi(tag.getBoolean(PREVIOUS_AI));
        tag.remove(TICKS);tag.remove(DIRECTION);tag.remove(PREVIOUS_AI);
    }
}
