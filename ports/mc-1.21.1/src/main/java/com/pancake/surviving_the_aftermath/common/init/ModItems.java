package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.item.DiamondFlintAndSteelItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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

    @SubscribeEvent
    public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(DIAMOND_FLINT_AND_STEEL.get());
    }
}
