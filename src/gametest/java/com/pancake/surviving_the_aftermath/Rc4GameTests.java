package com.pancake.surviving_the_aftermath;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.api.AftermathState;
import com.pancake.surviving_the_aftermath.common.module.amount.IntegerAmountModule;
import com.pancake.surviving_the_aftermath.common.module.entity_info.*;
import com.pancake.surviving_the_aftermath.common.module.predicate.EquipmentPredicate;
import com.pancake.surviving_the_aftermath.common.module.weighted.ItemWeightedModule;
import com.pancake.surviving_the_aftermath.common.network.AftermathNetwork;
import com.pancake.surviving_the_aftermath.common.raid.BaseRaid;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import java.io.InputStreamReader;
import java.util.*;

@net.neoforged.neoforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public class Rc4GameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
    private static FakePlayer player(ServerLevel level, BlockPos pos, String name) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(Vec3.atCenterOf(pos));
        player.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(player);
        return player;
    }
    private static BaseRaidModule defaultModule(GameTestHelper h) throws Exception {
        var location = SurvivingTheAftermath.asResource("aftermath/common.json");
        try (var stream = h.getLevel().getServer().getResourceManager().getResourceOrThrow(location).open();
             var reader = new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            return BaseRaidModule.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        }
    }

    @GameTest(template = "stability_empty")
    public static void defaultWavePiglinsHaveWeapons(GameTestHelper h) throws Exception {
        var module = defaultModule(h);
        check(module.getWaves().size() == 9, "Default wave configuration changed");
        int piglins = 0;
        for (int repeat = 0; repeat < 12; repeat++) for (var wave : module.getWaves()) for (var group : wave) {
            for (var created : group.spawnEntity(h.getLevel(), h.absolutePos(new BlockPos(5, 2, 5)))) {
                Entity entity = created.orElseThrow();
                if (entity instanceof AbstractPiglin piglin) {
                    piglins++;
                    check(!piglin.getMainHandItem().isEmpty(), "An actual configured wave produced an unarmed piglin");
                    if (piglin instanceof Piglin) check(!piglin.isBaby(), "Dungeon piglin was a baby");
                }
                entity.discard();
            }
        }
        check(piglins > 200, "Not enough configured piglins exercised");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void configuredEquipmentOverridesDefaults(GameTestHelper h) {
        var weapon = new EquipmentPredicate.Builder().add(Items.NETHERITE_SWORD, 1).canDrop(false).build();
        var armor = new EquipmentPredicate.Builder().add(Items.NETHERITE_CHESTPLATE, 1).canDrop(false).build();
        var module = new EntityInfoWithPredicateModule(EntityType.PIGLIN, new IntegerAmountModule(32), List.of(weapon, armor));
        for (var created : module.spawnEntity(h.getLevel(), h.absolutePos(new BlockPos(5, 2, 5)))) {
            var mob = (Mob) created.orElseThrow();
            check(mob.getMainHandItem().is(Items.NETHERITE_SWORD), "Default initialization overwrote configured weapon");
            check(mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE), "Configured armor was rejected by pickup AI");
            mob.discard();
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void dungeonMobsDropNoLootOrExperience(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(5, 2, 5));
        AABB area = new AABB(pos).inflate(4);
        var player = player(level, pos.east(2), "loot_test");
        var mob = EntityType.PIGLIN.create(level, EntitySpawnReason.EVENT);
        mob.moveTo(Vec3.atCenterOf(pos));
        UUID oldRaid = UUID.randomUUID();
        mob.getPersistentData().putString("raid", "enemies");
        mob.getPersistentData().putUUID("raid_uuid", oldRaid);
        RaidMobLoot.mark(mob);
        BattleEntityState.clear(mob, oldRaid); // Still no loot after the encounter has ended.
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
        try {
            level.addFreshEntity(mob);
            // Apply after the entity-load handler, so the death event must suppress a guaranteed drop.
            mob.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
            check(!mob.canPickUpLoot(), "Dungeon mob could pick up reward drops");
            mob.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
            check(!mob.isAlive(), "Dungeon loot fixture did not die");
            check(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Dungeon mob dropped loot/equipment");
            check(level.getEntitiesOfClass(ExperienceOrb.class, area).isEmpty(), "Dungeon mob dropped experience");
            var natural = EntityType.COW.create(level, EntitySpawnReason.EVENT);
            natural.moveTo(Vec3.atCenterOf(pos));
            natural.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
            natural.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
            level.addFreshEntity(natural);
            natural.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
            check(!level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Natural mob loot was suppressed");
            check(!level.getEntitiesOfClass(ExperienceOrb.class, area).isEmpty(), "Natural mob experience was suppressed");
            var playerItem = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.EMERALD));
            var playerDrops = new LivingDropsEvent(player, level.damageSources().generic(), new ArrayList<>(List.of(playerItem)), true);
            NeoForge.EVENT_BUS.post(playerDrops);
            check(!playerDrops.isCanceled() && playerDrops.getDrops().size() == 1, "Player death inventory was suppressed");
            natural.discard();
        } finally {
            mob.discard();
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard);
            level.getEntitiesOfClass(ExperienceOrb.class, area).forEach(Entity::discard);
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void splitMagmaCubesKeepDungeonLootPolicy(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(5, 2, 5));
        var module = new BaseRaidModule("split", new ItemWeightedModule.Builder().add(Items.APPLE, 1).build(),
                List.of(), List.of(List.of(new EntityInfoModule(EntityType.MAGMA_CUBE, new IntegerAmountModule(1)))), 0, 1);
        var raid = new BaseRaid(module, level, pos);
        raid.state = AftermathState.ONGOING;
        AftermathManager.getInstance().getAftermathMap().put(raid.getUUID(), raid);
        var parent = EntityType.MAGMA_CUBE.create(level, EntitySpawnReason.EVENT);
        parent.setSize(4, true);
        parent.moveTo(Vec3.atCenterOf(pos));
        raid.insertTag(parent);
        level.addFreshEntity(parent);
        try {
            parent.setHealth(0);
            parent.remove(Entity.RemovalReason.KILLED);
            var children = level.getEntitiesOfClass(MagmaCube.class, new AABB(pos).inflate(4));
            check(children.size() >= 2, "Magma cube did not split");
            for (var child : children) {
                check(RaidMobLoot.isDungeonMob(child), "Split child lost no-loot marker");
                check(raid.getEnemies().contains(child.getUUID()), "Split child was not tracked by the parent encounter");
                BattleEntityState.clear(child, raid.getUUID());
                check(RaidMobLoot.isDungeonMob(child), "Cleanup restored child loot");
            }
            var wild = EntityType.MAGMA_CUBE.create(level, EntitySpawnReason.EVENT);
            wild.moveTo(Vec3.atCenterOf(pos.east(3)));
            level.addFreshEntity(wild);
            check(!RaidMobLoot.isDungeonMob(wild) && !raid.getEnemies().contains(wild.getUUID()), "Nearby wild magma cube was enrolled as a dungeon mob");
        } finally {
            raid.end(); AftermathManager.getInstance().getAftermathMap().remove(raid.getUUID());
            level.getEntitiesOfClass(MagmaCube.class, new AABB(pos).inflate(5)).forEach(Entity::discard);
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void buildingMusicStopsWhenLastListenerLeaves(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(1, 2, 1));
        try (var arena = DungeonLifecycleGameTests.arena(h)) {
            BlockPos center = RaidMusic.center(level, origin);
            check(!center.equals(origin), "Music source used activation block instead of building center");
            var first = player(level, center.east(2), "listener_one");
            var second = player(level, center.west(2), "listener_two");
            UUID id = UUID.randomUUID();
            try {
                RaidMusic.start(level, origin, id);
                check(RaidMusic.playbackId(level, origin).orElseThrow().equals(id), "Victory song did not start");
                first.moveTo(Vec3.atCenterOf(center.east(RaidMusic.RADIUS + 2)));
                RaidMusic.tick(level);
                check(RaidMusic.playbackId(level, origin).isPresent(), "First departure stopped the other listener's music");
                second.moveTo(Vec3.atCenterOf(center.west(RaidMusic.RADIUS + 2)));
                RaidMusic.tick(level);
                check(RaidMusic.playbackId(level, origin).isEmpty(), "Last listener departure did not end playback");
                first.moveTo(Vec3.atCenterOf(center));
                RaidMusic.tick(level);
                check(RaidMusic.playbackId(level, origin).isEmpty(), "Returning to an empty building restarted music");
                UUID next = UUID.randomUUID();
                RaidMusic.start(level, origin, next);
                RaidMusic.start(level, origin, next);
                check(RaidMusic.playbackId(level, origin).orElseThrow().equals(next), "A later victory could not start its song");
                RaidMusic.stop(level, origin);
                check(RaidMusic.playbackId(level, origin).isEmpty(), "Explicit new-challenge stop failed");
            } finally {
                RaidMusic.stop(level, origin);
                level.removePlayerImmediately(first, Entity.RemovalReason.DISCARDED);
                level.removePlayerImmediately(second, Entity.RemovalReason.DISCARDED);
            }
        }
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void musicPacketAndSpatialAsset(GameTestHelper h) throws Exception {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var start = new AftermathNetwork.MusicPacket(UUID.randomUUID(), h.getLevel().dimension().location(), new BlockPos(-13, 75, 28), 1234);
            start.encode(buffer);
            check(AftermathNetwork.MusicPacket.decode(buffer).equals(start), "Music source/elapsed time changed in transit");
            var stop = new AftermathNetwork.MusicPacket(start.id(), null, BlockPos.ZERO, 0);
            stop.encode(buffer);
            check(AftermathNetwork.MusicPacket.decode(buffer).equals(stop), "Playback-specific stop changed in transit");
        } finally { buffer.release(); }
        try (var stream = Rc4GameTests.class.getResourceAsStream("/assets/surviving_the_aftermath/sounds/orchelias_vox.ogg")) {
            byte[] header = stream.readNBytes(128);
            int index = -1;
            for (int i = 0; i < header.length - 16; i++) {
                if (header[i] == 1 && header[i + 1] == 'v' && header[i + 2] == 'o' && header[i + 3] == 'r') { index = i; break; }
            }
            check(index >= 0 && header[index + 11] == 1, "Victory Ogg must be mono for positional attenuation");
        }
        try (var stream = Rc4GameTests.class.getResourceAsStream("/assets/surviving_the_aftermath/sounds.json");
             var reader = new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            var sound = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("orchelias_vox").getAsJsonArray("sounds").get(0).getAsJsonObject();
            check(sound.get("stream").getAsBoolean() && sound.get("attenuation_distance").getAsInt() == RaidMusic.RADIUS,
                    "Audio falloff radius differs from server listener radius");
        }
        h.succeed();
    }
}
