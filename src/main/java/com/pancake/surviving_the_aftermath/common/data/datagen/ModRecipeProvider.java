package com.pancake.surviving_the_aftermath.common.data.datagen;

import com.pancake.surviving_the_aftermath.common.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, java.util.concurrent.CompletableFuture<net.minecraft.core.HolderLookup.Provider> provider) { super(output, provider); }

    @Override
    protected void buildRecipes(RecipeOutput consumer) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, ModItems.DIAMOND_FLINT_AND_STEEL.get())
                .requires(Items.FLINT).requires(Items.DIAMOND)
                .unlockedBy("has_diamond", has(Items.DIAMOND)).save(consumer);        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, ModItems.GOLDEN_FLINT_AND_STEEL.get())
                .requires(Items.FLINT).requires(Items.GOLD_INGOT)
                .unlockedBy("has_gold_ingot", has(Items.GOLD_INGOT)).save(consumer);        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, ModItems.NETHERITE_FLINT_AND_STEEL.get())
                .requires(Items.FLINT).requires(Items.NETHERITE_INGOT)
                .unlockedBy("has_netherite_ingot", has(Items.NETHERITE_INGOT)).save(consumer);
    }
}
