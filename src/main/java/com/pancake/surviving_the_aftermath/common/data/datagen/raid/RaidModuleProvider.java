package com.pancake.surviving_the_aftermath.common.data.datagen.raid;

import com.pancake.surviving_the_aftermath.api.module.IEntityInfoModule;
import com.pancake.surviving_the_aftermath.common.data.datagen.AftermathModuleProviders;
import com.pancake.surviving_the_aftermath.common.init.ModItems;
import com.pancake.surviving_the_aftermath.common.init.ModStructures;
import com.pancake.surviving_the_aftermath.common.module.amount.IntegerAmountModule;
import com.pancake.surviving_the_aftermath.common.module.condition.StructureConditionModule;
import com.pancake.surviving_the_aftermath.common.module.entity_info.EntityInfoModule;
import com.pancake.surviving_the_aftermath.common.module.weighted.ItemWeightedModule;
import com.pancake.surviving_the_aftermath.common.raid.RaidDifficulty;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import net.minecraft.data.PackOutput;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

public class RaidModuleProvider extends AftermathModuleProviders<BaseRaidModule> {
    public RaidModuleProvider(PackOutput output) { super(output, "Raid"); }

    // Columns: piglins, brutes, hoglins, magma cubes, blazes, ghasts. Counts rise every wave.
    private static final int[][] EASY = {
        {3,0,0,0,0,0}, {4,0,1,0,0,0}, {4,0,1,1,0,0}, {5,0,1,1,1,0}, {5,1,2,1,1,0}
    };
    private static final int[][] NORMAL = {
        {4,0,1,0,0,0}, {5,0,1,1,0,0}, {5,1,2,1,0,0}, {6,1,2,1,1,0},
        {6,2,2,2,1,0}, {7,2,3,2,1,1}, {7,3,3,2,2,1}, {8,3,3,3,2,1}, {8,4,4,3,3,1}
    };
    private static final int[][] HARD = {
        {5,0,1,0,0,0}, {6,1,1,1,0,0}, {6,2,2,1,1,0}, {7,2,2,2,1,0},
        {7,3,3,2,2,0}, {8,3,3,2,2,1}, {8,4,3,3,2,1}, {9,4,4,3,3,1},
        {9,5,4,3,3,1}, {10,5,4,3,3,1}, {10,6,4,3,3,2}, {11,6,4,4,3,2}, {11,7,5,4,4,2}
    };

    @Override public void addModules() {
        addModule(create(RaidDifficulty.EASY, EASY, 25, new ItemWeightedModule.Builder()
                .add(Items.GOLD_INGOT,80).add(Items.EMERALD,15).add(ModItems.NETHER_CORE.get(),5).build()));
        addModule(create(RaidDifficulty.NORMAL, NORMAL, 60, new ItemWeightedModule.Builder()
                .add(Items.GOLD_INGOT,60).add(Items.DIAMOND,10).add(Items.EMERALD,20)
                .add(Items.NETHERITE_SCRAP,2).add(ModItems.NETHER_CORE.get(),8).build()));
        addModule(create(RaidDifficulty.HARD, HARD, 100, new ItemWeightedModule.Builder()
                .add(Items.GOLD_INGOT,40).add(Items.DIAMOND,20).add(Items.EMERALD,15)
                .add(Items.ENCHANTED_GOLDEN_APPLE,2).add(Items.NETHERITE_SCRAP,8).add(ModItems.NETHER_CORE.get(),15).build()));
    }

    private BaseRaidModule create(RaidDifficulty difficulty, int[][] counts, int rewardTime, ItemWeightedModule rewards) {
        List<EntityType<?>> types = List.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN,
                EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.GHAST);
        var builder = new BaseRaidModule.Builder(difficulty.moduleName()).readyTime(100).rewardTime(rewardTime)
                .rewards(rewards).addCondition(new StructureConditionModule(ModStructures.NETHER_RAID.identifier().toString()));
        for (int[] wave : counts) {
            List<IEntityInfoModule> enemies = new ArrayList<>();
            for (int i = 0; i < types.size(); i++) if (wave[i] > 0)
                enemies.add(new EntityInfoModule(types.get(i), new IntegerAmountModule(wave[i])));
            builder.addWave(enemies);
        }
        return builder.build();
    }
}
