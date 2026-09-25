package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.common.raid.*;
import com.pancake.surviving_the_aftermath.common.raid.module.BaseRaidModule;
import com.pancake.surviving_the_aftermath.common.module.entity_info.EntityInfoModule;
import com.pancake.surviving_the_aftermath.common.data.pack.AftermathModuleLoader;
import com.pancake.surviving_the_aftermath.common.util.RaidMobLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.*;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class RaidRosterGameTests {
    private static void check(boolean ok,String message) {
        if(!ok) throw new GameTestAssertException(net.minecraft.network.chat.Component.literal(message),0);
    }
    // Independent expected counts transcribed from the approved table: melee / ranged / hoglin / brute.
    private static final int[][] COUNTS={{2,2,0,0},{2,2,0,0},{2,2,1,0},{3,3,1,0},{3,3,2,0},{3,3,2,2},{4,3,2,3},{6,3,3,4},{6,4,4,5},{6,4,4,5},{7,5,4,6},{7,5,4,6},{8,6,5,7}};
    private static final int[][] MELEE={{2,0,0,0,0},{2,0,0,0,0},{2,0,0,0,0},{1,2,0,0,0},{0,3,0,0,0},{0,1,2,0,0},{0,1,3,0,0},{0,0,4,2,0},{0,0,3,3,0},{0,0,2,4,0},{0,0,1,6,0},{0,0,0,5,2},{0,0,0,4,4}};
    private static final int[][] RANGED={{2,0,0,0,0},{2,0,0,0,0},{2,0,0,0,0},{1,2,0,0,0},{0,3,0,0,0},{0,2,1,0,0},{0,1,2,0,0},{0,0,3,0,0},{0,0,3,1,0},{0,0,2,2,0},{0,0,2,3,0},{0,0,1,3,1},{0,0,0,4,2}};
    private static final int[][] BRUTES={{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,0,0,0,0},{0,2,0,0,0},{0,1,2,0,0},{0,0,4,0,0},{0,0,4,1,0},{0,0,3,2,0},{0,0,2,4,0},{0,0,1,5,0},{0,0,1,3,3}};
    private static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);

    private static BaseRaidModule profile(RaidDifficulty mode) {
        return AftermathModuleLoader.AFTERMATH_MODULE_MAP.get(SurvivingTheAftermath.asResource("raid")).stream()
                .filter(m->m.getModuleName().equals(mode.moduleName())).map(m->(BaseRaidModule)m).findFirst().orElseThrow();
    }

    private static int armorTier(Mob mob) {
        int tier=0;
        for(var slot:SLOTS) {
            var stack=mob.getItemBySlot(slot);
            if(stack.isEmpty()) continue;
            String name=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            int found=List.of("golden","iron","diamond","netherite").indexOf(name.substring(0,name.indexOf('_')))+1;
            check(found>0 && (tier==0 || tier==found),"Unexpected/mixed armor on a roster member");tier=found;
        }
        return tier;
    }

    private static int armorMask(Mob mob) {
        int mask=0;for(int i=0;i<SLOTS.size();i++) if(!mob.getItemBySlot(SLOTS.get(i)).isEmpty()) mask|=1<<i;
        return mask;
    }

    static void checkWave(List<Mob> mobs,int wave) {
        int[] counts=new int[4];int[][] armor=new int[3][5];
        for(Mob mob:mobs) {
            if(mob instanceof Hoglin hoglin) {check(!hoglin.isBaby(),"Baby hoglin");counts[2]++;continue;}
            boolean brute=mob instanceof PiglinBrute;
            check(brute || mob instanceof Piglin,"Unapproved enemy in shared roster");
            if(mob instanceof Piglin piglin) check(!piglin.isBaby(),"Baby piglin");
            boolean bow=mob.getMainHandItem().is(Items.CROSSBOW);
            int role=brute?2:bow?1:0;counts[brute?3:role]++;
            Item expected=brute?(wave>=13?Items.NETHERITE_AXE:wave>=12?Items.DIAMOND_AXE:Items.GOLDEN_AXE)
                    :bow?Items.CROSSBOW:wave>=12?Items.NETHERITE_SWORD:wave>=10?Items.DIAMOND_SWORD:wave>=7?Items.IRON_SWORD:Items.GOLDEN_SWORD;
            check(mob.getMainHandItem().is(expected),"Weapon disagrees with wave table at "+wave);
            int tier=armorTier(mob);armor[role][tier]++;
            int pieces=Integer.bitCount(armorMask(mob));
            if(tier==0) check(pieces==0,"Unarmored member retained vanilla armor");
            else if(!brute && wave==4) check(pieces>=1 && pieces<=2,"Wave four must have 1-2 distinct gold pieces");
            else if(!brute && wave==6) check(pieces>=2 && pieces<=3,"Wave six must have 2-3 distinct pieces");
            else check(pieces==4,"Missing full armor set at "+wave);
            check(mob.getOffhandItem().isEmpty() && !mob.canPickUpLoot(),"Roster equipment can be replaced by pickups");
        }
        if(wave==1) {
            check(counts[0]>=1 && counts[0]<=2 && counts[1]>=1 && counts[1]<=2 && counts[2]==0 && counts[3]==0,"Opening must have 1-2 of each piglin role");
            check(armor[0][0]==counts[0] && armor[1][0]==counts[1],"Opening piglins must be unarmored");
        } else {
            check(Arrays.equals(counts,COUNTS[wave-1]),"Wrong roster at "+wave+": "+Arrays.toString(counts));
            check(Arrays.equals(armor[0],MELEE[wave-1]) && Arrays.equals(armor[1],RANGED[wave-1]) && Arrays.equals(armor[2],BRUTES[wave-1]),"Armor quotas changed at wave "+wave+": "+Arrays.deepToString(armor));
        }
    }

    public static void sharedRosterQuotasAndReload(GameTestHelper h) {
        var level=h.getLevel();int formations=0,entities=0;
        Set<String> openingPairs=new HashSet<>(),arrangements=new HashSet<>();Set<Integer> masks=new HashSet<>();
        Set<String> rangedEnchantments=new HashSet<>();
        for(var mode:RaidDifficulty.values()) {
            BaseRaidModule module=profile(mode);
            var serialized=BaseRaidModule.CODEC.encodeStart(NbtOps.INSTANCE,module).result().orElseThrow();
            var restored=BaseRaidModule.CODEC.parse(NbtOps.INSTANCE,serialized).result().orElseThrow();
            check(restored.getWaves().size()==mode.waves(),"Saved roster changed wave limit");
            for(int wave=1;wave<=mode.waves();wave++) for(int trial=0;trial<32;trial++) {
                List<Mob> mobs=new ArrayList<>();
                try {
                    for(var group:restored.getWaves().get(wave-1)) group.spawnEntity(level,h.absolutePos(new BlockPos(4,3,4))).forEach(entry->entry.ifPresent(entity->{
                        check(entity instanceof Mob,"Non-mob in roster");mobs.add((Mob)entity);
                    }));
                    for(var mob:mobs) {
                        RaidCombat.prepare(level,mob,mode,wave);
                        RaidDifficultyGameTests.verify(mob,mode,wave);
                        if(mob.getMainHandItem().is(Items.CROSSBOW)) rangedEnchantments.addAll(RaidDifficultyGameTests.enchantments(mob.getMainHandItem()).keySet());
                    }
                    checkWave(mobs,wave);
                    if(wave==1) openingPairs.add(mobs.stream().filter(m->m.getMainHandItem().is(Items.CROSSBOW)).count()+":"+mobs.size());
                    if(wave==4 || wave==6) {
                        mobs.stream().filter(m->m instanceof Piglin).forEach(m->masks.add(armorMask(m)));
                        arrangements.add(wave+":"+mobs.stream().map(m->Integer.toString(armorTier(m))).toList());
                    }
                    formations++;entities+=mobs.size();
                } finally {mobs.forEach(Entity::discard);}
            }
        }
        check(openingPairs.size()==4,"Opening counts do not independently cover 1-2 per role");
        check(masks.size()>=10 && arrangements.size()>5,"Partial armor slots or recipient assignment never randomize");
        check(rangedEnchantments.equals(Set.of("quick_charge","piercing","multishot")),"Crossbows have invalid or missing combat enchantments: "+rangedEnchantments);
        var invalid=new RaidEquipmentProfile(Items.CROSSBOW,List.of(1,0),0,0);
        check(RaidEquipmentProfile.CODEC.encodeStart(NbtOps.INSTANCE,invalid).error().isPresent(),"Malformed quota vector accepted");
        var wrongPieces=new RaidEquipmentProfile(Items.CROSSBOW,List.of(0,1,0,0,0),4,1);
        check(RaidEquipmentProfile.CODEC.encodeStart(NbtOps.INSTANCE,wrongPieces).error().isPresent(),"Reversed armor piece range accepted");
        var legacyTag=(CompoundTag)EntityInfoModule.CODEC.encodeStart(NbtOps.INSTANCE,new EntityInfoModule(net.minecraft.world.entity.EntityTypes.PIGLIN,new com.pancake.surviving_the_aftermath.common.module.amount.IntegerAmountModule(1))).result().orElseThrow();
        legacyTag.remove("raid_equipment");
        check(EntityInfoModule.CODEC.parse(NbtOps.INSTANCE,legacyTag).result().orElseThrow().getEquipmentProfile().isEmpty(),"Legacy entity module failed to load");
        System.out.println("SHARED ROSTER CHECK: formations="+formations+", entities="+entities+"; all 27 waves, exact roles/weapons/armor quotas, random opening/slots, saved profiles and crossbow enchantments passed");
        h.succeed();
    }

    public static void rosterCrossbowUsesNativeAi(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(2,2,2));
        for(int x=0;x<15;x++) for(int z=0;z<9;z++) {
            var floor=h.absolutePos(new BlockPos(x,1,z));level.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<7;y++) level.setBlock(floor.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        List<Mob> group=new ArrayList<>();
        profile(RaidDifficulty.EASY).getWaves().get(0).get(1).spawnEntity(level,origin).forEach(entry->entry.ifPresent(e->group.add((Mob)e)));
        Piglin archer=(Piglin)group.remove(0);group.forEach(Entity::discard);
        RaidCombat.prepare(level,archer,RaidDifficulty.EASY,1);RaidMobLoot.mark(archer);
        archer.setImmuneToZombification(true);archer.setPersistenceRequired();archer.getRandom().setSeed(4821L);
        archer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        archer.snapTo(Vec3.atBottomCenterOf(origin));
        var target=net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.EVENT);
        target.setNoAi(true);target.snapTo(Vec3.atBottomCenterOf(origin.east(6)));
        level.addFreshEntity(target);level.addFreshEntity(archer);
        archer.setTarget(target);archer.getBrain().setMemory(MemoryModuleType.ANGRY_AT,target.getUUID());
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,target);
        Set<UUID> arrows=new HashSet<>();
        for(int tick=1;tick<=200;tick++) h.runAfterDelay(tick,()->{
            level.getEntitiesOfClass(Projectile.class,archer.getBoundingBox().inflate(20),p->p.getOwner()==archer).forEach(p->arrows.add(p.getUUID()));
        });
        h.runAfterDelay(201,()->{
            try {
                check(!arrows.isEmpty(),"Roster crossbow piglin never fired through native AI");
                check(archer.getMainHandItem().is(Items.CROSSBOW),"Ranged piglin lost its crossbow");
                System.out.println("CROSSBOW AI CHECK: native brain charged and fired "+arrows.size()+" projectiles; target health="+target.getHealth());
                h.succeed();
            } finally {
                level.getEntitiesOfClass(Projectile.class,archer.getBoundingBox().inflate(32),p->p.getOwner()==archer).forEach(Entity::discard);
                archer.discard();target.discard();
            }
        });
    }
}
