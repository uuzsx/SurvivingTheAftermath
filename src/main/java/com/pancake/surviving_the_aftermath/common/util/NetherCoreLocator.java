package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.*;

/** Off-thread worldgen prediction uses immutable height bounds, never a level/chunk API. */
@EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class NetherCoreLocator {
    private NetherCoreLocator() {}
    private static final int SEARCH_RINGS = 12;
    private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Aftermath city locator"); thread.setDaemon(true); return thread;
    });
    private static final Map<ServerLevel, State> STATES = new IdentityHashMap<>();
    private static final class State {
        final Map<UUID, BlockPos> destinations = new HashMap<>();
        final Map<UUID, Long> nextUse = new HashMap<>();
        final ArrayDeque<Search> searches = new ArrayDeque<>();
        Search active;
        java.util.concurrent.Future<Optional<BlockPos>> prediction;
    }
    private static final class Search {
        final ServerPlayer player;
        final InteractionHand hand;
        final ItemStack held;
        final BlockPos origin;
        final ArrayDeque<ChunkPos> candidates;
        final long expires;
        Search(ServerPlayer player, InteractionHand hand, ArrayDeque<ChunkPos> candidates) {
            this.player = player; this.hand = hand; this.held = player.getItemInHand(hand);
            this.origin = player.blockPosition(); this.candidates = candidates;
            this.expires = player.level().getGameTime() + 1200;
        }
    }

    public static Optional<BlockPos> nearbyDealer(ServerLevel level, BlockPos origin) {
        Vec3 point = Vec3.atCenterOf(origin);
        return level.getEntitiesOfClass(Villager.class, new AABB(origin).inflate(128),
                        v -> v.isAlive() && v.getVillagerData().getProfession() == ModVillagers.RELIC_DEALER.get())
                .stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(point))).map(Villager::blockPosition);
    }

    public static void use(ServerPlayer player, InteractionHand hand) {
        ServerLevel level = (ServerLevel) player.level();
        if (!player.getItemInHand(hand).is(ModItems.NETHER_CORE.get())) return;
        if (level.dimension() != Level.OVERWORLD) { message(player, "overworld"); return; }
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        long now = level.getGameTime();
        if (now < state.nextUse.getOrDefault(player.getUUID(), 0L)) return;
        state.nextUse.put(player.getUUID(), now + 20);
        if (state.active != null && state.active.player.getUUID().equals(player.getUUID())) return;
        if (state.searches.stream().anyMatch(s -> s.player.getUUID().equals(player.getUUID()))) return;
        var dealer = nearbyDealer(level, player.blockPosition());
        if (dealer.isPresent()) { launch(player, hand, dealer.get(), true); return; }
        var target = state.destinations.get(player.getUUID());
        if (target != null && horizontalDistance(target, player.blockPosition()) > 96
                && horizontalDistance(target, player.blockPosition()) < 12000) {
            launch(player, hand, target, false);
            return;
        }
        // An old city may lack a dealer, or its dealer may have died. Search onward instead
        // of pointing forever at an empty destination; never resurrect killed merchants.
        state.destinations.remove(player.getUUID());
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) { message(player, "not_found"); return; }
        beginSearch(player, hand);
    }

    /** Queues a held core for prediction; inventory is only changed after a successful launch. */
    public static void beginSearch(ServerPlayer player, InteractionHand hand) {
        ServerLevel level = (ServerLevel) player.level();
        if (level.dimension() != Level.OVERWORLD || !player.getItemInHand(hand).is(ModItems.NETHER_CORE.get())) return;
        State state = STATES.computeIfAbsent(level, ignored -> new State());
        if ((state.active != null && state.active.player.getUUID().equals(player.getUUID()))
                || state.searches.stream().anyMatch(s -> s.player.getUUID().equals(player.getUUID()))) return;
        var candidates = candidates(level, player.blockPosition());
        if (candidates.isEmpty()) { message(player, "not_found"); return; }
        state.searches.add(new Search(player, hand, candidates));
        message(player, "searching");
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        return Math.hypot((double)a.getX() - b.getX(), (double)a.getZ() - b.getZ());
    }

    public static ArrayDeque<ChunkPos> candidates(ServerLevel level, BlockPos origin) {
        var result = new ArrayDeque<ChunkPos>();
        var set = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(ModStructureSets.CITY_SET).value();
        if (!(set.placement() instanceof RandomSpreadStructurePlacement spread)) return result;
        var state = level.getChunkSource().getGeneratorState();
        int regionX = Math.floorDiv(origin.getX() >> 4, spread.spacing());
        int regionZ = Math.floorDiv(origin.getZ() >> 4, spread.spacing());
        for (int ring = 0; ring <= SEARCH_RINGS; ring++) {
            var entries = new ArrayList<ChunkPos>();
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                var chunk = spread.getPotentialStructureChunk(level.getSeed(), (regionX + dx) * spread.spacing(), (regionZ + dz) * spread.spacing());
                if (spread.isStructureChunk(state, chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4)) entries.add(chunk);
            }
            entries.sort(Comparator.comparingDouble(c -> horizontalDistance(c.getMiddleBlockPosition(origin.getY()), origin)));
            result.addAll(entries);
        }
        return result;
    }

    /** Uses the same registered generator, placement and valid-generation predicate as worldgen. */
    public static Optional<BlockPos> preview(ServerLevel level, ChunkPos candidate) {
        return previewTask(level, candidate).get();
    }

    private static java.util.function.Supplier<Optional<BlockPos>> previewTask(ServerLevel level, ChunkPos candidate) {
        var city = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        // Already generated starts take precedence over predictions after a datapack/mod update.
        var loaded = level.getChunkSource().getChunkNow(candidate.getMinBlockX() >> 4, candidate.getMinBlockZ() >> 4);
        if (loaded != null) {
            var start = loaded.getStartForStructure(city);
            var result = start != null && start.isValid() ? Optional.of(start.getBoundingBox().getCenter()) : Optional.<BlockPos>empty();
            return () -> result;
        }
        var generator = level.getChunkSource().getGenerator();
        var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), candidate, net.minecraft.world.level.LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()), city.biomes()::contains);
        return () -> city.findValidGenerationPoint(context).map(stub -> stub.getPiecesBuilder().build().calculateBoundingBox().getCenter());
    }

    public static boolean launch(ServerPlayer player, InteractionHand hand, BlockPos destination, boolean dealer) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        var stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.NETHER_CORE.get()) || stack.isEmpty()) return false;
        var eye = new EyeOfEnder(level, player.getX(), player.getEyeY() - 0.15, player.getZ());
        eye.setItem(stack.copyWithCount(1));
        ((NetherCoreFlight) eye).aftermath$launch(Vec3.atCenterOf(destination).add(0, 1, 0), !player.getAbilities().instabuild);
        if (!level.addFreshEntity(eye)) return false;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.awardStat(Stats.ITEM_USED.get(ModItems.NETHER_CORE.get()));
        message(player, dealer ? "dealer" : "city");
        return true;
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("message.surviving_the_aftermath.nether_core." + key), true);
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }
    public static void tick(ServerLevel level) {
        State state = STATES.get(level);
        if (state == null) return;
        if (state.active == null) {
            if (state.searches.isEmpty()) return;
            state.active = state.searches.remove();
        }
        Search search = state.active;
        if (!search.player.isAlive() || search.player.hasDisconnected() || search.player.level() != level
                || search.player.getItemInHand(search.hand) != search.held || !search.held.is(ModItems.NETHER_CORE.get())
                || search.held.isEmpty() || horizontalDistance(search.origin, search.player.blockPosition()) > 32) {
            finish(state); return;
        }
        if (level.getGameTime() >= search.expires) { message(search.player, "not_found"); finish(state); return; }
        var dealer = nearbyDealer(level, search.player.blockPosition());
        if (dealer.isPresent()) { launch(search.player, search.hand, dealer.get(), true); finish(state); return; }
        if (state.prediction == null) {
            if (search.candidates.isEmpty()) { message(search.player, "not_found"); finish(state); return; }
            // Registry, template manager and noise generator support parallel worldgen. Capture
            // them on the server thread; the worker receives only a context with fixed height bounds.
            var task = previewTask(level, search.candidates.remove());
            state.prediction = WORKER.submit(task::get);
            return;
        }
        if (!state.prediction.isDone()) return;
        Optional<BlockPos> target;
        try { target = state.prediction.get(); }
        catch (Exception failure) {
            SurvivingTheAftermath.LOGGER.warn("Unable to predict a city for Nether Core", failure);
            message(search.player, "not_found"); finish(state); return;
        }
        if (target.isPresent() && horizontalDistance(target.get(), search.origin) > 96) {
            state.destinations.put(search.player.getUUID(), target.get());
            launch(search.player, search.hand, target.get(), false);
        } else state.searches.add(search);
        finish(state);
    }
    private static void finish(State state) {
        if (state.prediction != null) state.prediction.cancel(true);
        state.active = null; state.prediction = null;
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var state = STATES.remove(level); if (state != null) finish(state);
        }
    }
}
