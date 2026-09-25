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
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.*;

public final class RaidDifficultyGameTests {
    private static void check(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(net.minecraft.network.chat.Component.literal(message), 0);
    }
    private static BaseRaidModule profile(RaidDifficulty difficulty) {
        return AftermathModuleLoader.AFTERMATH_MODULE_MAP.get(SurvivingTheAftermath.asResource("raid")).stream()
                .filter(m -> m.getModuleName().equals(difficulty.moduleName())).map(m -> (BaseRaidModule)m).findFirst().orElseThrow();
    }
    private static Map<String,Integer> enchantments(ItemStack stack) {
        Map<String,Integer> result = new HashMap<>();
        stack.getEnchantments().entrySet().forEach(e -> result.put(e.getKey().unwrapKey().orElseThrow().identifier().getPath(),e.getIntValue()));
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
            check(Set.of(MobEffects.ABSORPTION,MobEffects.REGENERATION,MobEffects.RESISTANCE,MobEffects.STRENGTH,MobEffects.SPEED).contains(effect.getEffect()),"Non-combat potion");
            check(effect.getDuration()==-1 && effect.getAmplifier()<=1,"Potion lost persistence or exceeded tier II");
            if (!(mob instanceof AbstractPiglin || mob instanceof net.minecraft.world.entity.monster.hoglin.Hoglin))
                check(effect.getEffect()!=MobEffects.STRENGTH && effect.getEffect()!=MobEffects.SPEED,"Ineffective effect on ranged/cube mob");
            if(effect.getEffect()==MobEffects.ABSORPTION) check(mob.getAbsorptionAmount()>0,"Absorption did not grant real extra health");
        }
    }

    public static void difficultyLiveWavesAndPersistence(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(6,3,6));
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"difficulty_test"));
        player.snapTo(net.minecraft.world.phys.Vec3.atCenterOf(pos.south(7)));level.addNewPlayer(player);
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

    public static void weightedEquipmentProgression(GameTestHelper h) {
        var level=h.getLevel();
        int samples=256;
        for (RaidDifficulty mode:RaidDifficulty.values()) {
            double firstQuality=0;
            for(int wave=1;wave<=mode.waves();wave++) {
                int[] counts=new int[4];
                var seeds=new java.util.Random(0x52414944L+mode.ordinal()*1000L+wave);
                for(int sample=0;sample<samples;sample++) {
                    boolean brute=(sample&1)!=0;
                    Mob mob=(brute?net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE:net.minecraft.world.entity.EntityTypes.PIGLIN).create(level,EntitySpawnReason.EVENT);
                    try {
                        mob.getRandom().setSeed(seeds.nextLong());
                        RaidCombat.prepare(level,mob,mode,wave);
                        verify(mob,mode,wave);
                        String helmet=BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(EquipmentSlot.HEAD).getItem()).getPath();
                        int material=List.of("golden_helmet","iron_helmet","diamond_helmet","netherite_helmet").indexOf(helmet);
                        check(material>=0,"Missing/unknown armor: "+helmet);
                        counts[material]++;
                        String prefix=helmet.substring(0,helmet.indexOf('_'));
                        String weapon=BuiltInRegistries.ITEM.getKey(mob.getMainHandItem().getItem()).getPath();
                        check(weapon.equals(prefix+(brute?"_axe":"_sword")),"Wrong weapon for sampled kit");
                        int pieces=0;
                        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
                            var armor=mob.getItemBySlot(slot);
                            if(!armor.isEmpty()) {
                                pieces++;
                                check(BuiltInRegistries.ITEM.getKey(armor.getItem()).getPath().startsWith(prefix+"_"),"Kit changed material between slots");
                            }
                        }
                        check(pieces>=1 && (wave<5 || pieces==4),"Lost armor coverage in later waves");
                        boolean diamondAllowed=mode==RaidDifficulty.NORMAL && wave>=8 || mode==RaidDifficulty.HARD && wave>=9;
                        check(material<2 || diamondAllowed,"Diamond appeared before late waves");
                        check(material!=3 || mode==RaidDifficulty.HARD && wave>=12,"Premature netherite");
                    } finally {mob.discard();}
                }
                check(counts[0]>0 && counts[1]>0,"Wave uses uniform material: "+mode+"/"+wave);
                double quality=(counts[1]+2.0*counts[2]+3.0*counts[3])/samples;
                if(wave==1) {
                    firstQuality=quality;
                    check(counts[0]>samples*0.65,"Opening wave is not mostly gold");
                }
                if(wave==mode.waves()) {
                    check(quality>firstQuality+0.15,"Gear did not get harder across waves");
                    if(mode==RaidDifficulty.EASY) check(counts[1]>samples*0.20 && counts[1]<samples*0.40,"Easy finale iron weight drifted");
                    if(mode==RaidDifficulty.NORMAL) check(counts[2]>samples*0.20 && counts[2]<samples*0.40,"Normal finale diamond weight drifted");
                    if(mode==RaidDifficulty.HARD) {
                        check(counts[2]>samples*0.35 && counts[2]<samples*0.55,"Hard finale diamond weight drifted");
                        check(counts[3]>samples*0.15 && counts[3]<samples*0.35,"Hard finale netherite weight drifted");
                    }
                }
                System.out.println("RAID EQUIPMENT SAMPLE: "+mode+"/"+wave+" gold/iron/diamond/netherite="+Arrays.toString(counts));
            }
        }
        // Legacy normal challenges had eleven waves; a saved extra wave uses the final mix safely.
        var legacy=net.minecraft.world.entity.EntityTypes.PIGLIN.create(level,EntitySpawnReason.EVENT);
        try {RaidCombat.prepare(level,legacy,RaidDifficulty.NORMAL,11);verify(legacy,RaidDifficulty.NORMAL,11);}
        finally {legacy.discard();}
        System.out.println("WEIGHTED EQUIPMENT CHECK: 6912 piglin/brute samples, 27 waves; mixed kits, late gates, combat buffs and legacy extra wave passed");
        h.succeed();
    }

    public static void combatBuffsAreEffectiveAndInherited(GameTestHelper h) {
        var level=h.getLevel();
        // Seeded sampling exercises every optional enchantment/effect, including the exact 7/10 boundaries.
        Set<String> observed=new HashSet<>();Set<Object> effects=new HashSet<>();
        for(var type:List.of(net.minecraft.world.entity.EntityTypes.PIGLIN,net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE,net.minecraft.world.entity.EntityTypes.HOGLIN,net.minecraft.world.entity.EntityTypes.MAGMA_CUBE,net.minecraft.world.entity.EntityTypes.BLAZE,net.minecraft.world.entity.EntityTypes.GHAST)) {
            for(int wave:List.of(6,7,9,10,11,12,13)) for(int seed=0;seed<24;seed++) {
                Mob mob=type.create(level,EntitySpawnReason.EVENT);mob.getRandom().setSeed(seed*91L+wave);
                RaidCombat.prepare(level,mob,RaidDifficulty.HARD,wave);verify(mob,RaidDifficulty.HARD,wave);
                for(EquipmentSlot slot:EquipmentSlot.values()) observed.addAll(enchantments(mob.getItemBySlot(slot)).keySet());
                mob.getActiveEffects().forEach(e -> effects.add(e.getEffect()));
                if(type==net.minecraft.world.entity.EntityTypes.MAGMA_CUBE && wave==13) {
                    var child=net.minecraft.world.entity.EntityTypes.MAGMA_CUBE.create(level,EntitySpawnReason.EVENT);
                    RaidMobLoot.mark(mob);
                    RaidMobLoot.split(new net.neoforged.neoforge.event.entity.living.MobSplitEvent(mob, List.of(child)));
                    check(child.getActiveEffects().size()==3 && RaidMobLoot.isDungeonMob(child),"Split lost buffs or no-loot policy");
                    child.discard();
                }
                mob.discard();
            }
        }
        check(observed.equals(Set.of("sharpness","knockback","fire_aspect","protection","thorns")),"Combat enchantment pool incomplete: "+observed);
        check(effects.size()==5,"Potion pool incomplete");
        // Real melee damage: the enchantment must work for a mob, not merely appear on the item.
        var attacker=net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE.create(level,EntitySpawnReason.EVENT);
        var plain=net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE.create(level,EntitySpawnReason.EVENT);
        var target=net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.EVENT);
        attacker.snapTo(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(new BlockPos(4,3,4))));plain.snapTo(attacker.position());target.snapTo(attacker.position().add(1,0,0));
        RaidCombat.prepare(level,attacker,RaidDifficulty.HARD,7);
        for(EquipmentSlot slot:EquipmentSlot.values()) plain.setItemSlot(slot,new ItemStack(attacker.getItemBySlot(slot).getItem()));
        attacker.tick();plain.tick();
        float before=target.getHealth();plain.doHurtTarget(level,target);float plainDamage=before-target.getHealth();
        target.setHealth(target.getMaxHealth());target.invulnerableTime=0;
        before=target.getHealth();attacker.doHurtTarget(level,target);float enchantedDamage=before-target.getHealth();
        check(enchantedDamage>plainDamage && plainDamage>0,"Sharpness did not increase actual mob damage: "+plainDamage+" -> "+enchantedDamage);
        var armored=net.minecraft.world.entity.EntityTypes.PIGLIN_BRUTE.create(level,EntitySpawnReason.EVENT);
        RaidCombat.prepare(level,armored,RaidDifficulty.HARD,7);armored.tick();
        // Compare protection on identical sampled materials, not two independently randomized kits.
        for(EquipmentSlot slot:EquipmentSlot.values()) plain.setItemSlot(slot,new ItemStack(armored.getItemBySlot(slot).getItem()));
        plain.tick();
        before=plain.getHealth();plain.hurtServer(level,level.damageSources().mobAttack(target),10);float unenchantedLoss=before-plain.getHealth();
        before=armored.getHealth();armored.hurtServer(level,level.damageSources().mobAttack(target),10);float enchantedLoss=before-armored.getHealth();
        check(enchantedLoss<unenchantedLoss && unenchantedLoss>0,"Protection did not reduce actual damage");
        attacker.discard();plain.discard();target.discard();armored.discard();
        System.out.println("DIFFICULTY COMBAT CHECK: all 5 enchantments/5 effects sampled; melee damage "+plainDamage+" -> "+enchantedDamage+", incoming damage "+unenchantedLoss+" -> "+enchantedLoss+"; split buffs retained");
        h.succeed();
    }
}
