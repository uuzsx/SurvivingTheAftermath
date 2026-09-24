package com.pancake.surviving_the_aftermath;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.pancake.surviving_the_aftermath.api.*;
import com.pancake.surviving_the_aftermath.common.capability.AftermathCap;
import com.pancake.surviving_the_aftermath.common.event.AftermathEvent;
import com.pancake.surviving_the_aftermath.common.event.tracker.*;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.module.amount.IntegerAmountModule;
import com.pancake.surviving_the_aftermath.common.module.condition.*;
import com.pancake.surviving_the_aftermath.common.module.entity_info.EntityInfoModule;
import com.pancake.surviving_the_aftermath.common.module.weighted.ItemWeightedModule;
import com.pancake.surviving_the_aftermath.common.network.AftermathNetwork;
import com.pancake.surviving_the_aftermath.common.raid.*;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.structure.AbstractStructure;
import com.pancake.surviving_the_aftermath.common.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(SurvivingTheAftermath.MOD_ID)
@PrefixGameTestTemplate(false)
public class StabilityGameTests {
    private static final AftermathManager MANAGER = AftermathManager.getInstance();
    private static BaseRaidModule module() {
        return new BaseRaidModule("regression", new ItemWeightedModule.Builder().add(Items.APPLE, 1).build(),
                List.of(), List.of(List.of(new EntityInfoModule(EntityType.ZOMBIE, new IntegerAmountModule(1)))), 7, 13);
    }
    private static BaseRaid raid(GameTestHelper h) { return new BaseRaid(module(), h.getLevel(), h.absolutePos(new BlockPos(5, 2, 5))); }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }

    @GameTest(template = "stability_empty")
    public static void serverLifecycleAndRewardTimer(GameTestHelper h) {
        BaseRaid raid = raid(h);
        check(MANAGER.create(raid, h.getLevel(), raid.getStartPos(), null), "Encounter did not start");
        try {
            check(raid.getReadyTime() == 7 && raid.getRewardTime() == 13, "Reward timer copied ready timer");
            raid.state = AftermathState.ONGOING;
            raid.tick();
            check(raid.getCurrentWave() == -1 && !raid.isEnd(), "Empty arena advanced a wave");
            raid.state = AftermathState.READY;
            raid.tick();
            check(raid.getReadyTime() == 7, "Empty arena consumed preparation time");
            raid.state = AftermathState.VICTORY;
            raid.tick();
            check(raid.getRewardTime() == 12, "Reward tick did not run");
        } finally { raid.end(); MANAGER.tick(); }
        h.succeed();
    }

    public static final class Canceller {
        final UUID id;
        Canceller(UUID id) { this.id = id; }
        @SubscribeEvent public void start(AftermathEvent.Start event) { cancel(event); }
        @SubscribeEvent public void ready(AftermathEvent.Ready event) { cancel(event); }
        @SubscribeEvent public void celebrating(AftermathEvent.Celebrating event) { cancel(event); }
        private void cancel(AftermathEvent event) {
            if (event.getAftermath().getUUID().equals(id) && event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) cancellable.setCanceled(true);
        }
    }
    @GameTest(template = "stability_empty")
    public static void cancelledLifecycleStops(GameTestHelper h) {
        for (AftermathState state : List.of(AftermathState.START, AftermathState.READY, AftermathState.VICTORY)) {
            BaseRaid raid = raid(h);
            Canceller cancel = new Canceller(raid.getUUID());
            NeoForge.EVENT_BUS.register(cancel);
            try {
                if (state == AftermathState.START) {
                    check(!MANAGER.create(raid, h.getLevel(), raid.getStartPos(), null), "Cancelled Start registered encounter");
                } else {
                    raid.state = state == AftermathState.READY ? AftermathState.START : state;
                    raid.tick();
                }
                check(raid.isEnd(), "Cancelled lifecycle continued: " + state);
            } finally { NeoForge.EVENT_BUS.unregister(cancel); raid.end(); }
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void allConditionsAndMissingActor(GameTestHelper h) {
        var module = module();
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        module.setConditions(List.of(new YAxisHeightConditionModule(pos.getY(), 0), new YAxisHeightConditionModule(pos.getY() + 1, 0)));
        check(!module.isCreate(h.getLevel(), pos, null), "Only first level condition checked");
        module.setConditions(List.of(new YAxisHeightConditionModule(pos.getY(), 0), new XpConditionModule(1)));
        check(!module.isCreate(h.getLevel(), pos, null), "Missing player bypassed player condition");
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "condition_test"));
        player.experienceLevel = 2;
        check(module.isCreate(h.getLevel(), pos, player), "Valid conditions rejected");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void savedBattleIdentityAndLegacyUuid(GameTestHelper h) {
        UUID player = UUID.randomUUID(), enemy = UUID.randomUUID(), savedId = UUID.randomUUID();
        RaidPlayerBattleTracker tracker = new RaidPlayerBattleTracker(Set.of(player), Map.of(player, 2),
                Map.of(player, 10L), Map.of(player, Set.of(UUID.randomUUID())));
        BaseRaid raid = new BaseRaid(AftermathState.ONGOING, module(), Set.of(player), 0.5f,
                h.absolutePos(new BlockPos(5, 2, 5)), 7, 13, Set.of(h.absolutePos(new BlockPos(5, 2, 5))),
                Set.of(enemy), 0, 1, List.of(tracker, new MobBattleTracker(), new RaidMobBattleTracker()));
        raid.restore(h.getLevel(), savedId);
        MANAGER.getAftermathMap().put(savedId, raid);
        AftermathCap cap = new AftermathCap(h.getLevel());
        CompoundTag saved = cap.serializeNBT();
        MANAGER.getAftermathMap().remove(savedId);
        // Legacy UUID elements were compounds; map keys are now proper strings.
        CompoundTag oldPlayer = new CompoundTag(); oldPlayer.putString("uuid", player.toString());
        ListTag legacy = new ListTag(); legacy.add(oldPlayer);
        saved.getCompound(savedId.toString()).put("players", legacy);
        cap.deserializeNBT(saved);
        BaseRaid loaded = (BaseRaid) MANAGER.getAftermath(savedId).orElseThrow();
        try {
            check(loaded.getUUID().equals(savedId), "Battle UUID changed on load");
            check(loaded.getPlayers().contains(player) && loaded.getEnemies().contains(enemy), "Saved memberships lost");
            RaidPlayerBattleTracker restored = (RaidPlayerBattleTracker) loaded.getTrackers().get(0);
            check(restored.getDeathMap().get(player) == 2, "UUID-keyed death map lost");
            UUID joined = UUID.randomUUID();
            NeoForge.EVENT_BUS.post(new AftermathEvent.Ongoing(loaded, Set.of(joined), h.getLevel()));
            check(restored.getPlayers().contains(joined), "Loaded tracker not rebound or registered");
            loaded.tick();
            check(loaded.getEnemies().contains(enemy), "Unloaded enemy treated as dead");
        } finally { loaded.end(); MANAGER.tick(); }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void trackersIgnoreOtherBattles(GameTestHelper h) {
        BaseRaid a = raid(h), b = raid(h);
        RaidPlayerBattleTracker tracker = new RaidPlayerBattleTracker(); tracker.setUUID(a.getUUID());
        UUID player = UUID.randomUUID();
        tracker.updatePlayer(new AftermathEvent.Ongoing(b, Set.of(player), h.getLevel()));
        check(tracker.getPlayers().isEmpty(), "Tracker accepted foreign encounter");
        tracker.updatePlayer(new AftermathEvent.Ongoing(a, Set.of(player), h.getLevel()));
        check(tracker.getPlayers().contains(player), "Tracker ignored own encounter");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void amountIsDrawnOnce(GameTestHelper h) {
        int[] calls = {0};
        var amount = new IntegerAmountModule(3) {
            @Override public int getSpawnAmount() { calls[0]++; return super.getSpawnAmount(); }
        };
        var spawned = new EntityInfoModule(EntityType.ZOMBIE, amount).spawnEntity(h.getLevel());
        check(spawned.size() == 3 && calls[0] == 1, "Amount sampled during loop");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void largeMobsSpawnOutsideBlocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(new BlockPos(8, 2, 8));
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 4, 1))) level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        for (EntityType<? extends Mob> type : List.of(EntityType.HOGLIN, EntityType.MAGMA_CUBE, EntityType.GHAST)) {
            Mob mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (mob instanceof MagmaCube magma) magma.setSize(4, true);
            check(SafeSpawn.placeMob(level, mob, Set.of(center), center, 50), "No safe candidate found for " + type);
            check(SafeSpawn.isSafe(level, mob), "Body intersects blocks for " + type);
            if (type == EntityType.GHAST) check(mob.getY() >= center.getY() + 18, "Ghast height overwritten");
        }
        // A fully obstructed search must fail instead of placing an embedded entity.
        BlockPos blocked = h.absolutePos(new BlockPos(8, 2, 8));
        check(!SafeSpawn.placeMob(level, EntityType.HOGLIN.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT), Set.of(blocked), blocked, 1), "Blocked spawn accepted");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void movementRestrictedOnlyOutsideArena(GameTestHelper h) {
        BaseRaid raid = raid(h); MANAGER.getAftermathMap().put(raid.getUUID(), raid);
        var mob = EntityType.GHAST.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT); raid.insertTag(mob);
        var tracker = new RaidMobBattleTracker(); tracker.setUUID(raid.getUUID());
        try {
            mob.moveTo(raid.getStartPos().getX(), raid.getStartPos().getY(), raid.getStartPos().getZ());
            tracker.onLivingRestrictedRange(new EntityTickEvent.Post(mob));
            check(!mob.getPersistentData().contains("restricted_range"), "In-arena movement overridden");
            mob.moveTo(raid.getStartPos().getX() + 55, raid.getStartPos().getY(), raid.getStartPos().getZ());
            tracker.onLivingRestrictedRange(new EntityTickEvent.Post(mob));
            check(mob.getPersistentData().contains("restricted_range"), "Escaped mob not restricted");
        } finally { MANAGER.getAftermathMap().remove(raid.getUUID()); }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void endCleansTagsAndPreservesPreviousGlow(GameTestHelper h) {
        BaseRaid raid = raid(h);
        var mobs = new ArrayList<Mob>();
        for (boolean original : List.of(false, true)) {
            var mob = EntityType.ZOMBIE.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
            mob.moveTo(h.absolutePos(new BlockPos(original ? 4 : 2, 2, 2)).getCenter());
            h.getLevel().addFreshEntity(mob);
            raid.insertTag(mob); mob.setGlowingTag(original); BattleEntityState.highlight(mob);
            mob.getPersistentData().putBoolean("restricted_range", true);
            BattleEntityState.clear(mob, UUID.randomUUID());
            check(BattleEntityState.belongsTo(mob, raid.getUUID()), "Foreign cleanup removed state");
            mobs.add(mob);
        }
        raid.end();
        for (int i = 0; i < mobs.size(); i++) {
            Mob mob = mobs.get(i);
            check(mob.hasGlowingTag() == (i == 1) && !mob.getPersistentData().contains("raid_uuid")
                    && !mob.getPersistentData().contains("restricted_range"), "Encounter cleanup left state");
            mob.discard();
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void offlineSpectatorRecovery(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "recovery_test"));
        player.setGameMode(GameType.ADVENTURE);
        PlayerRecovery.mark(player, UUID.randomUUID(), GameType.ADVENTURE);
        player.setGameMode(GameType.SPECTATOR);
        PlayerRecovery.restore(h.getLevel().getServer(), player.getUUID(), GameType.ADVENTURE);
        PlayerRecovery.login(new PlayerEvent.PlayerLoggedInEvent(player));
        check(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "Offline original mode not restored");
        check(player.getCamera() == player, "Spectator camera not reset");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void authoredLootAndDecorativeBarrels(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(origin, Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(origin.east(2), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(origin.east(4), Blocks.CHEST.defaultBlockState());
        var authored = (RandomizableContainerBlockEntity) level.getBlockEntity(origin.east(4));
        authored.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 123L);
        ResourceLocation id = SurvivingTheAftermath.asResource("regression_loot");
        var template = level.getStructureManager().getOrCreate(id);
        template.fillFromWorld(level, origin, new Vec3i(5, 1, 1), false, Blocks.STRUCTURE_VOID);
        var piece = new AbstractStructure.Piece(ModStructurePieceTypes.CITY.get(), level.getStructureManager(), id, origin, Rotation.NONE);
        var city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        city.afterPlace(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random,
                BoundingBox.fromCorners(origin, origin.offset(5, 1, 1)), new ChunkPos(origin), new PiecesContainer(List.of(piece)));
        check(!level.getBlockEntity(origin).saveWithoutMetadata(level.registryAccess()).contains("LootTable"), "Decorative barrel gained treasure");
        check(level.getBlockEntity(origin.east(2)).saveWithoutMetadata(level.registryAccess()).getString("LootTable").equals(BuiltInLootTables.DESERT_PYRAMID.location().toString()), "Treasure chest lost fallback");
        check(authored.saveWithoutMetadata(level.registryAccess()).getString("LootTable").equals(BuiltInLootTables.SIMPLE_DUNGEON.location().toString()), "Authored loot table overwritten");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void reloadReplacesModules(GameTestHelper h) {
        var old = ArrayListMultimap.create(MANAGER.getAftermathModuleMap());
        var modules = ArrayListMultimap.<ResourceLocation, com.pancake.surviving_the_aftermath.api.module.IAftermathModule>create();
        modules.put(SurvivingTheAftermath.asResource("regression"), module());
        try {
            MANAGER.fillAftermathModuleMap(modules); MANAGER.fillAftermathModuleMap(modules);
            check(MANAGER.getAftermathModuleMap().size() == 1, "Reload accumulated duplicate modules");
        } finally { MANAGER.fillAftermathModuleMap(old); }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void bossBarPacketRoundTrip(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        var packet = new AftermathNetwork.BarPacket(id, NetherRaid.BARS_RESOURCE, new int[]{192, 23, 182, 4, 5, 4});
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.encode(buffer);
            var decoded = AftermathNetwork.BarPacket.decode(buffer);
            check(decoded.id().equals(id) && decoded.texture().equals(packet.texture()) && Arrays.equals(decoded.offsets(), packet.offsets()), "Boss bar metadata changed in transit");
            new AftermathNetwork.BarPacket(id, null, new int[0]).encode(buffer);
            check(AftermathNetwork.BarPacket.decode(buffer).texture() == null, "Boss bar removal lost");
        } finally { buffer.release(); }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void netherEventsOnDedicatedServer(GameTestHelper h) {
        NetherRaid raid = new NetherRaid(AftermathState.START, module(), Set.of(), 0f,
                h.absolutePos(new BlockPos(5, 2, 5)), 7, 13, Set.of(), Set.of(), -1, 0, List.of());
        raid.setLevel(h.getLevel());
        check(AftermathEventUtil.start(raid, Set.of(), h.getLevel()), "Nether start failed");
        AftermathEventUtil.victory(raid, Set.of(), h.getLevel());
        raid.end();
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void absentAndIneligibleModulesDoNotCrash(GameTestHelper h) {
        var modules = com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader.AFTERMATH_MODULE_MAP;
        ResourceLocation key = SurvivingTheAftermath.asResource(BaseRaid.IDENTIFIER);
        var old = List.copyOf(modules.get(key));
        try {
            modules.removeAll(key);
            check(!new BaseRaid(h.getLevel(), BlockPos.ZERO).isCreate(h.getLevel(), BlockPos.ZERO, null), "Empty registry accepted");
            var rejected = module(); rejected.setConditions(List.of(new XpConditionModule(99)));
            modules.put(key, rejected);
            check(!new BaseRaid(h.getLevel(), BlockPos.ZERO).isCreate(h.getLevel(), BlockPos.ZERO, null), "Rejected module selected");
            var accepted = module(); modules.put(key, accepted);
            BaseRaid raid = new BaseRaid(h.getLevel(), BlockPos.ZERO);
            check(raid.isCreate(h.getLevel(), BlockPos.ZERO, null) && raid.getModule() == accepted, "Valid alternative not selected");
        } finally { modules.removeAll(key); modules.putAll(key, old); }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void deathRespawnAndEndRestoreSpectators(GameTestHelper h) throws ReflectiveOperationException {
        ServerLevel level = h.getLevel();
        // FakePlayer has a packet sink; register UUID lookups for this synchronous fixture.
        var first = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "watcher_one"));
        var second = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "watcher_two"));
        var survivor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "survivor"));
        var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var byId = (Map<UUID, net.minecraft.server.level.ServerPlayer>) field.get(level.getServer().getPlayerList());
        for (var player : List.of(first, second, survivor)) {
            player.moveTo(h.absolutePos(new BlockPos(5, 2, 5)).getCenter());
            byId.put(player.getUUID(), player);
            level.addNewPlayer(player);
        }
        first.setGameMode(GameType.ADVENTURE); second.setGameMode(GameType.SURVIVAL);
        BaseRaid raid = raid(h); raid.state = AftermathState.ONGOING;
        var tracker = new RaidPlayerBattleTracker(); tracker.setUUID(raid.getUUID());
        raid.getTrackers().add(tracker);
        MANAGER.getAftermathMap().put(raid.getUUID(), raid);
        try {
            Set<UUID> ids = Set.of(first.getUUID(), second.getUUID(), survivor.getUUID());
            raid.getPlayers().addAll(ids);
            tracker.updatePlayer(new AftermathEvent.Ongoing(raid, ids, level));
            tracker.onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(first, level.damageSources().generic()));
            tracker.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(first, false));
            check(first.isSpectator(), "Dead teammate did not spectate survivor");
            tracker.onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(second, level.damageSources().generic()));
            tracker.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(second, false));
            check(second.isSpectator(), "Second dead teammate did not spectate");
            check(tracker.getSpectatorMap().values().stream().mapToInt(Set::size).sum() == 2, "Watcher set overwritten");
            raid.end();
            check(first.gameMode.getGameModeForPlayer() == GameType.ADVENTURE && second.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "End failed to restore each original game mode");
            check(first.getCamera() == first && second.getCamera() == second, "End left foreign camera");
        } catch (RuntimeException exception) {
            SurvivingTheAftermath.LOGGER.error("Multiplayer regression failed", exception);
            throw exception;
        } finally {
            raid.end(); MANAGER.getAftermathMap().remove(raid.getUUID());
            for (var player : List.of(first, second, survivor)) {
                level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
                byId.remove(player.getUUID());
            }
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void kubeJsLifecycleCancellation(GameTestHelper h) {
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            for (String phase : List.of("start", "ready", "celebrating")) {
                var module = module(); module.setName("regression_cancel_" + phase);
                BaseRaid raid = new BaseRaid(module, h.getLevel(), h.absolutePos(new BlockPos(5, 2, 5)));
                if (phase.equals("start")) check(!MANAGER.create(raid, h.getLevel(), raid.getStartPos(), null), "KubeJS start cancellation ignored");
                else { raid.state = phase.equals("ready") ? AftermathState.START : AftermathState.VICTORY; raid.tick(); }
                check(raid.isEnd(), "KubeJS cancellation ignored for " + phase);
            }
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void missingEnemyDoesNotCompleteOccupiedWave(GameTestHelper h) {
        UUID enemy = UUID.randomUUID();
        BaseRaid raid = new BaseRaid(AftermathState.ONGOING, module(), Set.of(UUID.randomUUID()), 1f,
                h.absolutePos(new BlockPos(5, 2, 5)), 7, 13, Set.of(), Set.of(enemy), 0, 1, List.of()) {
            @Override public void updatePlayers() { /* Keep a participant while simulating an unloaded enemy. */ }
        };
        raid.setLevel(h.getLevel());
        raid.tick();
        check(raid.getState() == AftermathState.ONGOING && raid.getEnemies().contains(enemy), "Unloaded enemy completed occupied wave");
        raid.end();
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void escapedPlayerIsRemovedFromPenaltyQueue(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BaseRaid raid = raid(h);
        MANAGER.getAftermathMap().put(raid.getUUID(), raid);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "escape_test"));
        player.moveTo(raid.getStartPos().getX() + 150, raid.getStartPos().getY(), raid.getStartPos().getZ());
        var tracker = new RaidPlayerBattleTracker(); tracker.setUUID(raid.getUUID());
        tracker.getEscapeMap().put(player.getUUID(), level.getGameTime() - 101);
        var tick = new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player);
        try {
            tracker.onPlayerEscape(tick);
            check(!tracker.getEscapeMap().containsKey(player.getUUID()), "Escape queue removed battle UUID instead of player UUID");
            check(player.hasEffect(ModMobEffects.COWARDICE), "Escape effect was not applied");
            player.removeEffect(ModMobEffects.COWARDICE);
            tracker.onPlayerEscape(tick);
            check(!player.hasEffect(ModMobEffects.COWARDICE), "Penalty kept being reapplied");
        } finally { raid.end(); MANAGER.getAftermathMap().remove(raid.getUUID()); }
        h.succeed();
    }
    @GameTest(template = "stability_empty")
    public static void attachmentSerializationRoundTrip(GameTestHelper h) {
        var source = new net.neoforged.neoforge.attachment.AttachmentHolder.AsField(h.getLevel());
        var stageType = com.pancake.surviving_the_aftermath.common.init.ModCapability.STAGE_CAP;
        source.getData(stageType).getStages().add("regression_saved_stage");
        source.getData(com.pancake.surviving_the_aftermath.common.init.ModCapability.AFTERMATH_CAP);
        var original = raid(h);
        var id = original.getUUID();
        MANAGER.getAftermathMap().put(id, original);

        var saved = source.serializeAttachments(h.getLevel().registryAccess());
        check(saved != null && !saved.isEmpty(), "Attachment serializer produced no data");
        original.end(); MANAGER.tick();
        var restored = new net.neoforged.neoforge.attachment.AttachmentHolder.AsField(h.getLevel());
        restored.deserializeInternal(h.getLevel().registryAccess(), saved);

        var loaded = (BaseRaid) MANAGER.getAftermath(id).orElseThrow(() -> new IllegalStateException("Battle lost by NeoForge attachment deserialization"));
        try {
            check(restored.getData(stageType).getStages().contains("regression_saved_stage"), "Progression stage lost by NeoForge attachment deserialization");
            check(loaded.getUUID().equals(id), "Attachment changed battle identity");
        } finally { loaded.end(); MANAGER.tick(); }
        h.succeed();
    }
}
