package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.enchantment.LegacyEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import java.util.*;

/** Legacy food and relic trades, installed after vanilla's version-specific trade generation. */
public final class LegacyTrades {
    public static void addOffers(Villager villager) {
        var profession = villager.getVillagerData().getProfession();
        int level = villager.getVillagerData().getLevel();
        if (profession == ModVillagers.RELIC_DEALER.get() && level == 1) {
            // The old pool had one listing; two draws therefore yielded one book offer.
            var names = LegacyEnchantments.NAMES;
            String name = names.get(villager.getRandom().nextInt(names.size()));
            var enchantment = ModEnchantments.get(name);
            int rank = 1 + villager.getRandom().nextInt(enchantment.getMaxLevel());
            int cost = Math.min(64, 2 + villager.getRandom().nextInt(5 + rank * 10) + 3 * rank);
            ItemStack book = net.minecraft.world.item.EnchantedBookItem.createForEnchantment(new net.minecraft.world.item.enchantment.EnchantmentInstance(enchantment, rank));
            villager.getOffers().add(new MerchantOffer(new ItemStack(ModItems.NETHER_CORE.get(), cost), new ItemStack(Items.BOOK), book, 12, 30, .2F));
        } else if (level == 4 || level == 5) {
            var id = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id != null && id.equals(new net.minecraft.resources.ResourceLocation("butcher"))) {
                addFood(villager, level, ModItems.RAW_FALUKORV.get());
            } else if (id != null && id.equals(new net.minecraft.resources.ResourceLocation("farmer"))) {
                Item[] foods = {ModItems.RAW_FALUKORV.get(), ModItems.COOKED_FALUKORV.get(), ModItems.EGG_TART.get(),
                        ModItems.STACK_OF_EGG_TARTS.get(), ModItems.HAMBURGER.get(), ModItems.TIANJIN_PANCAKE.get()};
                // Restore a food listing without replacing or removing vanilla trades.
                addFood(villager, level, foods[villager.getRandom().nextInt(foods.length)]);
            }
        }
    }
    private static void addFood(Villager villager, int level, Item item) {
        var cost = level == 4 ? new ItemStack(Items.EMERALD, 2) : new ItemStack(Items.DIAMOND);
        villager.getOffers().add(new MerchantOffer(cost, new ItemStack(item), 12, 30, 1.0F));
    }
}
