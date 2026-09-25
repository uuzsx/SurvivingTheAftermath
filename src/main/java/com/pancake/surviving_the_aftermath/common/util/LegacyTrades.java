package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.enchantment.LegacyEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
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
        if (RelicDealerTrades.isDealer(villager)) {
            RelicDealerTrades.synchronize(villager);
        } else if (level == 4 || level == 5) {
            var id = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id != null && id.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("butcher"))) {
                addFood(villager, level, ModItems.RAW_FALUKORV.get());
            } else if (id != null && id.equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("farmer"))) {
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
