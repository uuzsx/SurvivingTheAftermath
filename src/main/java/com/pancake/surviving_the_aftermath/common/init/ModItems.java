package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.item.DiamondFlintAndSteelItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Rarity;

import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SurvivingTheAftermath.MOD_ID);
    public static final RegistryObject<Item> DIAMOND_FLINT_AND_STEEL = ITEMS.register("diamond_flint_and_steel",
            () -> new DiamondFlintAndSteelItem(new Item.Properties().durability(64)));


    public static final RegistryObject<Item> RAW_FALUKORV = ITEMS.register("raw_falukorv", () -> new Item(properties("raw_falukorv").food(new FoodProperties.Builder().nutrition(3).saturationMod(0.3F).build())));
    public static final RegistryObject<Item> COOKED_FALUKORV = ITEMS.register("cooked_falukorv", () -> new Item(properties("cooked_falukorv").food(new FoodProperties.Builder().nutrition(10).saturationMod(14F).effect(() -> new MobEffectInstance(MobEffects.DAMAGE_BOOST, 4800, 0), 1.0F).build())));
    public static final RegistryObject<Item> EGG_TART = ITEMS.register("egg_tart", () -> new Item(properties("egg_tart").food(new FoodProperties.Builder().nutrition(3).saturationMod(2F).effect(() -> new MobEffectInstance(MobEffects.HEALTH_BOOST, 4800, 0), 1.0F).build())));
    public static final RegistryObject<Item> STACK_OF_EGG_TARTS = ITEMS.register("stack_of_egg_tarts", () -> new Item(properties("stack_of_egg_tarts").food(new FoodProperties.Builder().nutrition(9).saturationMod(6F).effect(() -> new MobEffectInstance(MobEffects.HEALTH_BOOST, 3600, 1), 1.0F).effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 60, 0), 1.0F).build())));
    public static final RegistryObject<Item> HAMBURGER = ITEMS.register("hamburger", () -> new Item(properties("hamburger").food(new FoodProperties.Builder().nutrition(7).saturationMod(4F).effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 6000, 0), 1.0F).build())));
    public static final RegistryObject<Item> TIANJIN_PANCAKE = ITEMS.register("tianjin_pancake", () -> new Item(properties("tianjin_pancake").food(new FoodProperties.Builder().nutrition(8).saturationMod(14F).effect(() -> new MobEffectInstance(MobEffects.SATURATION, 4800, 0), 1.0F).effect(() -> new MobEffectInstance(MobEffects.LUCK, 2400, 0), 1.0F).build())));
    public static final RegistryObject<Item> NETHER_CORE = ITEMS.register("nether_core", () -> new com.pancake.surviving_the_aftermath.common.item.NetherCoreItem(properties("nether_core")));
    public static final RegistryObject<Item> MUSIC_DISK_ORCHELIAS_VOX = ITEMS.register("music_disk_orchelias_vox", () -> new net.minecraft.world.item.RecordItem(15, ModSoundEvents.ORCHELIAS_VOX, properties("music_disk_orchelias_vox").stacksTo(1).rarity(Rarity.RARE), 269 * 20));
    private static Item.Properties properties(String name) { return new Item.Properties(); }

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(DIAMOND_FLINT_AND_STEEL);
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            var entries = event.getEntries().iterator();
            while (entries.hasNext()) {
                if (com.pancake.surviving_the_aftermath.common.util.ModEnchantedBooks.isModBook(entries.next().getKey())) entries.remove();
            }
        }
    }
}
