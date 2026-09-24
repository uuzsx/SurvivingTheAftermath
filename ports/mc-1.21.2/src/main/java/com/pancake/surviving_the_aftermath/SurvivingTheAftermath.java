package com.pancake.surviving_the_aftermath;

import com.mojang.logging.LogUtils;


import com.pancake.surviving_the_aftermath.common.config.AftermathConfig;
import com.pancake.surviving_the_aftermath.common.data.datagen.EventSubscriber;
import com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.network.AftermathNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;


@Mod(SurvivingTheAftermath.MOD_ID)
public class SurvivingTheAftermath {
    public static final String MOD_ID = "surviving_the_aftermath";
    public static final Logger LOGGER = LogUtils.getLogger();
    public SurvivingTheAftermath(IEventBus bus, ModContainer container) {
        NeoForge.EVENT_BUS.register(this);
        bus.addListener(EventSubscriber::onGatherData);
        ModuleRegistry.register(bus);
        ModMobEffects.MOB_EFFECTS.register(bus);
        ModSoundEvents.SOUND_EVENTS.register(bus);
        ModItems.ITEMS.register(bus);
        ModTabs.TABS.register(bus);
        ModVillagers.VILLAGER_PROFESSIONS.register(bus);

        ModStructurePieceTypes.STRUCTURE_PIECE_TYPES.register(bus);
        ModStructureTypes.STRUCTURE_TYPES.register(bus);
        bus.addListener(AftermathNetwork::register);
        ModCapability.ATTACHMENTS.register(bus);


        container.registerConfig(ModConfig.Type.COMMON, AftermathConfig.SPEC);
    }

    @SubscribeEvent
    public void onDataPackLoad(AddReloadListenerEvent event) {
        event.addListener(new AftermathModuleLoader());
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

}
