package com.pancake.surviving_the_aftermath;

import com.mojang.authlib.GameProfile;
import com.pancake.surviving_the_aftermath.api.*;
import com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader;
import com.pancake.surviving_the_aftermath.common.init.ModItems;
import com.pancake.surviving_the_aftermath.common.raid.*;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.*;

@net.neoforged.neoforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class PortalEntranceGameTests {
    private static void check(boolean ok,String message) {
        if(!ok) throw new GameTestAssertException(message);
    }
    private static Mob mob(GameTestHelper h,EntityType<? extends Mob> type) {
        Mob mob=type.create(h.getLevel(),EntitySpawnReason.EVENT);
        if(mob instanceof AbstractPiglin p)p.setImmuneToZombification(true);
        if(mob instanceof Hoglin hog){hog.setBaby(false);hog.setImmuneToZombification(true);}
        return mob;
    }
    private static BaseRaidModule profile() {
        return AftermathModuleLoader.AFTERMATH_MODULE_MAP.get(SurvivingTheAftermath.asResource("raid")).stream()
                .filter(m->m.getModuleName().equals("hard")).map(m->(BaseRaidModule)m).findFirst().orElseThrow();
    }
    private static Set<BlockPos> doorway(GameTestHelper h,BlockPos base,Direction.Axis axis) {
        var level=h.getLevel();
        for(var p:BlockPos.betweenClosed(base.offset(-5,-1,-5),base.offset(5,5,5)))
            level.setBlock(p,p.getY()==base.getY()-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        Set<BlockPos> portal=new HashSet<>();
        for(int w=-1;w<=2;w++)for(int y=-1;y<=3;y++) {
            var p=base.offset(axis==Direction.Axis.X?w:0,y,axis==Direction.Axis.Z?w:0);
            boolean edge=w==-1||w==2||y==-1||y==3;
            level.setBlock(p,edge?Blocks.OBSIDIAN.defaultBlockState():Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS,axis),2);
            if(!edge)portal.add(p);
        }
        for(var p:portal)level.setBlock(p,Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS,axis),18);
        return portal;
    }
    private static NetherRaid reload(GameTestHelper h,NetherRaid raid) {
        var data=NetherRaid.CODEC.encodeStart(NbtOps.INSTANCE,raid).result().orElseThrow();
        var loaded=NetherRaid.CODEC.parse(NbtOps.INSTANCE,data).result().orElseThrow();
        loaded.restore(h.getLevel(),raid.getUUID());
        AftermathManager.getInstance().getAftermathMap().put(loaded.getUUID(),loaded);
        check(loaded.getEntranceState().equals(raid.getEntranceState()),"Pending equipment, order, or delay changed on reload");
        return loaded;
    }

    @GameTest(template="stability_empty", timeoutTicks=400)
    public static void entranceAuthenticTemplateAndWalk(GameTestHelper h) {
        var level=h.getLevel();var template=level.getStructureManager().getOrCreate(SurvivingTheAftermath.asResource("nether_invasion_portal"));
        int cases=0;
        for(Rotation rotation:Rotation.values()) {
            BlockPos origin=h.absolutePos(new BlockPos(1024+rotation.ordinal()*64,5,2048));
            var settings=new StructurePlaceSettings().setRotation(rotation);
            template.placeInWorld(level,origin,origin,settings,level.getRandom(),2);
            Set<BlockPos> portal=new HashSet<>();
            for(int y=4;y<=13;y++)for(int z=12;z<=17;z++) {
                var p=origin.offset(StructureTemplate.transform(new BlockPos(17,y,z),Mirror.NONE,rotation,BlockPos.ZERO));
                portal.add(p);
                var axis=rotation.rotate(Direction.WEST).getAxis()==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;
                level.setBlock(p,Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS,axis),2);
            }
            Direction exit=rotation.rotate(Direction.WEST);
            var target=Vec3.atCenterOf(origin.offset(StructureTemplate.transform(new BlockPos(10,4,14),Mirror.NONE,rotation,BlockPos.ZERO)));
            for(var type:List.of(EntityType.PIGLIN,EntityType.PIGLIN_BRUTE,EntityType.HOGLIN)) {
                var mob=mob(h,type);
                var direction=PortalEntrance.place(level,mob,portal,target).orElseThrow(()->new IllegalStateException("No doorway position for "+type+"/"+rotation));
                check(direction==exit,"Mob entered through the obstructed back of the real building");
                check(mob.getY()==origin.getY()+4 && mob.getDeltaMovement().y==0 && mob.fallDistance==0,"Mob appeared above doorway floor");
                check(SafeSpawn.isSafe(level,mob),"Entrance body clips doorway");
                float health=mob.getHealth();Vec3 start=mob.position();
                PortalEntrance.begin(mob,direction);
                for(int tick=0;tick<PortalEntrance.WALK_TICKS+1;tick++){PortalEntrance.tick(level,mob);mob.tick();}
                double moved=mob.position().subtract(start).dot(new Vec3(exit.getStepX(),0,exit.getStepZ()));
                check(moved>1.5,"Entrance did not walk outward: "+type+"/"+rotation+" distance="+moved);
                check(mob.getHealth()==health && !mob.isNoAi() && !PortalEntrance.isWalking(mob),"Entrance caused damage or never restored AI");
                check(SafeSpawn.isSafe(level,mob),"Walking through the real stairs clipped blocks");
                mob.discard();cases++;
            }
        }
        System.out.println("PORTAL WALK CHECK: "+cases+" real-template rotation/species cases, grounded doorway, outward physics, no fall damage, AI resumed");h.succeed();
    }

    @GameTest(template="stability_empty", timeoutTicks=400)
    public static void entranceQueueCountsEquipmentAndReload(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(8,3,8));var portal=doorway(h,base,Direction.Axis.X);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"entrance_queue"));
        player.moveTo(Vec3.atCenterOf(base.south(4)));level.addNewPlayer(player);
        var module=profile();var manager=AftermathManager.getInstance();
        var raid=new NetherRaid(AftermathState.ONGOING,module,Set.of(player.getUUID()),0F,base,0,module.getRewardTime(),portal,Set.of(),11,0,List.of(),portal,RaidDifficulty.HARD);
        raid.setLevel(level);manager.getAftermathMap().put(raid.getUUID(),raid);
        List<Mob> all=new ArrayList<>();Set<UUID> seen=new HashSet<>();int lastArrival=-8;Set<Integer> reloaded=new HashSet<>();
        try {
            for(int tick=0;tick<230;tick++) {
                raid.tick();
                check(raid.getCurrentWave()==12 && raid.state==AftermathState.ONGOING,"Queue allowed early wave completion/victory");
                check(raid.getRewardTime()==module.getRewardTime(),"Reward paid while enemies still awaited entrance");
                if(tick==0)check(raid.getEnemies().size()==1 && raid.getPendingSpawnCount()==25 && raid.getProgressPercent()==1F,"Wave burst spawned or bossbar omitted pending mobs: enemies="+raid.getEnemies().size()+" pending="+raid.getPendingSpawnCount()+" progress="+raid.getProgressPercent());
                if(raid.getPendingSpawnCount()>0) {
                    int remaining=raid.getPendingSpawnCount();
                    if(Set.of(25,13,1).contains(remaining) && reloaded.add(remaining))raid=reload(h,raid);
                }
                for(UUID id:List.copyOf(raid.getEnemies())) {
                    var mob=(Mob)level.getEntity(id);check(mob!=null && seen.add(id),"Queue duplicated an entity identity");
                    check(tick-lastArrival>=8,"Entrants released without spacing");lastArrival=tick;
                    check(mob.getY()==base.getY() && mob.getDeltaMovement().y==0 && mob.fallDistance==0,"Queued mob was launched/fell");
                    check(PortalEntrance.isWalking(mob) && mob.isNoAi(),"Entrance stage not active");
                    RaidDifficultyGameTests.verify(mob,RaidDifficulty.HARD,13);
                    check(RaidMobLoot.isDungeonMob(mob) && !mob.canPickUpLoot(),"Queued mob lost loot policy");
                    all.add(mob);mob.discard();raid.getEnemies().remove(id);
                }
                if(raid.getPendingSpawnCount()==0)break;
            }
            check(all.size()==26 && raid.getPendingSpawnCount()==0 && reloaded.size()==3,"Queue lost mobs or failed to finish");
            RaidRosterGameTests.checkWave(all,13);
            raid.tick();check(raid.state==AftermathState.CELEBRATING,"Last entrant's death failed to trigger victory");
            check(level.getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(4)).stream().filter(e->e.getItem().is(ModItems.NETHER_CORE.get())).mapToInt(e->e.getItem().getCount()).sum()>=20,"Queue completion lost rc.22 core guarantee");
            System.out.println("PORTAL QUEUE CHECK: 26 hard-wave entrants spaced 8 ticks, 3 queue reloads, exact armor quotas/combat buffs, no premature rewards");
        } finally {
            all.forEach(Entity::discard);raid.end();manager.getAftermathMap().remove(raid.getUUID());RaidMusic.stop(level,base);
            level.getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(4)).forEach(Entity::discard);
            level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    private static Mob restoredMob(GameTestHelper h,Mob mob) {
        var data=new CompoundTag();mob.save(data);
        return (Mob)EntityType.loadEntityRecursive(data,h.getLevel(),EntitySpawnReason.EVENT,e->e);
    }

    @GameTest(template="stability_empty", timeoutTicks=400)
    public static void entranceObstructionAndAiRecovery(GameTestHelper h) {
        var level=h.getLevel();var base=h.absolutePos(new BlockPos(8,3,8));var portal=doorway(h,base,Direction.Axis.Z);
        var mob=mob(h,EntityType.PIGLIN);mob.moveTo(Vec3.atCenterOf(base));
        PortalEntrance.begin(mob,Direction.EAST);mob=restoredMob(h,mob);
        check(mob.isNoAi() && PortalEntrance.isWalking(mob),"Walking state lost on entity reload");
        mob.hurtTime=5;PortalEntrance.tick(level,mob);check(!mob.isNoAi() && !PortalEntrance.isWalking(mob),"Attack did not restore combat AI immediately");
        mob.setNoAi(true);PortalEntrance.begin(mob,Direction.EAST);PortalEntrance.finish(mob);check(mob.isNoAi(),"Entrance erased pre-existing no-AI state");
        mob.setNoAi(false);PortalEntrance.begin(mob,Direction.EAST);
        var dummy=new NetherRaid(level,base);dummy.insertTag(mob);BattleEntityState.clear(mob,dummy.getUUID());
        check(!mob.isNoAi() && !PortalEntrance.isWalking(mob),"Encounter cleanup left a frozen mob");mob.discard();
        for(int x:new int[]{-1,1})for(int y=0;y<4;y++)for(int z=-1;z<=2;z++)level.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        var blocked=mob(h,EntityType.HOGLIN);check(PortalEntrance.place(level,blocked,portal,Vec3.atCenterOf(base.east(4))).isEmpty(),"Blocked door teleported entrant outside");blocked.discard();
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"blocked_entrance"));player.moveTo(Vec3.atCenterOf(base.east(4)));level.addNewPlayer(player);
        var module=profile();var raid=new NetherRaid(AftermathState.ONGOING,module,Set.of(player.getUUID()),0F,base,0,module.getRewardTime(),portal,Set.of(),-1,0,List.of(),portal,RaidDifficulty.HARD);
        raid.setLevel(level);var manager=AftermathManager.getInstance();manager.getAftermathMap().put(raid.getUUID(),raid);
        try {
            for(int tick=0;tick<205 && !raid.isEnd();tick++)raid.tick();
            check(raid.isEnd() && raid.getEnemies().isEmpty() && raid.getPendingSpawnCount()==0,"Blocked portal hangs or spawns outside door");
            check(level.getEntitiesOfClass(ItemEntity.class,new AABB(base).inflate(4)).isEmpty(),"Blocked portal granted rewards");
            System.out.println("PORTAL RECOVERY CHECK: blocked exit terminates without rewards; entity reload, damage interruption and old AI state restored");
        } finally {raid.end();manager.getAftermathMap().remove(raid.getUUID());level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        h.succeed();
    }
}
