package com.pancake.surviving_the_aftermath.common.data.datagen;
import com.pancake.surviving_the_aftermath.common.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import java.util.concurrent.CompletableFuture;
public class ModRecipeProvider extends RecipeProvider.Runner {
 public ModRecipeProvider(PackOutput out, CompletableFuture<HolderLookup.Provider> lookup) { super(out, lookup); }
 protected RecipeProvider createRecipeProvider(HolderLookup.Provider lookup, RecipeOutput output) {
  return new RecipeProvider(lookup, output) {
   protected void buildRecipes() {
    shapeless(RecipeCategory.TOOLS, ModItems.DIAMOND_FLINT_AND_STEEL.get()).requires(Items.FLINT).requires(Items.DIAMOND)
      .unlockedBy("has_diamond", has(Items.DIAMOND)).save(output);    shapeless(RecipeCategory.TOOLS, ModItems.GOLDEN_FLINT_AND_STEEL.get()).requires(Items.FLINT).requires(Items.GOLD_INGOT)
      .unlockedBy("has_gold_ingot", has(Items.GOLD_INGOT)).save(output);    shapeless(RecipeCategory.TOOLS, ModItems.NETHERITE_FLINT_AND_STEEL.get()).requires(Items.FLINT).requires(Items.NETHERITE_INGOT)
      .unlockedBy("has_netherite_ingot", has(Items.NETHERITE_INGOT)).save(output);
   }
  };
 }
 public String getName() { return "Aftermath recipes"; }
}
