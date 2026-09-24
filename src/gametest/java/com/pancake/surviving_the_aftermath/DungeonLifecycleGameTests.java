package com.pancake.surviving_the_aftermath;

import com.mojang.authlib.GameProfile;
import com.pancake.surviving_the_aftermath.api.*;
import com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.module.amount.IntegerAmountModule;
import com.pancake.surviving_the_aftermath.common.module.condition.XpConditionModule;
import com.pancake.surviving_the_aftermath.common.module.entity_info.EntityInfoModule;
import com.pancake.surviving_the_aftermath.common.module.weighted.ItemWeightedModule;
import com.pancake.surviving_the_aftermath.common.raid.*;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.structure.AbstractStructure;
import com.pancake.surviving_the_aftermath.common.util.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(SurvivingTheAftermath.MOD_ID)
@PrefixGameTestTemplate(false)
public class DungeonLifecycleGameTests {
    private static final AftermathManager MANAGER = AftermathManager.getInstance();
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
    private static BaseRaidModule module() {
        var wave = List.<com.pancake.surviving_the_aftermath.api.module.IEntityInfoModule>of(
                new EntityInfoModule(EntityType.ZOMBIE, new IntegerAmountModule(1)));
        return new BaseRaidModule("dungeon_regression", new ItemWeightedModule.Builder().add(Items.APPLE, 1).build(),
                List.of(), List.of(wave, wave), 0, 3);
    }

    public static final class Sounds {
        final ServerLevel level;
        int horns;
        Sounds(ServerLevel level) { this.level = level; }
        @SubscribeEvent public void sound(PlayLevelSoundEvent.AtPosition event) {
            if (event.getLevel() != level || event.getSound() == null) return;
            if (event.getSound().value() == SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value()) horns++;
        }
    }

    @GameTest(template = "stability_empty")
    public static void eachWaveTransformsBeforeSpawningAndVictoryRewardsImmediately(GameTestHelper h) {
        int[] transforms = {0}, rewards = {0};
        Sounds sounds = new Sounds(h.getLevel());
        var listener = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "music_timing"));
        listener.moveTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 2, 5))));
        h.getLevel().addNewPlayer(listener);
        var raid = new NetherRaid(AftermathState.ONGOING, module(), Set.of(UUID.randomUUID()), 0f,
                h.absolutePos(new BlockPos(5, 2, 5)), 0, 3, Set.of(), Set.of(), -1, 0, List.of()) {
            @Override public void updatePlayers() {}
            @Override protected void updateStructure() { transforms[0]++; }
            @Override public void setMobSpawn(ServerLevel level, Mob mob) {
                check(transforms[0] == getCurrentWave() + 1, "Mobs spawned before the structure transformed");
                enemies.add(mob.getUUID()); totalEnemy++;
            }
            @Override public void createRewards() { rewards[0]++; }
        };
        raid.setLevel(h.getLevel());
        NeoForge.EVENT_BUS.register(sounds);
        try {
            raid.tick();
            check(transforms[0] == 1 && sounds.horns == 1 && raid.getCurrentWave() == 0, "First wave did not transform once");
            raid.tick();
            check(transforms[0] == 1 && sounds.horns == 1, "Active wave repeated transformation or horn");
            raid.getEnemies().clear(); raid.tick();
            check(transforms[0] == 2 && sounds.horns == 2 && raid.getCurrentWave() == 1, "Second wave did not transform");
            raid.getEnemies().clear(); raid.tick();
            check(RaidMusic.playbackId(raid.level, raid.getStartPos()).filter(raid.getUUID()::equals).isPresent() && rewards[0] == 1 && raid.state == AftermathState.CELEBRATING,
                    "Victory did not play one song and issue the first reward in the same tick");
            raid.tick(); raid.tick(); raid.tick();
            check(raid.isEnd() && rewards[0] == 3 && RaidMusic.playbackId(raid.level, raid.getStartPos()).filter(raid.getUUID()::equals).isPresent() && transforms[0] == 2,
                    "Reward completion, music count, or final transformation incorrect");
        } finally {
            RaidMusic.stop(h.getLevel(), raid.getStartPos());
            h.getLevel().removePlayerImmediately(listener, Entity.RemovalReason.DISCARDED);
            raid.end(); NeoForge.EVENT_BUS.unregister(sounds); }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void diamondRecipeRequiresBothIngredients(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "recipe_test"));
        var grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 2, 2);
        grid.setItem(0, new ItemStack(Items.DIAMOND));
        check(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), h.getLevel()).isEmpty(), "Diamond alone crafted the tool");
        grid.setItem(3, new ItemStack(Items.FLINT_AND_STEEL));
        check(h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), h.getLevel()).isEmpty(), "Old flint-and-steel ingredient still accepted");
        grid.setItem(3, new ItemStack(Items.FLINT));
        var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), h.getLevel()).orElseThrow();
        var result = recipe.value().assemble(grid.asCraftInput(), h.getLevel().registryAccess());
        check(result.is(ModItems.DIAMOND_FLINT_AND_STEEL.get()) && result.getCount() == 1 && result.getMaxDamage() == 64,
                "Diamond flint and steel recipe or durability incorrect");
        grid.setItem(1, new ItemStack(Items.IRON_INGOT));
        check(!recipe.value().matches(grid.asCraftInput(), h.getLevel()), "Recipe accepted an extra ingredient");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void victoryEndsDeathAndEscapePenalties(GameTestHelper h) {
        var level = h.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "victory_safety"));
        BlockPos pos = h.absolutePos(new BlockPos(5, 2, 5));
        player.moveTo(pos.east(150).getCenter());
        var raid = new BaseRaid(module(), level, pos);
        raid.state = AftermathState.ONGOING;
        raid.getPlayers().add(player.getUUID());
        var tracker = new com.pancake.surviving_the_aftermath.common.event.tracker.RaidPlayerBattleTracker();
        tracker.setUUID(raid.getUUID()); raid.getTrackers().add(tracker);
        tracker.getDeathMap().put(player.getUUID(), 3);
        tracker.getEscapeMap().put(player.getUUID(), level.getGameTime() - 101);
        MANAGER.getAftermathMap().put(raid.getUUID(), raid);
        NeoForge.EVENT_BUS.register(tracker);
        try {
            AftermathEventUtil.victory(raid, raid.getPlayers(), level);
            check(tracker.getDeathMap().isEmpty() && tracker.getEscapeMap().isEmpty(), "Victory retained combat penalties");
            // No new death bookkeeping may start after the victory event.
            tracker.onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(player, level.damageSources().generic()));
            tracker.onPlayerRespawn(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(player, false));
            check(tracker.getDeathMap().isEmpty() && raid.state == AftermathState.VICTORY, "Post-victory death interrupted rewards");
            // A stale countdown from an old celebrating save must also be discarded.
            raid.state = AftermathState.CELEBRATING;
            tracker.getEscapeMap().put(player.getUUID(), level.getGameTime() - 101);
            tracker.onPlayerEscape(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
            check(!player.hasEffect(ModMobEffects.COWARDICE) && tracker.getEscapeMap().isEmpty(), "Post-victory escape applied punishment");
        } finally {
            raid.end(); NeoForge.EVENT_BUS.unregister(tracker); MANAGER.getAftermathMap().remove(raid.getUUID());
        }
        h.succeed();
    }

    /** Register a small real structure footprint, restoring all chunk metadata afterwards. */
    static AutoCloseable arena(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.NETHER_RAID).value();
        var piece = new AbstractStructure.Piece(ModStructurePieceTypes.NETHER_RAID.get(), level.getStructureManager(),
                SurvivingTheAftermath.asResource("stability_empty"), origin, Rotation.NONE);
        var startChunk = level.getChunkAt(origin);
        var savedStarts = new HashMap<>(startChunk.getAllStarts());
        List<Runnable> restore = new ArrayList<>();
        var box = piece.getBoundingBox();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                var chunk = level.getChunk(x, z);
                var old = new HashMap<>(chunk.getAllReferences());
                old.replaceAll((key, refs) -> new LongOpenHashSet(refs));
                restore.add(() -> chunk.setAllReferences(old));
                chunk.addReferenceForStructure(structure, startChunk.getPos().toLong());
            }
        }
        startChunk.setStartForStructure(structure, new StructureStart(structure, startChunk.getPos(), 0, new PiecesContainer(List.of(piece))));
        return () -> { startChunk.setAllStarts(savedStarts); restore.forEach(Runnable::run); };
    }

    private static Set<BlockPos> frame(ServerLevel level, BlockPos base, Direction.Axis axis) {
        Set<BlockPos> interior = new HashSet<>();
        for (int w = -1; w <= 2; w++) for (int y = -1; y <= 3; y++) {
            BlockPos p = base.offset(axis == Direction.Axis.X ? w : 0, y, axis == Direction.Axis.Z ? w : 0);
            boolean edge = w == -1 || w == 2 || y == -1 || y == 3;
            level.setBlockAndUpdate(p, (edge ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState());
            if (!edge) interior.add(p);
        }
        return interior;
    }

    private static InteractionResult ignite(FakePlayer player, ItemStack tool, BlockPos clicked, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        return tool.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false)));
    }

    @GameTest(template = "stability_empty")
    public static void diamondPortalLifecycleX(GameTestHelper h) throws Exception { portalLifecycle(h, Direction.Axis.X); }

    @GameTest(template = "stability_empty")
    public static void diamondPortalLifecycleZ(GameTestHelper h) throws Exception { portalLifecycle(h, Direction.Axis.Z); }

    private static void portalLifecycle(GameTestHelper h, Direction.Axis axis) throws Exception {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(6, 3, 6));
        Set<BlockPos> plane = frame(level, pos, axis);
        var modules = AftermathModuleLoader.AFTERMATH_MODULE_MAP;
        ResourceLocation key = SurvivingTheAftermath.asResource(BaseRaid.IDENTIFIER);
        var oldModules = List.copyOf(modules.get(key));
        var battleModule = module();
        modules.removeAll(key); modules.put(key, battleModule);
        Set<UUID> oldBattles = new HashSet<>(MANAGER.getAftermathMap().keySet());
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "portal_test"));
        player.moveTo(pos.south(2).getCenter());
        player.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(player);
        ItemStack tool = new ItemStack(ModItems.DIAMOND_FLINT_AND_STEEL.get());
        Sounds sounds = new Sounds(level);
        NeoForge.EVENT_BUS.register(sounds);
        try (var ignored = arena(h)) {
            check(RaidPortal.isArena(level, pos), "Fixture structure not recognized");
            for (Item ordinary : List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)) {
                ignite(player, new ItemStack(ordinary), pos.below(), Direction.UP);
                check(plane.stream().noneMatch(p -> level.getBlockState(p).is(Blocks.NETHER_PORTAL)), "Ordinary ignition opened a dungeon");
                check(MANAGER.getAftermathMap().keySet().equals(oldBattles), "Ordinary ignition created a battle");
                level.removeBlock(pos, false);
            }
            var shape = PortalShape.findEmptyPortalShape(level, pos, axis).orElseThrow();
            var natural = new net.neoforged.neoforge.event.level.BlockEvent.PortalSpawnEvent(level, pos, Blocks.FIRE.defaultBlockState(), shape);
            check(NeoForge.EVENT_BUS.post(natural).isCanceled(), "Natural fire bypassed diamond requirement");
            battleModule.setConditions(List.of(new XpConditionModule(2)));
            player.experienceLevel = 0;
            check(ignite(player, tool, pos.below(), Direction.UP) == InteractionResult.FAIL && tool.getDamageValue() == 0,
                    "Failed conditions consumed durability or opened dungeon");
            player.experienceLevel = 2;
            check(ignite(player, tool, pos.below(), Direction.UP).consumesAction(), "Diamond activation failed");
            check(tool.getDamageValue() == 1 && plane.stream().allMatch(p -> level.getBlockState(p).is(Blocks.NETHER_PORTAL)), "Activation did not create portal or consume exactly one durability");
            NetherRaid raid = (NetherRaid) MANAGER.getAftermathMap().values().stream().filter(a -> !oldBattles.contains(a.getUUID())).findFirst().orElseThrow();
            check(raid.getPortalBlocks().equals(plane), "Portal plane was not captured completely");
            check(ignite(player, tool, pos, Direction.UP) == InteractionResult.FAIL && tool.getDamageValue() == 1,
                    "Repeated activation created another battle or consumed durability");
            AftermathEventUtil.victory(raid, raid.getPlayers(), level);
            raid.tick();
            check(RaidMusic.playbackId(raid.level, raid.getStartPos()).filter(raid.getUUID()::equals).isPresent() && raid.getRewardTime() == 2 && !raid.isEnd(), "Victory/reward phase incorrect");
            check(ignite(player, tool, pos, Direction.UP) == InteractionResult.FAIL && tool.getDamageValue() == 1,
                    "Portal accepted another challenge while issuing rewards");

            check(RaidMusic.playbackId(level, pos).filter(raid.getUUID()::equals).isPresent(), "Rejected reactivation stopped the song");

            // Save while rewarding and reload: no extra music, no lost portal ownership.
            UUID id = raid.getUUID();
            CompoundTag saved = (CompoundTag) IAftermath.CODEC.get().encodeStart(NbtOps.INSTANCE, raid).result().orElseThrow();
            raid.getTrackers().forEach(ITracker::unregister);
            MANAGER.getAftermathMap().remove(id);
            MANAGER.create(level, id, saved);
            NetherRaid loaded = (NetherRaid) MANAGER.getAftermath(id).orElseThrow();
            check(loaded.getPortalBlocks().equals(plane), "Portal plane lost on save/load");
            loaded.tick(); loaded.tick();
            check(loaded.isEnd() && RaidMusic.playbackId(raid.level, raid.getStartPos()).filter(raid.getUUID()::equals).isPresent(), "Reload repeated victory song or failed to finish");
            check(plane.stream().noneMatch(p -> level.getBlockState(p).is(Blocks.NETHER_PORTAL))
                    && level.getBlockState(pos.below()).is(Blocks.OBSIDIAN), "Reward completion did not close portal safely");
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5)).size() == 3, "Wrong number of reward drops");
            // Do not tick the manager: even before END is removed, immediate reuse must work.
            check(ignite(player, tool, pos.below(), Direction.UP).consumesAction() && tool.getDamageValue() == 2,
                    "Could not immediately restart after rewards");
            check(RaidMusic.playbackId(level, pos).isEmpty(), "Successful reactivation kept the victory song playing");
            var repeated = MANAGER.getAftermathMap().values().stream().filter(a -> !oldBattles.contains(a.getUUID()) && !a.isEnd()).findFirst().orElseThrow();
            ((NetherRaid) repeated).end();

            // Older versions may leave a lit portal without an active encounter.
            PortalShape.findEmptyPortalShape(level, pos, axis).orElseThrow().createPortalBlocks(level);
            check(ignite(player, tool, pos, Direction.UP).consumesAction() && tool.getDamageValue() == 3,
                    "Legacy already-lit portal could not be reactivated");
            var legacy = (NetherRaid) MANAGER.getAftermathMap().values().stream().filter(a -> !oldBattles.contains(a.getUUID()) && !a.isEnd()).findFirst().orElseThrow();
            CompoundTag legacySave = (CompoundTag) NetherRaid.CODEC.encodeStart(NbtOps.INSTANCE, legacy).result().orElseThrow();
            legacySave.remove("portal_blocks");
            legacySave.put("spawnPos", CodecUtils.setOf(BlockPos.CODEC).encodeStart(NbtOps.INSTANCE, plane).result().orElseThrow());
            NetherRaid legacyLoaded = NetherRaid.CODEC.parse(NbtOps.INSTANCE, legacySave).result().orElseThrow();
            legacyLoaded.setLevel(level);
            check(legacyLoaded.getPortalBlocks().equals(plane), "Legacy save lost its portal positions");
            legacyLoaded.end();
            check(plane.stream().noneMatch(p -> level.getBlockState(p).is(Blocks.NETHER_PORTAL)), "Legacy saved portal did not close");
        } finally {
            MANAGER.getAftermathMap().values().stream().filter(a -> !oldBattles.contains(a.getUUID())).toList().forEach(a -> {
                ((NetherRaid) a).end(); a.getTrackers().forEach(ITracker::unregister); MANAGER.getAftermathMap().remove(a.getUUID());
            });
            NeoForge.EVENT_BUS.unregister(sounds);
            RaidMusic.stop(level, pos);
            modules.removeAll(key); modules.putAll(key, oldModules);
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void ordinaryPortalsOutsideDungeonStillWork(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(6, 3, 6));
        Set<BlockPos> plane = frame(level, pos, Direction.Axis.X);
        check(!RaidPortal.isArena(level, pos), "Unexpected structure at ordinary portal fixture");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ordinary_portal"));
        Set<UUID> before = new HashSet<>(MANAGER.getAftermathMap().keySet());
        for (Item item : List.of(Items.FLINT_AND_STEEL, ModItems.DIAMOND_FLINT_AND_STEEL.get())) {
            check(ignite(player, new ItemStack(item), pos.below(), Direction.UP).consumesAction(), "Ordinary portal failed to ignite");
            check(plane.stream().allMatch(p -> level.getBlockState(p).is(Blocks.NETHER_PORTAL)), "Outside portal was blocked");
            level.removeBlock(pos, false);
        }
        check(MANAGER.getAftermathMap().keySet().equals(before), "Outside portal incorrectly started a challenge");
        h.succeed();
    }
}
