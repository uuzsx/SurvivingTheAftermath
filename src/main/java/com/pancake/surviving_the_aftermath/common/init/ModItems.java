package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.item.DiamondFlintAndSteelItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SurvivingTheAftermath.MOD_ID);
    public static final RegistryObject<Item> DIAMOND_FLINT_AND_STEEL = ITEMS.register("diamond_flint_and_steel",
            () -> new DiamondFlintAndSteelItem(new Item.Properties().durability(64)));

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(DIAMOND_FLINT_AND_STEEL);
    }
}
