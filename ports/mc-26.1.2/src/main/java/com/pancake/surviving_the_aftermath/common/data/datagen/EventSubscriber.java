package com.pancake.surviving_the_aftermath.common.data.datagen;

import com.pancake.surviving_the_aftermath.common.data.datagen.raid.RaidModuleProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;

import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.concurrent.CompletableFuture;

public class EventSubscriber {

	public static void onGatherData(GatherDataEvent.Client event) {
		DataGenerator generator = event.getGenerator();
		PackOutput output = generator.getPackOutput();

		CompletableFuture<HolderLookup.Provider> provider = event.getLookupProvider();
		generator.addProvider(true, new ModTagProviders.ModBiomeTagsProvider(output, provider));
		generator.addProvider(true, new ModTagProviders.ModStructureTagsProvider(output, provider));
		generator.addProvider(true, new RaidModuleProvider(output));
		generator.addProvider(true, new RegistryDataGenerator(output, provider));
		generator.addProvider(true, new ModRecipeProvider(output, provider));
		generator.addProvider(true, new ModItemModelProvider(output));

		generator.addProvider(true, new ModLanguageCNProvider(output));
		generator.addProvider(true, new ModLanguageProvider(output));

		generator.addProvider(true, new ModSoundProvider(output));
	}

}
