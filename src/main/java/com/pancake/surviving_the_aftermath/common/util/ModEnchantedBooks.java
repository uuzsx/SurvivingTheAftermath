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
            var enchantment = ModEnchantments.get(name);
            for (int rank = 1; rank <= enchantment.getMaxLevel(); rank++) {
                ItemStack book = EnchantedBookItem.createForEnchantment(new net.minecraft.world.item.enchantment.EnchantmentInstance(enchantment, rank));
                result.add(new Book(name, rank, book));
            }
        }
        return result;
    }

    public static boolean isModBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) && net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(stack).keySet().stream().anyMatch(e -> SurvivingTheAftermath.MOD_ID.equals(net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(e).getNamespace()));
    }
}
