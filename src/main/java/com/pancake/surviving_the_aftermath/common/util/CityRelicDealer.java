package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.common.init.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** One designated merchant in the city's center chunk, independently of random residents. */
public final class CityRelicDealer {
    private CityRelicDealer() {}
    public static void place(WorldGenLevel level, BoundingBox city, BoundingBox writable, int floor) {
        int centerX = city.getCenter().getX(), centerZ = city.getCenter().getZ();
        if (!writable.isInside(new BlockPos(centerX, floor, centerZ))) return;
        for (int radius = 0; radius < 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = centerX + dx, z = centerZ + dz;
                if ((x >> 4) != (centerX >> 4) || (z >> 4) != (centerZ >> 4)) continue;
                for (int y = floor + 1; y < city.maxY(); y++) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                            || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) continue;
                    Villager dealer = EntityType.VILLAGER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                    if (dealer == null) return;
                    dealer.moveTo(x + 0.5, y, z + 0.5);
                    if (!level.noCollision(dealer)) continue;
                    dealer.setVillagerData(dealer.getVillagerData().setProfession(ModVillagers.RELIC_DEALER.get()));
                    dealer.setVillagerXp(1);
                    dealer.setPersistenceRequired();
                    dealer.addTag("aftermath_city_dealer");
                    level.addFreshEntity(dealer);
                    return;
                }
            }
        }
    }
}
