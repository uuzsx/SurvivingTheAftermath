package com.pancake.surviving_the_aftermath;

import com.mojang.authlib.GameProfile;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.item.NetherCoreItem;
import com.pancake.surviving_the_aftermath.common.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.*;

public final class NetherCoreGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(Component.literal(message), 0);
    }
    private static FakePlayer player(GameTestHelper h) {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "core_test")) {
            @Override public boolean hasDisconnected() { return false; }
        };
        player.getAbilities().instabuild = false;
        player.snapTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 6, 3))));
        return player;
    }
    private static AABB area(GameTestHelper h) { return new AABB(h.absolutePos(BlockPos.ZERO)).inflate(80); }
    private static List<EyeOfEnder> eyes(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(EyeOfEnder.class, area(h), e -> e.getItem().is(ModItems.NETHER_CORE.get()));
    }
    private static List<ItemEntity> drops(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, area(h), e -> e.getItem().is(ModItems.NETHER_CORE.get()));
    }
    private static void cleanup(GameTestHelper h) {
        eyes(h).forEach(net.minecraft.world.entity.Entity::discard);
        drops(h).forEach(net.minecraft.world.entity.Entity::discard);
    }
    private static ItemStack namedCore() {
        var stack = new ItemStack(ModItems.NETHER_CORE.get());
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("precious_core"));
        return stack;
    }

    public static void coreAlwaysReturnsAndCanBeThrownAgain(GameTestHelper h) {
        cleanup(h);
        check(ModItems.NETHER_CORE.get() instanceof NetherCoreItem, "Registered currency is still a plain item");
        var player = player(h);var stack = namedCore();
        player.setItemInHand(InteractionHand.OFF_HAND, stack);
        try {
            for (int repeat = 0; repeat < 128; repeat++) {
                check(NetherCoreLocator.launch(player, InteractionHand.OFF_HAND, player.blockPosition().east(40), false), "Launch failed");
                check(player.getItemInHand(InteractionHand.OFF_HAND).isEmpty(), "Survival launch did not take exactly one core");
                var launched = eyes(h);check(launched.size() == 1, "Duplicate/missing projectile");
                var eye = launched.get(0);
                for (int tick = 0; tick < 90; tick++) eye.tick();
                var returned = drops(h);
                check(eyes(h).isEmpty() && returned.size() == 1 && returned.get(0).getItem().getCount() == 1, "A core broke or duplicated");
                var drop = returned.get(0);
                check(drop.getItem().getHoverName().getString().equals("precious_core"), "Custom item data was lost");
                check(drop.isInvulnerable(), "Returned core can be destroyed by damage");
                var next = drop.getItem().copy();drop.discard();player.setItemInHand(InteractionHand.OFF_HAND, next);
            }
            System.out.println("CORE REUSE CHECK: 128 throws, 128 intact returns, offhand and custom name retained, no duplicates");
            h.succeed();
        } finally { cleanup(h); }
    }

    public static void coreFlightSurvivesSaveReload(GameTestHelper h) {
        cleanup(h);var player = player(h);player.setItemInHand(InteractionHand.MAIN_HAND, namedCore());
        NetherCoreLocator.launch(player, InteractionHand.MAIN_HAND, player.blockPosition().east(40), false);
        var original = eyes(h).get(0);
        for (int i = 0; i < 25; i++) original.tick();
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        original.saveWithoutId(output);
        var saved = output.buildResult();original.discard();
        var loaded = new EyeOfEnder(h.getLevel(), player.getX(), player.getY(), player.getZ());
        loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved));
        h.getLevel().addFreshEntity(loaded);
        try {
            for (int i = 0; i < 90; i++) loaded.tick();
            check(drops(h).size() == 1 && eyes(h).isEmpty(), "Reloaded flight failed to return exactly one core");
            check(drops(h).get(0).getItem().getHoverName().getString().equals("precious_core"), "Reload lost item components");
            System.out.println("CORE SAVE CHECK: in-flight entity save/load retains item and guaranteed single return");
            h.succeed();
        } finally { cleanup(h); }
    }

    public static void coreUseTracksLivingDealerAndTicksNaturally(GameTestHelper h) {
        cleanup(h);var player = player(h);
        var dealer = EntityTypes.VILLAGER.create(h.getLevel(), EntitySpawnReason.EVENT);
        dealer.snapTo(player.getX()+10, player.getY(), player.getZ());dealer.setNoAi(true);
        dealer.setVillagerData(dealer.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(ModVillagers.RELIC_DEALER.get())));
        dealer.setVillagerXp(1);h.getLevel().addFreshEntity(dealer);
        player.setItemInHand(InteractionHand.MAIN_HAND, namedCore());
        check(NetherCoreLocator.nearbyDealer(h.getLevel(), player.blockPosition()).orElseThrow().equals(dealer.blockPosition()), "Locator did not select actual dealer");
        ModItems.NETHER_CORE.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        check(eyes(h).size()==1 && player.getMainHandItem().isEmpty(), "Real right-click failed to launch");
        var eye=eyes(h).get(0);double startX=eye.getX();
        h.runAfterDelay(12,()->check(eye.getX()>startX, "Core did not fly toward dealer on server ticks"));
        h.runAfterDelay(85,()->{
            try {
                check(drops(h).size()==1 && eyes(h).isEmpty(), "Naturally ticking flight broke or duplicated core");
                dealer.discard();
                check(NetherCoreLocator.nearbyDealer(h.getLevel(), player.blockPosition()).isEmpty(), "Dead/removed trader remains a destination");
                System.out.println("CORE USE CHECK: registered item right-click, actual dealer direction, 85 server ticks and removed dealer exclusion");
                h.succeed();
            } finally { dealer.discard();cleanup(h); }
        });
    }

    public static void coreCreativeAndMissingTargetDoNotDuplicate(GameTestHelper h) {
        cleanup(h);var player=player(h);
        var stack=namedCore();stack.setCount(3);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        // The test world disables new structure generation, so no nearby dealer means no launch.
        ModItems.NETHER_CORE.get().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        check(stack.getCount()==3 && eyes(h).isEmpty(), "Failed search consumed a core");
        player.getAbilities().instabuild=true;
        check(NetherCoreLocator.launch(player,InteractionHand.MAIN_HAND,player.blockPosition().east(40),false), "Creative launch failed");
        check(stack.getCount()==3,"Creative launch consumed inventory");
        for(var eye:eyes(h))for(int i=0;i<90;i++)eye.tick();
        check(eyes(h).isEmpty() && drops(h).isEmpty(),"Creative throw duplicated currency");
        System.out.println("CORE TRANSACTION CHECK: no-target conserves inventory; creative keeps stack without creating drops");
        h.succeed();
    }

    public static void coreStopsBeforeWallsAndNeverDespawns(GameTestHelper h) {
        cleanup(h);var player=player(h);player.setItemInHand(InteractionHand.MAIN_HAND,namedCore());
        var wall=player.blockPosition().east(2);
        for(int y=-2;y<=10;y++)for(int z=-2;z<=2;z++)h.getLevel().setBlockAndUpdate(wall.offset(0,y,z),Blocks.STONE.defaultBlockState());
        try {
            NetherCoreLocator.launch(player,InteractionHand.MAIN_HAND,player.blockPosition().east(40),false);
            var eye=eyes(h).get(0);for(int i=0;i<90;i++)eye.tick();
            check(drops(h).size()==1,"Collision lost the core");var drop=drops(h).get(0);
            check(drop.getX()<wall.getX(),"Core flew into/through solid wall");
            drop.setNoGravity(true);drop.setDeltaMovement(Vec3.ZERO);
            for(int i=0;i<6100;i++)drop.tick();
            check(drop.isAlive() && drop.getItem().getCount()==1,"Returned core despawned after five minutes");
            System.out.println("CORE COLLISION CHECK: wall stops flight, intact drop remains after 6100 item ticks");
            h.succeed();
        } finally {
            cleanup(h);
            for(int y=-2;y<=10;y++)for(int z=-2;z<=2;z++)h.getLevel().setBlockAndUpdate(wall.offset(0,y,z),Blocks.AIR.defaultBlockState());
        }
    }
    public static void coreBackgroundSearchAndCancellation(GameTestHelper h) throws Exception {
        cleanup(h);var player=player(h);
        var held=namedCore();player.setItemInHand(InteractionHand.MAIN_HAND,held);
        // Exercise the production queue directly because GameTest disables structure generation.
        NetherCoreLocator.beginSearch(player,InteractionHand.MAIN_HAND);
        NetherCoreLocator.beginSearch(player,InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        NetherCoreLocator.tick(h.getLevel());
        check(held.getCount()==1 && eyes(h).isEmpty(), "Changing hands while searching consumed/duplicated core");
        player.setItemInHand(InteractionHand.MAIN_HAND,held);
        // Some GameTest worlds use the_void, where natural city generation is correctly
        // disallowed. Install a saved city start as the deterministic locator fixture.
        var candidate=NetherCoreLocator.candidates(h.getLevel(),player.blockPosition()).stream()
                .filter(c -> c.getMiddleBlockPosition(64).distSqr(player.blockPosition())>256*256).findFirst().orElseThrow();
        int cx=candidate.getMinBlockX()>>4,cz=candidate.getMinBlockZ()>>4;
        h.getLevel().setChunkForced(cx,cz,true);
        var chunk=h.getLevel().getChunk(cx,cz);
        var originalStarts=new HashMap<>(chunk.getAllStarts());
        var fixtureStarts=new HashMap<>(originalStarts);
        var city=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getOrThrow(ModStructures.CITY).value();
        var piece=new com.pancake.surviving_the_aftermath.common.structure.CityStructure.Piece(h.getLevel().getStructureManager(),
                candidate.getMiddleBlockPosition(64),net.minecraft.world.level.block.Rotation.NONE);
        fixtureStarts.put(city,new net.minecraft.world.level.levelgen.structure.StructureStart(city,candidate,0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece))));
        chunk.setAllStarts(fixtureStarts);
        NetherCoreLocator.beginSearch(player,InteractionHand.MAIN_HAND);
        // Forge GameTest advances ticks faster than real time. Drive the server-side queue
        // explicitly while giving the worldgen worker a bounded wall-clock interval.
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(50);
        while(eyes(h).isEmpty() && System.nanoTime()<deadline) {
            NetherCoreLocator.tick(h.getLevel());
            Thread.sleep(2);
        }
        try {
            check(eyes(h).size()==1 && held.isEmpty(), "Queued search did not transfer exactly one core");
            var eye=eyes(h).get(0);
            for(int i=0;i<90;i++)eye.tick();
            check(drops(h).size()==1,"Background search launch did not return core");
            System.out.println("CORE SEARCH CHECK: background city lookup launches once; duplicate requests and changing hands conserve cores");
            h.succeed();
        } finally { player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);NetherCoreLocator.tick(h.getLevel());cleanup(h);chunk.setAllStarts(originalStarts);h.getLevel().setChunkForced(cx,cz,false); }
    }
}
