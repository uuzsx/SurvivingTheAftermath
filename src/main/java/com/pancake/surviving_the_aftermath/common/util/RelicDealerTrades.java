package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.common.init.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import java.util.*;

/** Three persistent offers per tier; existing stock and purchases survive migration and upgrades. */
public final class RelicDealerTrades {
    private RelicDealerTrades() {}

    public static boolean isDealer(Villager villager) {
        return villager.getVillagerData().getProfession() == ModVillagers.RELIC_DEALER.get();
    }

    public static void synchronize(Villager villager) {
        if (!isDealer(villager)) return;
        int level = Math.max(1, Math.min(5, villager.getVillagerData().getLevel()));
        var offers = villager.getOffers();
        var books = ModEnchantedBooks.all(villager.level().registryAccess());
        Item[] foods = {ModItems.RAW_FALUKORV.get(), ModItems.COOKED_FALUKORV.get(), ModItems.EGG_TART.get(),
                ModItems.STACK_OF_EGG_TARTS.get(), ModItems.HAMBURGER.get(), ModItems.TIANJIN_PANCAKE.get()};
        int[] foodPrice = {4, 6, 6, 10, 8, 10};
        int[] foodCount = {4, 2, 2, 1, 1, 1};
        var usedBooks = new HashSet<String>();
        var usedFoods = new HashSet<Item>();
        int existing = 0;
        // Preserve existing choices (including legacy high-rank books), uses and foreign trades.
        for (var offer : new ArrayList<>(offers)) {
            if (!offer.getBaseCostA().is(ModItems.NETHER_CORE.get())) continue;
            var book = books.stream().filter(b -> ItemStack.isSameItemSameTags(b.stack(), offer.getResult())).findFirst();
            if (book.isPresent() && offer.getCostB().is(Items.BOOK) && offer.getCostB().getCount() == 1) {
                existing++;
                usedBooks.add(book.get().name());
                merge(offers, offer.getResult(), true, 8 + 6 * book.get().rank(), level, 1);
            } else if (offer.getCostB().isEmpty()) {
                for (int i = 0; i < foods.length; i++) {
                    if (offer.getResult().is(foods[i])) {
                        existing++;
                        usedFoods.add(foods[i]);
                        merge(offers, offer.getResult(), false, foodPrice[i], level, 1);
                        break;
                    }
                }
            }
        }
        // Exactly three slots per career tier: two distinct book types and one food.
        // UUID + slot gives stable choices even if an unopened merchant is saved/reloaded.
        for (int slot = existing; slot < level * 3; slot++) {
            var random = new Random(villager.getUUID().getMostSignificantBits()
                    ^ villager.getUUID().getLeastSignificantBits() ^ (0x9E3779B97F4A7C15L * (slot + 1)));
            if (slot % 3 == 2) {
                var available = new ArrayList<Integer>();
                for (int i = 0; i < foods.length; i++) if (!usedFoods.contains(foods[i])) available.add(i);
                if (available.isEmpty()) break;
                int i = available.get(random.nextInt(available.size()));
                merge(offers, new ItemStack(foods[i], foodCount[i]), false, foodPrice[i], level, 1);
                usedFoods.add(foods[i]);
            } else {
                var names = books.stream().map(ModEnchantedBooks.Book::name).distinct().filter(n -> !usedBooks.contains(n)).toList();
                if (names.isEmpty()) break;
                String name = names.get(random.nextInt(names.size()));
                int tier = slot / 3 + 1;
                var ranks = books.stream().filter(b -> b.name().equals(name) && b.rank() <= tier).toList();
                var book = ranks.get(random.nextInt(ranks.size()));
                merge(offers, book.stack(), true, 8 + 6 * book.rank(), level, 1);
                usedBooks.add(name);
            }
        }
    }

    private static void merge(MerchantOffers offers, ItemStack result, boolean book, int basePrice, int level, int unlock) {
        int index = -1;
        for (int i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            if (offer.getBaseCostA().is(ModItems.NETHER_CORE.get())
                    && (book ? offer.getCostB().is(Items.BOOK) && offer.getCostB().getCount() == 1 : offer.getCostB().isEmpty())
                    && offer.getResult().getCount() == result.getCount()
                    && ItemStack.isSameItemSameTags(offer.getResult(), result)) {
                index = i;
                break;
            }
        }
        if (index < 0 && level < unlock) return;
        int price = Math.max(1, basePrice * (100 - 5 * (level - 1)) / 100);
        int maxUses = book ? 12 : 16;
        int xp = book ? 10 : 5;
        var old = index < 0 ? null : offers.get(index);
        if (old != null && old.getBaseCostA().getCount() == price && old.getMaxUses() == maxUses
                && old.getXp() == xp && old.getPriceMultiplier() == 0F) return;
        // Upgrading/reopening must not refill stock. Vanilla restocking alone resets uses.
        int uses = old == null ? 0 : old.getUses();
        int demand = old == null ? 0 : old.getDemand();
        var updated = new MerchantOffer(new ItemStack(ModItems.NETHER_CORE.get(), price), book ? new ItemStack(Items.BOOK) : ItemStack.EMPTY, result.copy(), uses, maxUses, xp, 0F, demand);
        if (old != null) {
            updated.setSpecialPriceDiff(old.getSpecialPriceDiff());
            offers.set(index, updated);
        } else offers.add(updated);
    }
}
