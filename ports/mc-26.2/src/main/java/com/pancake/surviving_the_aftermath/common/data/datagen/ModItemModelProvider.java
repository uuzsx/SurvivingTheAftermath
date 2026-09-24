package com.pancake.surviving_the_aftermath.common.data.datagen;
import com.google.gson.JsonParser;
import net.minecraft.data.*;
import java.util.concurrent.CompletableFuture;
public class ModItemModelProvider implements DataProvider {
 private final PackOutput output;
 public ModItemModelProvider(PackOutput output) { this.output=output; }
 public String getName() { return "Aftermath item models"; }
 public CompletableFuture<?> run(CachedOutput cache) {
  var root=output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve("surviving_the_aftermath");
  return CompletableFuture.allOf(
   DataProvider.saveStable(cache, JsonParser.parseString("{\"parent\":\"minecraft:item/handheld\",\"textures\":{\"layer0\":\"surviving_the_aftermath:item/diamond_flint_and_steel\"}}"),root.resolve("models/item/diamond_flint_and_steel.json")),
   DataProvider.saveStable(cache, JsonParser.parseString("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"surviving_the_aftermath:item/diamond_flint_and_steel\"}}"),root.resolve("items/diamond_flint_and_steel.json")));
 }
}
