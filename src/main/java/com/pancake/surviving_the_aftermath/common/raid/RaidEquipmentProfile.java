package com.pancake.surviving_the_aftermath.common.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A saved data-pack roster: exact material quotas, randomly assigned to members of one role. */
public record RaidEquipmentProfile(Item weapon, List<Integer> armorQuotas, int minPieces, int maxPieces) {
    public static final String PRESET_TAG = "surviving_the_aftermath.raid_equipment";
    public static final Codec<RaidEquipmentProfile> CODEC = RecordCodecBuilder.<RaidEquipmentProfile>create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("weapon").forGetter(RaidEquipmentProfile::weapon),
            Codec.intRange(0, 128).listOf().fieldOf("armor_quotas").forGetter(RaidEquipmentProfile::armorQuotas),
            Codec.intRange(0, 4).fieldOf("min_pieces").forGetter(RaidEquipmentProfile::minPieces),
            Codec.intRange(0, 4).fieldOf("max_pieces").forGetter(RaidEquipmentProfile::maxPieces)
    ).apply(instance, RaidEquipmentProfile::new)).flatXmap(RaidEquipmentProfile::validate, RaidEquipmentProfile::validate);

    public RaidEquipmentProfile { armorQuotas = List.copyOf(armorQuotas); }

    private static DataResult<RaidEquipmentProfile> validate(RaidEquipmentProfile profile) {
        if (profile.armorQuotas.size() != 5 || profile.armorQuotas.stream().mapToInt(Integer::intValue).sum() == 0
                || profile.minPieces > profile.maxPieces)
            return DataResult.error(() -> "Raid armor quotas need five entries (none/gold/iron/diamond/netherite), a positive total and min_pieces <= max_pieces");
        return DataResult.success(profile);
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Item[][] SETS = {
        {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
        {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
        {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
        {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}
    };

    public void apply(List<Mob> mobs, RandomSource random) {
        List<Integer> bag = new ArrayList<>();
        for (int tier = 0; tier < armorQuotas.size(); tier++)
            for (int count = 0; count < armorQuotas.get(tier); count++) bag.add(tier - 1);
        shuffle(bag, random);
        for (int member = 0; member < mobs.size(); member++) {
            // Built-in groups exactly fill the bag (wave one may use fewer unarmored slots).
            // A data pack increasing group size reuses the quota bag instead of crashing the server.
            if (member > 0 && member % bag.size() == 0) shuffle(bag, random);
            int material = bag.get(member % bag.size());
            Mob mob = mobs.get(member);
            for (EquipmentSlot slot : ARMOR) mob.setItemSlot(slot, ItemStack.EMPTY);
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
            mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (material >= 0) {
                int pieces = minPieces + random.nextInt(maxPieces - minPieces + 1);
                List<Integer> slots = new ArrayList<>(List.of(0, 1, 2, 3));
                shuffle(slots, random);
                for (int i = 0; i < pieces; i++) {
                    int slot = slots.get(i);
                    mob.setItemSlot(ARMOR[slot], new ItemStack(SETS[material][slot]));
                }
            }
            mob.getPersistentData().putBoolean(PRESET_TAG, true);
            mob.setCanPickUpLoot(false);
        }
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (int i = values.size() - 1; i > 0; i--) Collections.swap(values, i, random.nextInt(i + 1));
    }
}
