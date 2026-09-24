package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.enchantment.LegacyEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import java.util.*;

/** Legacy food and relic trades, installed after vanilla's version-specific trade generation. */
public final class LegacyTrades {
    public static void addOffers(Villager villager) {
        var profession = villager.getVillagerData().profession().value();
        int level = villager.getVillagerData().level();
        if (profession == ModVillagers.RELIC_DEALER.get() && level == 1) {
            // The old pool had one listing; two draws therefore yielded one book offer.
            var names = LegacyEnchantments.NAMES;
            String name = names.get(villager.getRandom().nextInt(names.size()));
            var enchantment = villager.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, SurvivingTheAftermath.asResource(name)));
            int rank = 1 + villager.getRandom().nextInt(enchantment.value().getMaxLevel());
            int cost = Math.min(64, 2 + villager.getRandom().nextInt(5 + rank * 10) + 3 * rank);
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(enchantment, rank);
            villager.getOffers().add(new MerchantOffer(new ItemCost(ModItems.NETHER_CORE.get(), cost), Optional.of(new ItemCost(Items.BOOK)), book, 12, 30, .2F));
        } else if (level == 4 || level == 5) {
            var id = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id != null && id.equals(net.minecraft.resources.Identifier.withDefaultNamespace("butcher"))) {
                addFood(villager, level, ModItems.RAW_FALUKORV.get());
            } else if (id != null && id.equals(net.minecraft.resources.Identifier.withDefaultNamespace("farmer"))) {
                Item[] foods = {ModItems.RAW_FALUKORV.get(), ModItems.COOKED_FALUKORV.get(), ModItems.EGG_TART.get(),
                        ModItems.STACK_OF_EGG_TARTS.get(), ModItems.HAMBURGER.get(), ModItems.TIANJIN_PANCAKE.get()};
                // Restore a food listing without replacing or removing vanilla trades.
                addFood(villager, level, foods[villager.getRandom().nextInt(foods.length)]);
            }
        }
    }
    private static void addFood(Villager villager, int level, Item item) {
        var cost = level == 4 ? new ItemCost(Items.EMERALD, 2) : new ItemCost(Items.DIAMOND);
        villager.getOffers().add(new MerchantOffer(cost, new ItemStack(item), 12, 30, 1.0F));
    }
}
