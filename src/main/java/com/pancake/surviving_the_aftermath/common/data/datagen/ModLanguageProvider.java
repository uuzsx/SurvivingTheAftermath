package com.pancake.surviving_the_aftermath.common.data.datagen;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.event.subscriber.RaidEventSubscriber;
import com.pancake.surviving_the_aftermath.common.event.tracker.RaidPlayerBattleTracker;
import com.pancake.surviving_the_aftermath.common.init.ModItems;
import com.pancake.surviving_the_aftermath.common.item.DiamondFlintAndSteelItem;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {

	public ModLanguageProvider(PackOutput output) {
		super(output, SurvivingTheAftermath.MOD_ID, "en_us");
	}

	@Override
	protected void addTranslations() {
        add(ModItems.GOLDEN_FLINT_AND_STEEL.get(), "Golden Flint and Steel");
        add(ModItems.NETHERITE_FLINT_AND_STEEL.get(), "Netherite Flint and Steel");
        add("difficulty.surviving_the_aftermath.easy", "Easy");
        add("difficulty.surviving_the_aftermath.normal", "Normal");
        add("difficulty.surviving_the_aftermath.hard", "Hard");
        add("message.surviving_the_aftermath.nether_raid.wave", "%s - Wave %s/%s");
        add("message.surviving_the_aftermath.nether_core.overworld", "Use in the Overworld to seek a relic dealer.");
        add("message.surviving_the_aftermath.nether_core.searching", "Searching for a suitable city… Keep holding the core.");
        add("message.surviving_the_aftermath.nether_core.not_found", "No city with a reachable dealer found nearby. Try from another area.");
        add("message.surviving_the_aftermath.nether_core.city", "The core points toward a city; it will seek a living relic dealer as you approach.");
        add("message.surviving_the_aftermath.nether_core.dealer", "The core points toward a nearby relic dealer.");
        add("item.surviving_the_aftermath.raw_falukorv", "Raw Falukorv");
        add("item.surviving_the_aftermath.cooked_falukorv", "Cooked Falukorv");
        add("item.surviving_the_aftermath.egg_tart", "Egg Tart");
        add("item.surviving_the_aftermath.stack_of_egg_tarts", "Stack of Egg Tarts");
        add("item.surviving_the_aftermath.hamburger", "Hamburger");
        add("item.surviving_the_aftermath.tianjin_pancake", "Tianjin Pancake");
        add("item.surviving_the_aftermath.nether_core", "Nether Core");
        add("item.surviving_the_aftermath.music_disk_orchelias_vox", "Music Disc");
        add("enchantment.surviving_the_aftermath.counter_attack", "Counter Attack");
        add("enchantment.surviving_the_aftermath.bloodthirsty", "Bloodthirsty");
        add("enchantment.surviving_the_aftermath.clean_water", "Clean Water");
        add("enchantment.surviving_the_aftermath.life_tree", "Life Tree");
        add("enchantment.surviving_the_aftermath.devoured", "Devoured");
        add("enchantment.surviving_the_aftermath.frantic", "Frantic");
        add("enchantment.surviving_the_aftermath.execute", "Execute");
        add("enchantment.surviving_the_aftermath.ranger", "Ranger");
        add("enchantment.surviving_the_aftermath.moon", "Moon");
        add("enchantment.surviving_the_aftermath.sun", "Sun");
        add("effect.surviving_the_aftermath.cowardice", "Cowardice");
        add("entity.minecraft.villager.surviving_the_aftermath.relic_dealer", "Relic Dealer");
        add("entity.minecraft.villager.relic_dealer", "Relic Dealer");
        add("item.surviving_the_aftermath.music_disk_orchelias_vox.desc", "Hagali - Orchelia's vox (offvocal ver_)");

		add(ModItems.DIAMOND_FLINT_AND_STEEL.get(), "Diamond Flint and Steel");
		add(DiamondFlintAndSteelItem.REQUIRED, "Use Golden, Diamond, or Netherite Flint and Steel for Easy, Normal, or Hard challenges.");
		add(DiamondFlintAndSteelItem.UNAVAILABLE, "Cannot activate: check the portal frame, challenge conditions, or an unfinished challenge.");
		add("itemGroup." + SurvivingTheAftermath.MOD_ID, "Surviving the Aftermath");

		add(RaidEventSubscriber.NETHER_RAID_START, "You feel the air getting hotter .....");
		add(RaidEventSubscriber.NETHER_RAID_VICTORY, "Looking at the last Mars extinguished, you feel they will not come back, temporarily......");
		add(RaidPlayerBattleTracker.PLAYER_BATTLE_ESCAPE, "Do not fight process escape, or you will pay the price, the countdown begins......%s");
		add(RaidPlayerBattleTracker.PLAYER_BATTLE_PERSONAL_FAIL, "Although you failed, you can continue to trust your teammates.");
	}

}
