package com.pancake.surviving_the_aftermath;

import com.mojang.authlib.GameProfile;
import com.pancake.surviving_the_aftermath.api.AftermathState;
import com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader;
import com.pancake.surviving_the_aftermath.common.init.ModItems;
import com.pancake.surviving_the_aftermath.common.module.weighted.ItemWeightedModule;
import com.pancake.surviving_the_aftermath.common.raid.*;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.util.RaidMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;

@net.minecraftforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class EconomyGameTests {
    private static void check(boolean ok,String message) {
        if(!ok) throw new GameTestAssertException(message);
    }
    private static BaseRaidModule profile(RaidDifficulty mode) {
        return AftermathModuleLoader.AFTERMATH_MODULE_MAP.get(SurvivingTheAftermath.asResource("raid")).stream()
                .filter(m->m.getModuleName().equals(mode.moduleName())).map(m->(BaseRaidModule)m).findFirst().orElseThrow();
    }
    private static List<ItemEntity> drops(GameTestHelper h,BlockPos pos) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
    }
    private static int count(GameTestHelper h,BlockPos pos,Item item) {
        return drops(h,pos).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();
    }
    private static NetherRaid reload(GameTestHelper h,NetherRaid raid) {
        var data=NetherRaid.CODEC.encodeStart(NbtOps.INSTANCE,raid).result().orElseThrow();
        var loaded=NetherRaid.CODEC.parse(NbtOps.INSTANCE,data).result().orElseThrow();
        loaded.restore(h.getLevel(),raid.getUUID());
        return loaded;
    }
    private static NetherRaid encounter(GameTestHelper h,BlockPos pos,BaseRaidModule module,RaidDifficulty mode,UUID player,AftermathState state,int remaining) {
        var raid=new NetherRaid(state,module,Set.of(player),0F,pos,0,remaining,Set.of(pos),Set.of(),
                module.getWaves().size()-1,0,List.of(),Set.of(),mode);
        raid.setLevel(h.getLevel());return raid;
    }

    @GameTest(template="stability_empty", timeoutTicks=400)
    public static void completionCoreGuaranteeSurvivesReload(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,3,5));
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"economy_test"));
        player.moveTo(Vec3.atCenterOf(pos.south(6)));level.addNewPlayer(player);
        try {
            for(var mode:RaidDifficulty.values()) {
                var shipped=profile(mode);int expected=new int[]{4,10,20}[mode.ordinal()];
                check(shipped.getGuaranteedCores()==expected,"Wrong guaranteed core data for "+mode);
                // Eliminate random cores to exercise the worst possible reward roll.
                var deterministic=new BaseRaidModule(shipped.getModuleName(),new ItemWeightedModule.Builder().add(Items.APPLE,1).build(),
                        List.of(),shipped.getWaves(),0,shipped.getRewardTime(),shipped.getGuaranteedCores());
                var raid=encounter(h,pos,deterministic,mode,player.getUUID(),AftermathState.ONGOING,shipped.getRewardTime());
                raid=reload(h,raid); // Save immediately before clearing the final wave.
                try {
                    raid.tick();
                    check(raid.state==AftermathState.CELEBRATING && raid.rewardTime==shipped.getRewardTime()-1,"Final wave did not start payout immediately");
                    check(count(h,pos,ModItems.NETHER_CORE.get())==expected && count(h,pos,Items.APPLE)==1,"Guaranteed reward missing on victory");
                    for(int tick=1;tick<shipped.getRewardTime();tick++) {
                        if(tick==1 || tick==shipped.getRewardTime()/2 || tick==shipped.getRewardTime()-1) raid=reload(h,raid);
                        raid.tick();
                    }
                    check(raid.isEnd(),"Reward timer never ended");
                    raid=reload(h,raid);for(int tick=0;tick<4;tick++)raid.tick();
                    check(count(h,pos,ModItems.NETHER_CORE.get())==expected,"Reload duplicated/lost completion bonus");
                    check(count(h,pos,Items.APPLE)==shipped.getRewardTime(),"Bonus replaced ordinary random rewards");
                    check(drops(h,pos).stream().allMatch(Entity::isInvulnerable),"Rewards lost protection");
                    System.out.println("ECONOMY PAYOUT CHECK: "+mode+" guaranteed="+expected+", random rolls="+shipped.getRewardTime()+", before/after/mid/end reloads passed");
                } finally { raid.end();RaidMusic.stop(level,pos);drops(h,pos).forEach(Entity::discard); }
            }
        } finally {level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        h.succeed();
    }

    @GameTest(template="stability_empty", timeoutTicks=400)
    public static void incompleteAndLegacyRaidsDoNotGrantBonus(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,3,5));
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"economy_legacy"));
        player.moveTo(Vec3.atCenterOf(pos.south(6)));level.addNewPlayer(player);
        var source=profile(RaidDifficulty.NORMAL);
        var module=new BaseRaidModule("economy_legacy",new ItemWeightedModule.Builder().add(Items.APPLE,1).build(),List.of(),source.getWaves(),0,3,10);
        try {
            var active=encounter(h,pos,module,RaidDifficulty.NORMAL,player.getUUID(),AftermathState.ONGOING,3);
            active.getEnemies().add(UUID.randomUUID()); // A tracked unloaded enemy still prevents victory.
            active.tick();check(drops(h,pos).isEmpty(),"Unfinished challenge paid rewards");
            active.lose();active.tick();check(drops(h,pos).isEmpty(),"Failed challenge paid rewards");
            var cancelled=encounter(h,pos,module,RaidDifficulty.NORMAL,player.getUUID(),AftermathState.VICTORY,3);
            cancelled.end();cancelled.tick();check(drops(h,pos).isEmpty(),"Cancelled challenge paid rewards");
            var data=(CompoundTag)BaseRaidModule.CODEC.encodeStart(NbtOps.INSTANCE,module).result().orElseThrow();
            data.remove("guaranteed_cores");
            var legacy=BaseRaidModule.CODEC.parse(NbtOps.INSTANCE,data).result().orElseThrow();
            check(legacy.getGuaranteedCores()==0,"Legacy/custom module gained an implicit bonus");
            var resumed=encounter(h,pos,legacy,RaidDifficulty.NORMAL,player.getUUID(),AftermathState.CELEBRATING,2);
            resumed=reload(h,resumed);resumed.tick();resumed.tick();
            check(resumed.isEnd() && count(h,pos,Items.APPLE)==2 && count(h,pos,ModItems.NETHER_CORE.get())==0,"Legacy countdown changed or paid a new bonus");
            boolean rejected=false;try { module.setGuaranteedCores(-1); } catch(IllegalArgumentException expected) { rejected=true; }
            check(rejected,"Negative guaranteed reward accepted");
            System.out.println("ECONOMY SAFETY CHECK: unfinished, failed, cancelled and legacy saved raids do not grant extra cores");
        } finally {RaidMusic.stop(level,pos);drops(h,pos).forEach(Entity::discard);level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        h.succeed();
    }
}
