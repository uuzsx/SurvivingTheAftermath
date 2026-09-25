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

    // Shared wave sequence: melee piglins, crossbow piglins, adult hoglins, brutes.
    private static final int[][] COUNTS = {
        {2,2,0,0},
        {2,2,0,0},
        {2,2,1,0},
        {3,3,1,0},
        {3,3,2,0},
        {3,3,2,2},
        {4,3,2,3},
        {6,3,3,4},
        {6,4,4,5},
        {6,4,4,5},
        {7,5,4,6},
        {7,5,4,6},
        {8,6,5,7}
    };
    private static final int[][] MELEE_ARMOR = {
        {2,0,0,0,0},
        {2,0,0,0,0},
        {2,0,0,0,0},
        {1,2,0,0,0},
        {0,3,0,0,0},
        {0,1,2,0,0},
        {0,1,3,0,0},
        {0,0,4,2,0},
        {0,0,3,3,0},
        {0,0,2,4,0},
        {0,0,1,6,0},
        {0,0,0,5,2},
        {0,0,0,4,4}
    };
    private static final int[][] RANGED_ARMOR = {
        {2,0,0,0,0},
        {2,0,0,0,0},
        {2,0,0,0,0},
        {1,2,0,0,0},
        {0,3,0,0,0},
        {0,2,1,0,0},
        {0,1,2,0,0},
        {0,0,3,0,0},
        {0,0,3,1,0},
        {0,0,2,2,0},
        {0,0,2,3,0},
        {0,0,1,3,1},
        {0,0,0,4,2}
    };
    private static final int[][] BRUTE_ARMOR = {
        {0,0,0,0,0},
        {0,0,0,0,0},
        {0,0,0,0,0},
        {0,0,0,0,0},
        {0,0,0,0,0},
        {0,2,0,0,0},
        {0,1,2,0,0},
        {0,0,4,0,0},
        {0,0,4,1,0},
        {0,0,3,2,0},
        {0,0,2,4,0},
        {0,0,1,5,0},
        {0,0,1,3,3}
    };

    @Override public void addModules() {
        addModule(create(RaidDifficulty.EASY, 25, new ItemWeightedModule.Builder()
                .add(Items.GOLD_INGOT,80).add(Items.EMERALD,15).add(ModItems.NETHER_CORE.get(),5).build()));
        addModule(create(RaidDifficulty.NORMAL, 60, new ItemWeightedModule.Builder()
                .add(Items.GOLD_INGOT,60).add(Items.DIAMOND,10).add(Items.EMERALD,20)
                .add(Items.NETHERITE_SCRAP,2).add(ModItems.NETHER_CORE.get(),8).build()));
        addModule(create(RaidDifficulty.HARD, 100, new ItemWeightedModule.Builder()
                .add(Items.GOLD_INGOT,40).add(Items.DIAMOND,20).add(Items.EMERALD,15)
                .add(Items.ENCHANTED_GOLDEN_APPLE,2).add(Items.NETHERITE_SCRAP,8).add(ModItems.NETHER_CORE.get(),15).build()));
    }

    private BaseRaidModule create(RaidDifficulty difficulty, int rewardTime, ItemWeightedModule rewards) {
        var builder = new BaseRaidModule.Builder(difficulty.moduleName()).readyTime(100).rewardTime(rewardTime)
                .guaranteedCores(switch (difficulty) { case EASY -> 4; case NORMAL -> 10; case HARD -> 20; })
                .rewards(rewards).addCondition(new StructureConditionModule(ModStructures.NETHER_RAID.identifier().toString()));
        for (int index = 0; index < difficulty.waves(); index++) {
            int wave = index + 1;
            int[] counts = COUNTS[index];
            int min = wave <= 3 ? 0 : wave == 4 ? 1 : wave == 6 ? 2 : 4;
            int max = wave <= 3 ? 0 : wave == 4 ? 2 : wave == 6 ? 3 : 4;
            net.minecraft.world.item.Item sword = wave >= 12 ? Items.NETHERITE_SWORD : wave >= 10 ? Items.DIAMOND_SWORD : wave >= 7 ? Items.IRON_SWORD : Items.GOLDEN_SWORD;
            net.minecraft.world.item.Item axe = wave >= 13 ? Items.NETHERITE_AXE : wave >= 12 ? Items.DIAMOND_AXE : Items.GOLDEN_AXE;
            List<IEntityInfoModule> enemies = new ArrayList<>();
            enemies.add(group(EntityType.PIGLIN, counts[0], wave == 1, sword, MELEE_ARMOR[index], min, max));
            enemies.add(group(EntityType.PIGLIN, counts[1], wave == 1, Items.CROSSBOW, RANGED_ARMOR[index], min, max));
            if (counts[2] > 0) enemies.add(new EntityInfoModule(EntityType.HOGLIN, new IntegerAmountModule(counts[2])));
            if (counts[3] > 0) enemies.add(group(EntityType.PIGLIN_BRUTE, counts[3], false, axe, BRUTE_ARMOR[index], 4, 4));
            builder.addWave(enemies);
        }
        return builder.build();
    }

    private static EntityInfoModule group(EntityType<?> type, int count, boolean randomOpening, net.minecraft.world.item.Item weapon,
                                          int[] quotas, int minPieces, int maxPieces) {
        com.pancake.surviving_the_aftermath.api.module.IAmountModule amount = randomOpening
                ? new com.pancake.surviving_the_aftermath.common.module.amount.RandomAmountModule(1, 2) : new IntegerAmountModule(count);
        var equipment = new com.pancake.surviving_the_aftermath.common.raid.RaidEquipmentProfile(weapon,
                java.util.Arrays.stream(quotas).boxed().toList(), minPieces, maxPieces);
        return new EntityInfoModule(type, amount, java.util.Optional.of(equipment));
    }
}
