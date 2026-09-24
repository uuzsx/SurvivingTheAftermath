package com.pancake.surviving_the_aftermath.common.init;

import com.google.common.collect.ImmutableSet;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModVillagers {
    public static final ResourceKey<PoiType> RELIC_DEALER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            SurvivingTheAftermath.asResource("relic_dealer"));
    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, SurvivingTheAftermath.MOD_ID);
    public static final java.util.function.Supplier<VillagerProfession> RELIC_DEALER = VILLAGER_PROFESSIONS.register("relic_dealer", () -> new VillagerProfession(
            "relic_dealer",
            x -> x.is(RELIC_DEALER_POI), x -> x.is(RELIC_DEALER_POI), ImmutableSet.of(), ImmutableSet.of(),
            SoundEvents.VILLAGER_WORK_CLERIC));
}
