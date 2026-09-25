package com.pancake.surviving_the_aftermath.common.raid;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import java.util.ArrayList;
import java.util.List;

/** Explicit combat-only lists; random enchanting can roll useless underwater/tool enchantments. */
public final class RaidCombat {
    private RaidCombat() {}
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Item[][] SETS = {
        {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
        {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
        {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
        {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}
    };
    private static final Item[] SWORDS = {Items.GOLDEN_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private static final Item[] AXES = {Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};

    // Per-mob kit weights (%): gold, iron, diamond, netherite. Rows are one-based waves.
    private static final int[][] EASY_WEIGHTS = {
        {95, 5, 0, 0},
        {90, 10, 0, 0},
        {85, 15, 0, 0},
        {80, 20, 0, 0},
        {70, 30, 0, 0}
    };
    private static final int[][] NORMAL_WEIGHTS = {
        {85, 15, 0, 0},
        {80, 20, 0, 0},
        {70, 30, 0, 0},
        {60, 40, 0, 0},
        {50, 50, 0, 0},
        {40, 60, 0, 0},
        {30, 70, 0, 0},
        {25, 60, 15, 0},
        {15, 55, 30, 0}
    };
    private static final int[][] HARD_WEIGHTS = {
        {80, 20, 0, 0},
        {75, 25, 0, 0},
        {65, 35, 0, 0},
        {60, 40, 0, 0},
        {50, 50, 0, 0},
        {40, 60, 0, 0},
        {35, 65, 0, 0},
        {25, 75, 0, 0},
        {20, 65, 15, 0},
        {15, 60, 25, 0},
        {10, 50, 40, 0},
        {5, 40, 45, 10},
        {5, 25, 45, 25}
    };

    private static int selectMaterial(net.minecraft.util.RandomSource random, RaidDifficulty difficulty, int wave) {
        int[][] table = switch (difficulty) {
            case EASY -> EASY_WEIGHTS;
            case NORMAL -> NORMAL_WEIGHTS;
            case HARD -> HARD_WEIGHTS;
        };
        // Old saves/data packs can have extra waves: retain the final mix instead of indexing past it.
        int[] weights = table[Math.max(0, Math.min(wave - 1, table.length - 1))];
        int roll = random.nextInt(100);
        for (int material = 0; material < weights.length; material++) {
            if (roll < weights[material]) return material;
            roll -= weights[material];
        }
        throw new IllegalStateException("Raid equipment weights must sum to 100");
    }

    /** Runs once after vanilla spawn initialization, before adding the mob to the world. Wave is one-based. */
    public static void prepare(ServerLevel level, Mob mob, RaidDifficulty difficulty, int wave) {
        if (mob instanceof Piglin piglin) piglin.setBaby(false);
        if (mob instanceof Hoglin hoglin) hoglin.setBaby(false);
        // Bound cube sizes: four giant cubes and their descendants are already substantial pressure.
        if (mob instanceof MagmaCube cube) cube.setSize(difficulty == RaidDifficulty.HARD && wave >= 7 ? 4 : 2, true);
        if (mob instanceof AbstractPiglin) equip(level, mob, difficulty, wave);
        if (difficulty == RaidDifficulty.HARD && wave >= 10) addEffects(mob, wave);
    }

    private static void equip(ServerLevel level, Mob mob, RaidDifficulty difficulty, int wave) {
        // One draw per enemy keeps coherent individual kits and mixes materials within a wave.
        int material = selectMaterial(mob.getRandom(), difficulty, wave);
        int pieces = difficulty == RaidDifficulty.EASY ? (wave < 3 ? 1 : wave < 5 ? 2 : 4)
                : difficulty == RaidDifficulty.NORMAL ? (wave < 4 ? 2 : 4) : (wave < 3 ? 2 : 4);
        boolean brute = mob instanceof PiglinBrute;
        ItemStack weapon = new ItemStack(brute ? AXES[material] : SWORDS[material]);
        mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        for (int i = 0; i < ARMOR.length; i++) {
            mob.setItemSlot(ARMOR[i], i < pieces ? new ItemStack(SETS[material][i]) : ItemStack.EMPTY);
        }
        if (wave < 7 || difficulty == RaidDifficulty.EASY) return;
        var random = mob.getRandom();
        int min = difficulty == RaidDifficulty.NORMAL ? 1 : wave >= 10 ? 3 : 1;
        int max = difficulty == RaidDifficulty.NORMAL ? 2 : wave >= 12 ? 5 : wave >= 10 ? 4 : 3;
        enchant(level, weapon, Enchantments.SHARPNESS, min + random.nextInt(max - min + 1));
        if (!brute && difficulty == RaidDifficulty.HARD) {
            if (random.nextBoolean()) enchant(level, weapon, Enchantments.KNOCKBACK, wave >= 10 ? 2 : 1);
            if (random.nextInt(3) == 0) enchant(level, weapon, Enchantments.FIRE_ASPECT, wave >= 12 ? 2 : 1);
        }
        for (EquipmentSlot slot : ARMOR) {
            ItemStack armor = mob.getItemBySlot(slot);
            if (armor.isEmpty()) continue;
            int protection = difficulty == RaidDifficulty.NORMAL ? 1 : wave >= 10 ? 3 + random.nextInt(2) : 1 + random.nextInt(2);
            enchant(level, armor, Enchantments.PROTECTION, protection);
            if (slot == EquipmentSlot.CHEST && difficulty == RaidDifficulty.HARD && random.nextBoolean())
                enchant(level, armor, Enchantments.THORNS, wave >= 10 ? 2 + random.nextInt(2) : 1);
        }
    }

    private static void enchant(ServerLevel level, ItemStack stack,
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key, int rank) {
        stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key), rank);
    }

    private static void addEffects(Mob mob, int wave) {
        // Strength is ineffective for fireballs and cube collision damage; speed is only chosen for ground mobs.
        var pool = new ArrayList<>(List.of(MobEffects.ABSORPTION, MobEffects.REGENERATION, MobEffects.RESISTANCE));
        if (mob instanceof AbstractPiglin || mob instanceof Hoglin) {
            pool.add(MobEffects.STRENGTH);
            pool.add(MobEffects.SPEED);
        }
        int count = Math.min(3, wave - 9);
        for (int i = 0; i < count; i++) {
            var effect = pool.remove(mob.getRandom().nextInt(pool.size()));
            int amplifier = wave >= 12 && mob.getRandom().nextBoolean() ? 1 : 0;
            // Infinite, so saving/reloading or a long fight does not silently remove the challenge.
            mob.addEffect(new MobEffectInstance(effect, -1, amplifier));
        }
    }

    public static void inheritEffects(Mob parent, Mob child) {
        parent.getActiveEffects().forEach(effect -> child.addEffect(new MobEffectInstance(effect)));
    }
}
