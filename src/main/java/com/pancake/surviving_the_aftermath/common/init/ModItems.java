package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.item.DiamondFlintAndSteelItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Rarity;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
import java.util.function.Supplier;

@net.neoforged.fml.common.EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ITEM, SurvivingTheAftermath.MOD_ID);
    public static final Supplier<Item> DIAMOND_FLINT_AND_STEEL = ITEMS.register("diamond_flint_and_steel",
            () -> new DiamondFlintAndSteelItem(new Item.Properties().durability(64)));


    public static final java.util.function.Supplier<Item> RAW_FALUKORV = ITEMS.register("raw_falukorv", () -> new Item(properties("raw_falukorv").food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build())));
    public static final java.util.function.Supplier<Item> COOKED_FALUKORV = ITEMS.register("cooked_falukorv", () -> new Item(properties("cooked_falukorv").food(new FoodProperties.Builder().nutrition(10).saturationModifier(14F).effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 4800, 0), 1.0F).build())));
    public static final java.util.function.Supplier<Item> EGG_TART = ITEMS.register("egg_tart", () -> new Item(properties("egg_tart").food(new FoodProperties.Builder().nutrition(3).saturationModifier(2F).effect(() -> new MobEffectInstance(MobEffects.HEALTH_BOOST, 4800, 0), 1.0F).build())));
    public static final java.util.function.Supplier<Item> STACK_OF_EGG_TARTS = ITEMS.register("stack_of_egg_tarts", () -> new Item(properties("stack_of_egg_tarts").food(new FoodProperties.Builder().nutrition(9).saturationModifier(6F).effect(() -> new MobEffectInstance(MobEffects.HEALTH_BOOST, 3600, 1), 1.0F).effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 60, 0), 1.0F).build())));
    public static final java.util.function.Supplier<Item> HAMBURGER = ITEMS.register("hamburger", () -> new Item(properties("hamburger").food(new FoodProperties.Builder().nutrition(7).saturationModifier(4F).effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 6000, 0), 1.0F).build())));
    public static final java.util.function.Supplier<Item> TIANJIN_PANCAKE = ITEMS.register("tianjin_pancake", () -> new Item(properties("tianjin_pancake").food(new FoodProperties.Builder().nutrition(8).saturationModifier(14F).effect(() -> new MobEffectInstance(MobEffects.SATURATION, 4800, 0), 1.0F).effect(() -> new MobEffectInstance(MobEffects.LUCK, 2400, 0), 1.0F).build())));
    public static final java.util.function.Supplier<Item> NETHER_CORE = ITEMS.register("nether_core", () -> new com.pancake.surviving_the_aftermath.common.item.NetherCoreItem(properties("nether_core")));
    public static final java.util.function.Supplier<Item> MUSIC_DISK_ORCHELIAS_VOX = ITEMS.register("music_disk_orchelias_vox", () -> new Item(properties("music_disk_orchelias_vox").stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.JUKEBOX_SONG, SurvivingTheAftermath.asResource("orchelias_vox")))));
    private static Item.Properties properties(String name) { return new Item.Properties(); }

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(DIAMOND_FLINT_AND_STEEL.get());
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            var books = new java.util.ArrayList<net.minecraft.world.item.ItemStack>(event.getParentEntries());
            books.addAll(event.getSearchEntries());
            books.stream().filter(com.pancake.surviving_the_aftermath.common.util.ModEnchantedBooks::isModBook)
                    .forEach(book -> event.remove(book, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
        }
    }
}
