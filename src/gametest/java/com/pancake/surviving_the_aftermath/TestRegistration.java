package com.pancake.surviving_the_aftermath;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
@EventBusSubscriber(modid=SurvivingTheAftermath.MOD_ID)
public class TestRegistration {
 @FunctionalInterface interface CheckedTest { void run(net.minecraft.gametest.framework.GameTestHelper helper) throws Exception; }
 private static java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper> wrap(CheckedTest test) {
  return h -> { try { test.run(h); } catch (Exception e) { throw new RuntimeException(e); } };
 }
 @SubscribeEvent public static void register(RegisterEvent event) {
  event.register(Registries.TEST_FUNCTION, helper -> {
   helper.register(SurvivingTheAftermath.asResource("citygradingpreservesregisteredbuilding"), wrap(CityAvoidanceGameTests::cityGradingPreservesRegisteredBuilding));
   helper.register(SurvivingTheAftermath.asResource("cityrejectsvillagecollision"), wrap(CityAvoidanceGameTests::cityRejectsVillageCollision));
   helper.register(SurvivingTheAftermath.asResource("citysavedcanopieskeepplayerdecorations"), wrap(CityVegetationGameTests::citySavedCanopiesKeepPlayerDecorations));
   helper.register(SurvivingTheAftermath.asResource("citylatecanopiesareremoved"), wrap(CityVegetationGameTests::cityLateCanopiesAreRemoved));
   helper.register(SurvivingTheAftermath.asResource("terraincityfrequencysurvey"), wrap(TerrainPlacementGameTests::terrainCityFrequencySurvey));
   helper.register(SurvivingTheAftermath.asResource("terraincitysettingsandgentleslope"), wrap(TerrainPlacementGameTests::terrainCitySettingsAndGentleSlope));
   helper.register(SurvivingTheAftermath.asResource("terrainfullcityfoundationandtransition"), wrap(TerrainPlacementGameTests::terrainFullCityFoundationAndTransition));
   helper.register(SurvivingTheAftermath.asResource("terrainrealnoisesiteselection"), wrap(TerrainPlacementGameTests::terrainRealNoiseSiteSelection));
   helper.register(SurvivingTheAftermath.asResource("terrainplanscheckfootprint"), wrap(TerrainPlacementGameTests::terrainPlansCheckFootprint));
   helper.register(SurvivingTheAftermath.asResource("terraincityandraidclearandsupport"), wrap(TerrainPlacementGameTests::terrainCityAndRaidClearAndSupport));
   helper.register(SurvivingTheAftermath.asResource("terrainplacementsurvivespiecereload"), wrap(TerrainPlacementGameTests::terrainPlacementSurvivesPieceReload));
   helper.register(SurvivingTheAftermath.asResource("terrainlegacypieceskeepoldplacement"), wrap(TerrainPlacementGameTests::terrainLegacyPiecesKeepOldPlacement));

   helper.register(SurvivingTheAftermath.asResource("skippedblockskeepworldstate"), wrap(StairTransformationGameTests::skippedBlocksKeepWorldState));
   helper.register(SurvivingTheAftermath.asResource("convertedstairskeepproperties"), wrap(StairTransformationGameTests::convertedStairsKeepProperties));
   helper.register(SurvivingTheAftermath.asResource("mixedwaveskeepstairgeometry"), wrap(StairTransformationGameTests::mixedWavesKeepStairGeometry));
   helper.register(SurvivingTheAftermath.asResource("defaultwavepiglinshaveweapons"), wrap(Rc4GameTests::defaultWavePiglinsHaveWeapons));
   helper.register(SurvivingTheAftermath.asResource("configuredequipmentoverridesdefaults"), wrap(Rc4GameTests::configuredEquipmentOverridesDefaults));
   helper.register(SurvivingTheAftermath.asResource("dungeonmobsdropnolootorexperience"), wrap(Rc4GameTests::dungeonMobsDropNoLootOrExperience));
   helper.register(SurvivingTheAftermath.asResource("splitmagmacubeskeepdungeonlootpolicy"), wrap(Rc4GameTests::splitMagmaCubesKeepDungeonLootPolicy));
   helper.register(SurvivingTheAftermath.asResource("buildingmusicstopswhenlastlistenerleaves"), wrap(Rc4GameTests::buildingMusicStopsWhenLastListenerLeaves));
   helper.register(SurvivingTheAftermath.asResource("musicpacketandspatialasset"), wrap(Rc4GameTests::musicPacketAndSpatialAsset));
   helper.register(SurvivingTheAftermath.asResource("restoredregistryandlanguage"), wrap(ContentGameTests::restoredRegistryAndLanguage));
   helper.register(SurvivingTheAftermath.asResource("restoredfoodconsumption"), wrap(ContentGameTests::restoredFoodConsumption));
   helper.register(SurvivingTheAftermath.asResource("cowardicepricesandlegacytrades"), wrap(ContentGameTests::cowardicePricesAndLegacyTrades));
   helper.register(SurvivingTheAftermath.asResource("restoreddiscinjukebox"), wrap(ContentGameTests::restoredDiscInJukebox));
   helper.register(SurvivingTheAftermath.asResource("restoredcombatenchantments"), wrap(ContentGameTests::restoredCombatEnchantments));
   helper.register(SurvivingTheAftermath.asResource("restoredgrowthenchantments"), wrap(ContentGameTests::restoredGrowthEnchantments));
   helper.register(SurvivingTheAftermath.asResource("attachmentserializationroundtrip"), wrap(StabilityGameTests::attachmentSerializationRoundTrip));
   helper.register(SurvivingTheAftermath.asResource("eachwavetransformsbeforespawningandvictoryrewardsimmediately"), wrap(DungeonLifecycleGameTests::eachWaveTransformsBeforeSpawningAndVictoryRewardsImmediately));
   helper.register(SurvivingTheAftermath.asResource("diamondreciperequiresbothingredients"), wrap(DungeonLifecycleGameTests::diamondRecipeRequiresBothIngredients));
   helper.register(SurvivingTheAftermath.asResource("victoryendsdeathandescapepenalties"), wrap(DungeonLifecycleGameTests::victoryEndsDeathAndEscapePenalties));
   helper.register(SurvivingTheAftermath.asResource("diamondportallifecyclex"), wrap(DungeonLifecycleGameTests::diamondPortalLifecycleX));
   helper.register(SurvivingTheAftermath.asResource("diamondportallifecyclez"), wrap(DungeonLifecycleGameTests::diamondPortalLifecycleZ));
   helper.register(SurvivingTheAftermath.asResource("ordinaryportalsoutsidedungeonstillwork"), wrap(DungeonLifecycleGameTests::ordinaryPortalsOutsideDungeonStillWork));
   helper.register(SurvivingTheAftermath.asResource("serverlifecycleandrewardtimer"), wrap(StabilityGameTests::serverLifecycleAndRewardTimer));
   helper.register(SurvivingTheAftermath.asResource("cancelledlifecyclestops"), wrap(StabilityGameTests::cancelledLifecycleStops));
   helper.register(SurvivingTheAftermath.asResource("allconditionsandmissingactor"), wrap(StabilityGameTests::allConditionsAndMissingActor));
   helper.register(SurvivingTheAftermath.asResource("savedbattleidentityandlegacyuuid"), wrap(StabilityGameTests::savedBattleIdentityAndLegacyUuid));
   helper.register(SurvivingTheAftermath.asResource("trackersignoreotherbattles"), wrap(StabilityGameTests::trackersIgnoreOtherBattles));
   helper.register(SurvivingTheAftermath.asResource("amountisdrawnonce"), wrap(StabilityGameTests::amountIsDrawnOnce));
   helper.register(SurvivingTheAftermath.asResource("largemobsspawnoutsideblocks"), wrap(StabilityGameTests::largeMobsSpawnOutsideBlocks));
   helper.register(SurvivingTheAftermath.asResource("movementrestrictedonlyoutsidearena"), wrap(StabilityGameTests::movementRestrictedOnlyOutsideArena));
   helper.register(SurvivingTheAftermath.asResource("endcleanstagsandpreservespreviousglow"), wrap(StabilityGameTests::endCleansTagsAndPreservesPreviousGlow));
   helper.register(SurvivingTheAftermath.asResource("offlinespectatorrecovery"), wrap(StabilityGameTests::offlineSpectatorRecovery));
   helper.register(SurvivingTheAftermath.asResource("authoredlootanddecorativebarrels"), wrap(StabilityGameTests::authoredLootAndDecorativeBarrels));
   helper.register(SurvivingTheAftermath.asResource("reloadreplacesmodules"), wrap(StabilityGameTests::reloadReplacesModules));
   helper.register(SurvivingTheAftermath.asResource("bossbarpacketroundtrip"), wrap(StabilityGameTests::bossBarPacketRoundTrip));
   helper.register(SurvivingTheAftermath.asResource("nethereventsondedicatedserver"), wrap(StabilityGameTests::netherEventsOnDedicatedServer));
   helper.register(SurvivingTheAftermath.asResource("absentandineligiblemodulesdonotcrash"), wrap(StabilityGameTests::absentAndIneligibleModulesDoNotCrash));
   helper.register(SurvivingTheAftermath.asResource("deathrespawnandendrestorespectators"), wrap(StabilityGameTests::deathRespawnAndEndRestoreSpectators));
   helper.register(SurvivingTheAftermath.asResource("kubejslifecyclecancellation"), wrap(StabilityGameTests::kubeJsLifecycleCancellation));
   helper.register(SurvivingTheAftermath.asResource("missingenemydoesnotcompleteoccupiedwave"), wrap(StabilityGameTests::missingEnemyDoesNotCompleteOccupiedWave));
   helper.register(SurvivingTheAftermath.asResource("escapedplayerisremovedfrompenaltyqueue"), wrap(StabilityGameTests::escapedPlayerIsRemovedFromPenaltyQueue));
  });
 }
}
