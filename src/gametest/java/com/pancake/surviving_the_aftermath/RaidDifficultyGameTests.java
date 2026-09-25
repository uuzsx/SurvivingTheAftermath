package com.pancake.surviving_the_aftermath;

import com.mojang.authlib.GameProfile;
import com.pancake.surviving_the_aftermath.api.AftermathState;
import com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader;
import com.pancake.surviving_the_aftermath.common.module.entity_info.EntityInfoModule;
import com.pancake.surviving_the_aftermath.common.raid.*;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.util.RaidMobLoot;
import com.pancake.surviving_the_aftermath.common.util.RaidMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;

@net.minecraftforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class RaidDifficultyGameTests {
    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
    private static BaseRaidModule profile(RaidDifficulty difficulty) {
        return AftermathModuleLoader.AFTERMATH_MODULE_MAP.get(SurvivingTheAftermath.asResource("raid")).stream()
                .filter(m -> m.getModuleName().equals(difficulty.moduleName())).map(m -> (BaseRaidModule)m).findFirst().orElseThrow();
    }
    private static Map<String,Integer> enchantments(ItemStack stack) {
        Map<String,Integer> result = new HashMap<>();
        EnchantmentHelper.getEnchantments(stack).forEach((e,n) -> result.put(BuiltInRegistries.ENCHANTMENT.getKey(e).getPath(),n));
        return result;
    }
    private static void verify(Mob mob, RaidDifficulty mode, int wave) {
        if (mob instanceof AbstractPiglin) {
            check(!mob.getMainHandItem().isEmpty(), "Unarmed piglin: " + mode + "/" + wave);
            for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                var stack=mob.getItemBySlot(slot);
                var enchs=enchantments(stack);
                check(Set.of("sharpness","knockback","fire_aspect","protection","thorns").containsAll(enchs.keySet()), "Non-combat enchantment: " + enchs);
                if (mode==RaidDifficulty.EASY || wave<7) check(enchs.isEmpty(), "Premature enchanted equipment");
                else {
                    check(!stack.isEmpty() && !enchs.isEmpty(), "Missing enchanted equipment: " + mode + "/" + wave + "/" + slot);
                    check(enchs.containsKey(slot==EquipmentSlot.MAINHAND ? "sharpness" : "protection"), "Missing effective primary enchantment");
                    check(enchs.values().stream().allMatch(n -> n>0 && n<=5),"Enchantment outside legal levels");
                }
            }
        }
        int count=mode==RaidDifficulty.HARD && wave>=10 ? Math.min(3,wave-9) : 0;
        check(mob.getActiveEffects().size()==count,"Wrong effect count at " + mode + "/" + wave);
        for(var effect:mob.getActiveEffects()) {
            check(Set.of(MobEffects.ABSORPTION,MobEffects.REGENERATION,MobEffects.DAMAGE_RESISTANCE,MobEffects.DAMAGE_BOOST,MobEffects.MOVEMENT_SPEED).contains(effect.getEffect()),"Non-combat potion");
            check(effect.getDuration()==-1 && effect.getAmplifier()<=1,"Potion lost persistence or exceeded tier II");
            if (!(mob instanceof AbstractPiglin || mob instanceof net.minecraft.world.entity.monster.hoglin.Hoglin))
                check(effect.getEffect()!=MobEffects.DAMAGE_BOOST && effect.getEffect()!=MobEffects.MOVEMENT_SPEED,"Ineffective effect on ranged/cube mob");
            if(effect.getEffect()==MobEffects.ABSORPTION) check(mob.getAbsorptionAmount()>0,"Absorption did not grant real extra health");
        }
    }

    @GameTest(template = "stability_empty", timeoutTicks = 400)
    public static void difficultyLiveWavesAndPersistence(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,3,6));
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"difficulty_test"));
        player.moveTo(pos.south(7).getCenter());level.addNewPlayer(player);
        List<Mob> spawned=new ArrayList<>();
        try {
            for (RaidDifficulty difficulty:RaidDifficulty.values()) {
                var module=profile(difficulty);
                check(module.getWaves().size()==difficulty.waves(),"Wrong data-pack wave count");
                int[] transforms={0};
                var raid=new NetherRaid(AftermathState.ONGOING,module,Set.of(player.getUUID()),0F,pos,0,module.getRewardTime(),Set.of(pos),Set.of(),-1,0,List.of(),Set.of(),difficulty) {
                    @Override protected void updateStructure() { transforms[0]++; }
                };
                raid.setLevel(level);
                try {
                    int previous=0;
                    for (int wave=1;wave<=difficulty.waves();wave++) {
                        raid.tick();
                        int expected=module.getWaves().get(wave-1).stream().mapToInt(g -> ((EntityInfoModule)g).getAmountModule().getSpawnAmount()).sum();
                        check(expected>previous && raid.getEnemies().size()==expected && raid.getCurrentWave()==wave-1 && !raid.isEnd(),"Wave skipped or spawn failed: "+difficulty+"/"+wave);
                        previous=expected;
                        for(var id:List.copyOf(raid.getEnemies())) {
                            var mob=(Mob)level.getEntity(id);check(mob!=null,"Spawned enemy missing from server");spawned.add(mob);
                            verify(mob,difficulty,wave);check(RaidMobLoot.isDungeonMob(mob) && !mob.canPickUpLoot(),"Difficulty enemies lost no-loot policy");
                        }
                        var tag=(CompoundTag)NetherRaid.CODEC.encodeStart(NbtOps.INSTANCE,raid).result().orElseThrow();
                        var saved=NetherRaid.CODEC.parse(NbtOps.INSTANCE,tag).result().orElseThrow();
                        check(saved.getDifficulty()==difficulty && saved.getCurrentWave()==wave-1 && saved.getModule().getWaves().size()==difficulty.waves() && saved.getEnemies().equals(raid.getEnemies()),"Reload changed difficulty, wave, or tracked enemies");
                        if(wave==1) {
                            tag.remove("difficulty");
                            check(NetherRaid.CODEC.parse(NbtOps.INSTANCE,tag).result().orElseThrow().getDifficulty()==RaidDifficulty.NORMAL,"Legacy save cannot load");
                        }
                        spawned.forEach(Entity::discard);spawned.clear();raid.getEnemies().clear();
                    }
                    raid.tick();
                    check(raid.getState()==AftermathState.CELEBRATING && transforms[0]==difficulty.waves(),"Did not finish exactly at selected wave limit");
                    check(raid.getRewardTime()==module.getRewardTime()-1,"Reward timing changed");
                    System.out.println("DIFFICULTY LIVE CHECK: "+difficulty+", waves="+transforms[0]+", last_wave_enemies="+previous+", equipment/effects/save passed");
                } finally { raid.end();RaidMusic.stop(level,pos); }
            }
        } finally {spawned.forEach(Entity::discard);level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        h.succeed();
    }

    @GameTest(template = "stability_empty", timeoutTicks = 400)
    public static void combatBuffsAreEffectiveAndInherited(GameTestHelper h) {
        var level=h.getLevel();
        // Seeded sampling exercises every optional enchantment/effect, including the exact 7/10 boundaries.
        Set<String> observed=new HashSet<>();Set<Object> effects=new HashSet<>();
        for(var type:List.of(EntityType.PIGLIN,EntityType.PIGLIN_BRUTE,EntityType.HOGLIN,EntityType.MAGMA_CUBE,EntityType.BLAZE,EntityType.GHAST)) {
            for(int wave:List.of(6,7,9,10,11,12,13)) for(int seed=0;seed<24;seed++) {
                Mob mob=type.create(level);mob.getRandom().setSeed(seed*91L+wave);
                RaidCombat.prepare(level,mob,RaidDifficulty.HARD,wave);verify(mob,RaidDifficulty.HARD,wave);
                for(EquipmentSlot slot:EquipmentSlot.values()) observed.addAll(enchantments(mob.getItemBySlot(slot)).keySet());
                mob.getActiveEffects().forEach(e -> effects.add(e.getEffect()));
                if(type==EntityType.MAGMA_CUBE && wave==13) {
                    var child=EntityType.MAGMA_CUBE.create(level);
                    RaidMobLoot.mark(mob);
                    RaidMobLoot.inherit(mob, child);
                    check(child.getActiveEffects().size()==3 && RaidMobLoot.isDungeonMob(child),"Split lost buffs or no-loot policy");
                    child.discard();
                }
                mob.discard();
            }
        }
        check(observed.equals(Set.of("sharpness","knockback","fire_aspect","protection","thorns")),"Combat enchantment pool incomplete: "+observed);
        check(effects.size()==5,"Potion pool incomplete");
        // Real melee damage: the enchantment must work for a mob, not merely appear on the item.
        var attacker=EntityType.PIGLIN_BRUTE.create(level);
        var plain=EntityType.PIGLIN_BRUTE.create(level);
        var target=EntityType.IRON_GOLEM.create(level);
        attacker.moveTo(h.absolutePos(new BlockPos(4,3,4)).getCenter());plain.moveTo(attacker.position());target.moveTo(attacker.position().add(1,0,0));
        RaidCombat.prepare(level,attacker,RaidDifficulty.HARD,7);
        for(EquipmentSlot slot:EquipmentSlot.values()) plain.setItemSlot(slot,new ItemStack(attacker.getItemBySlot(slot).getItem()));
        attacker.tick();plain.tick();
        float before=target.getHealth();plain.doHurtTarget(target);float plainDamage=before-target.getHealth();
        target.setHealth(target.getMaxHealth());target.invulnerableTime=0;
        before=target.getHealth();attacker.doHurtTarget(target);float enchantedDamage=before-target.getHealth();
        check(enchantedDamage>plainDamage && plainDamage>0,"Sharpness did not increase actual mob damage: "+plainDamage+" -> "+enchantedDamage);
        var armored=EntityType.PIGLIN_BRUTE.create(level);
        RaidCombat.prepare(level,armored,RaidDifficulty.HARD,7);armored.tick();
        before=plain.getHealth();plain.hurt(level.damageSources().mobAttack(target),10);float unenchantedLoss=before-plain.getHealth();
        before=armored.getHealth();armored.hurt(level.damageSources().mobAttack(target),10);float enchantedLoss=before-armored.getHealth();
        check(enchantedLoss<unenchantedLoss && unenchantedLoss>0,"Protection did not reduce actual damage");
        attacker.discard();plain.discard();target.discard();armored.discard();
        System.out.println("DIFFICULTY COMBAT CHECK: all 5 enchantments/5 effects sampled; melee damage "+plainDamage+" -> "+enchantedDamage+", incoming damage "+unenchantedLoss+" -> "+enchantedLoss+"; split buffs retained");
        h.succeed();
    }
}
