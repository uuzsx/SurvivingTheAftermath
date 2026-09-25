package com.pancake.surviving_the_aftermath.common.data.datagen;
import com.pancake.surviving_the_aftermath.common.init.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.Items;
public class ModRecipeProvider extends RecipeProvider {
 public ModRecipeProvider(BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) { super(recipes, advancements); }
 protected void buildRecipes() {
  shapeless(RecipeCategory.TOOLS, ModItems.DIAMOND_FLINT_AND_STEEL.get()).requires(Items.FLINT).requires(Items.DIAMOND)
   .unlockedBy("has_diamond", has(Items.DIAMOND)).save(output);  shapeless(RecipeCategory.TOOLS, ModItems.GOLDEN_FLINT_AND_STEEL.get()).requires(Items.FLINT).requires(Items.GOLD_INGOT)
   .unlockedBy("has_gold_ingot", has(Items.GOLD_INGOT)).save(output);  shapeless(RecipeCategory.TOOLS, ModItems.NETHERITE_FLINT_AND_STEEL.get()).requires(Items.FLINT).requires(Items.NETHERITE_INGOT)
   .unlockedBy("has_netherite_ingot", has(Items.NETHERITE_INGOT)).save(output);
 }
}
