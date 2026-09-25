package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.enchantment.LegacyEnchantments;
import com.pancake.surviving_the_aftermath.common.init.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import java.util.*;

/** Fresh stacks from the current registry: never cache holders across world or data-pack reloads. */
public final class ModEnchantedBooks {
    private ModEnchantedBooks() {}
    public record Book(String name, int rank, ItemStack stack) {}

    public static List<Book> all(HolderLookup.Provider lookup) {
        List<Book> result = new ArrayList<>();
        for (String name : LegacyEnchantments.NAMES) {
            var enchantment = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(
                    net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, SurvivingTheAftermath.asResource(name)));
            for (int rank = 1; rank <= enchantment.value().getMaxLevel(); rank++) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                book.enchant(enchantment, rank);
                result.add(new Book(name, rank, book));
            }
        }
        return result;
    }

    public static boolean isModBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) && stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).entrySet().stream().anyMatch(e -> e.getKey().unwrapKey().map(k -> k.identifier().getNamespace().equals(SurvivingTheAftermath.MOD_ID)).orElse(false));
    }
}
