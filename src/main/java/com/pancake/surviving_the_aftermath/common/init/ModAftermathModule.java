package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.api.IAftermath;
import com.pancake.surviving_the_aftermath.api.ITracker;
import com.pancake.surviving_the_aftermath.api.module.*;
import com.pancake.surviving_the_aftermath.common.event.tracker.MobBattleTracker;
import com.pancake.surviving_the_aftermath.common.event.tracker.RaidMobBattleTracker;
import com.pancake.surviving_the_aftermath.common.event.tracker.RaidPlayerBattleTracker;
import com.pancake.surviving_the_aftermath.common.module.amount.IntegerAmountModule;
import com.pancake.surviving_the_aftermath.common.module.amount.RandomAmountModule;
import com.pancake.surviving_the_aftermath.common.module.condition.*;
import com.pancake.surviving_the_aftermath.common.module.entity_info.EntityInfoModule;
import com.pancake.surviving_the_aftermath.common.module.entity_info.EntityInfoWithPredicateModule;
import com.pancake.surviving_the_aftermath.common.module.predicate.AttributePredicate;
import com.pancake.surviving_the_aftermath.common.module.predicate.EffectPredicate;
import com.pancake.surviving_the_aftermath.common.module.predicate.EquipmentPredicate;
import com.pancake.surviving_the_aftermath.common.module.predicate.NBTPredicate;
import com.pancake.surviving_the_aftermath.common.module.weighted.AttributeWeightedModule;
import com.pancake.surviving_the_aftermath.common.module.weighted.EffectWeightedModule;
import com.pancake.surviving_the_aftermath.common.module.weighted.EntityTypeWeightedModule;
import com.pancake.surviving_the_aftermath.common.module.weighted.ItemWeightedModule;
import com.pancake.surviving_the_aftermath.common.raid.BaseRaid;
import com.pancake.surviving_the_aftermath.common.raid.NetherRaid;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.fml.common.Mod;
import java.util.function.Supplier;

public class ModAftermathModule {
    public static void init() {}
    public static final Supplier<IAftermathModule> BASE_RAID_MODULE = ModuleRegistry.AFTERMATH_MODULE.register(BaseRaidModule.IDENTIFIER, () -> new BaseRaidModule());
    public static final Supplier<IAftermath> BASE_RAID = ModuleRegistry.AFTERMATH.register(BaseRaid.IDENTIFIER, () -> new BaseRaid());
    public static final Supplier<IAftermath> NETHER_RAID = ModuleRegistry.AFTERMATH.register(NetherRaid.IDENTIFIER, () -> new NetherRaid());


    public static final Supplier<IEntityInfoModule> ENTITY_INFO = ModuleRegistry.ENTITY_INFO_MODULE.register(EntityInfoModule.IDENTIFIER, () -> new EntityInfoModule());
    public static final Supplier<IEntityInfoModule> ENTITY_INFO_PREDICATE = ModuleRegistry.ENTITY_INFO_MODULE.register(EntityInfoWithPredicateModule.IDENTIFIER, () -> new EntityInfoWithPredicateModule());


    public static final Supplier<IAmountModule> INTEGER_AMOUNT = ModuleRegistry.AMOUNT_MODULE.register(IntegerAmountModule.IDENTIFIER, () -> new IntegerAmountModule());
    public static final Supplier<IAmountModule> RANDOM_AMOUNT = ModuleRegistry.AMOUNT_MODULE.register(RandomAmountModule.IDENTIFIER, () -> new RandomAmountModule());


    public static final Supplier<IWeightedModule<EntityType<?>>> ENTITY_TYPE_WEIGHTED = ModuleRegistry.WEIGHTED_MODULE.register(EntityTypeWeightedModule.IDENTIFIER, () -> new EntityTypeWeightedModule());
    public static final Supplier<IWeightedModule<Item>> ITEM_WEIGHTED = ModuleRegistry.WEIGHTED_MODULE.register(ItemWeightedModule.IDENTIFIER, () -> new ItemWeightedModule());
    public static final Supplier<IWeightedModule<MobEffectInstance>> EFFECT_WEIGHTED = ModuleRegistry.WEIGHTED_MODULE.register(EffectWeightedModule.IDENTIFIER, () -> new EffectWeightedModule());
    public static final Supplier<IWeightedModule<AttributeWeightedModule.AttributeInfo>> ATTRIBUTE_WEIGHTED = ModuleRegistry.WEIGHTED_MODULE.register(AttributeWeightedModule.IDENTIFIER, () -> new AttributeWeightedModule());

    public static final Supplier<IConditionModule> LEVEL_CONDITION = ModuleRegistry.CONDITION_MODULE.register(StructureConditionModule.IDENTIFIER, () -> new StructureConditionModule());
    public static final Supplier<IConditionModule> BIOMES_CONDITION = ModuleRegistry.CONDITION_MODULE.register(BiomesConditionModule.IDENTIFIER, () -> new BiomesConditionModule());
    public static final Supplier<IConditionModule> Y_AXIS_HEIGHT_CONDITION = ModuleRegistry.CONDITION_MODULE.register(YAxisHeightConditionModule.IDENTIFIER, () -> new YAxisHeightConditionModule());
    public static final Supplier<IConditionModule> WEATHER_CONDITION = ModuleRegistry.CONDITION_MODULE.register(WeatherConditionModule.IDENTIFIER, () -> new WeatherConditionModule());
    public static final Supplier<IConditionModule> XP_CONDITION = ModuleRegistry.CONDITION_MODULE.register(XpConditionModule.IDENTIFIER, () -> new XpConditionModule());
    public static final Supplier<IConditionModule> PLAYER_STAGE_CONDITION = ModuleRegistry.CONDITION_MODULE.register(PlayerStageConditionModule.IDENTIFIER, () -> new PlayerStageConditionModule());
    public static final Supplier<IConditionModule> LEVEL_STAGE_CONDITION = ModuleRegistry.CONDITION_MODULE.register(LevelStageConditionModule.IDENTIFIER, () -> new LevelStageConditionModule());


    public static final Supplier<IPredicateModule> NBT_PREDICATE = ModuleRegistry.PREDICATE_MODULE.register(NBTPredicate.IDENTIFIER, () -> new NBTPredicate());
    public static final Supplier<IPredicateModule> EQUIPMENT_PREDICATE = ModuleRegistry.PREDICATE_MODULE.register(EquipmentPredicate.IDENTIFIER, () -> new EquipmentPredicate());
    public static final Supplier<IPredicateModule> EFFECT_PREDICATE = ModuleRegistry.PREDICATE_MODULE.register(EffectPredicate.IDENTIFIER, () -> new EffectPredicate());
    public static final Supplier<IPredicateModule> ATTRIBUTE_PREDICATE = ModuleRegistry.PREDICATE_MODULE.register(AttributePredicate.IDENTIFIER, () -> new AttributePredicate());


    public static final Supplier<ITracker> RAID_PLAYER_BATTLE_TRACKER = ModuleRegistry.TRACKER_MODULE.register("raid_player_battle_tracker", () -> new RaidPlayerBattleTracker());
    public static final Supplier<ITracker> MOB_BATTLE_TRACKER = ModuleRegistry.TRACKER_MODULE.register("mob_battle_tracker", () -> new MobBattleTracker());
    public static final Supplier<ITracker> RAID_MOB_BATTLE_TRACKER = ModuleRegistry.TRACKER_MODULE.register("raid_mob_battle_tracker", () -> new RaidMobBattleTracker());









}
