package com.pancake.surviving_the_aftermath;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.pancake.surviving_the_aftermath.common.init.*;
import com.pancake.surviving_the_aftermath.common.enchantment.LegacyEnchantments;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class EnchantmentAuditGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(Component.literal(message), 0);
    }
    private static FakePlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "content_test")) {
            @Override public boolean isInvulnerableTo(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        p.setInvulnerable(false);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.snapTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        return p;
    }
    private static ItemStack enchanted(GameTestHelper h, Item item, String name, int rank) {
        var stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, SurvivingTheAftermath.asResource(name))), rank);
        return stack;
    }
    private static Mob mob(GameTestHelper h, EntityType<? extends Mob> type) {
        var mob = type.create(h.getLevel(), EntitySpawnReason.EVENT);
        mob.snapTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 2, 3))));
        mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    private static void hit(GameTestHelper h, LivingEntity target, Player p, float amount) {
        target.invulnerableTime = 0;
        target.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(p), amount);
    }
    private static ItemStack book(GameTestHelper h, String name, int rank) {
        return enchanted(h, Items.ENCHANTED_BOOK, name, rank);
    }
    private static ItemStack anvil(Player p, ItemStack left, ItemStack right) {
        var menu = new net.minecraft.world.inventory.AnvilMenu(1, p.getInventory());
        menu.getSlot(0).set(left.copy());
        menu.getSlot(1).set(right.copy());
        menu.createResult();
        return menu.getSlot(2).getItem().copy();
    }
    private static final int[] MAX = {3,3,4,4,4,1,3,1,4,4};
    private static Item itemFor(String name) {
        return switch(name) {
            case "counter_attack", "life_tree" -> Items.DIAMOND_CHESTPLATE;
            case "ranger" -> Items.BOW;
            default -> Items.DIAMOND_SWORD;
        };
    }
    public static void enchantmentBooksWorkInAnvil(GameTestHelper h) {
        var p = player(h);
        int count = 0;
        for (int i=0; i<LegacyEnchantments.NAMES.size(); i++) {
            String name=LegacyEnchantments.NAMES.get(i);
            for (int rank=1; rank<=MAX[i]; rank++) {
                var result=anvil(p,new ItemStack(itemFor(name)),book(h,name,rank));
                check(!result.isEmpty() && LegacyEnchantments.level(result,name)==rank, "Book failed in survival anvil: "+name+" "+rank);
                count++;
            }
            check(anvil(p,new ItemStack(Items.STICK),book(h,name,1)).isEmpty(), "Invalid item accepted: "+name);
            if (MAX[i]>1) {
                var merged=anvil(p,book(h,name,1),book(h,name,1));
                var applied=anvil(p,new ItemStack(itemFor(name)),merged);
                check(LegacyEnchantments.level(applied,name)==2, "Book merging failed: "+name);
            }
        }
        for (var item:List.of(Items.DIAMOND_SWORD,Items.DIAMOND_AXE,Items.DIAMOND_PICKAXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_HOE,Items.TRIDENT)) {
            check(LegacyEnchantments.level(anvil(p,new ItemStack(item),book(h,"devoured",4)),"devoured")==4,"Devoured rejected tool "+item);
        }
        check(anvil(p,new ItemStack(Items.DIAMOND_HELMET),book(h,"devoured",1)).isEmpty(), "Devoured accepted armor");
        check(anvil(p,enchanted(h,Items.DIAMOND_SWORD,"moon",1),book(h,"sun",1)).isEmpty(), "Moon and Sun stacked");
        var sharp = new ItemStack(Items.DIAMOND_SWORD);
        sharp.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS),1);
        check(anvil(p,sharp,book(h,"clean_water",1)).isEmpty(),"Clean Water stacked with Sharpness in anvil");
        System.out.println("ENCHANTMENT AUDIT: all "+count+" book/rank combinations applied through survival anvil; merging, tools and conflicts passed");
        h.succeed();
    }
    public static void enchantmentExecuteRespectsTotems(GameTestHelper h) {
        var p=player(h);
        p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(h,Items.DIAMOND_SWORD,"execute",3));
        var target=mob(h,EntityTypes.COW);
        target.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.TOTEM_OF_UNDYING));
        target.setHealth(.45F);
        hit(h,target,p,.1F);
        check(target.isAlive() && target.getOffhandItem().isEmpty() && target.getHealth()>0,"Execute bypassed death protection / did not consume totem");
        target.discard();
        target=mob(h,EntityTypes.COW);
        target.setHealth(.45F);target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,1));target.setAbsorptionAmount(4);
        check(target.getAbsorptionAmount()==4,"Absorption fixture was not applied");
        hit(h,target,p,1);
        check(target.isAlive() && Math.abs(target.getHealth()-.45F)<.001,"Execute triggered through fully absorbed damage");
        target.setAbsorptionAmount(0);hit(h,target,p,.1F);
        check(!target.isAlive(),"Execute failed after absorption was gone");target.discard();
        for(int rank=1;rank<=3;rank++) {
            p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(h,Items.DIAMOND_SWORD,"execute",rank));
            float threshold=(rank==1?.01F:rank==2?.03F:.05F)*10;
            target=mob(h,EntityTypes.COW);target.setHealth(threshold+.05F);hit(h,target,p,.025F);
            check(target.isAlive(),"Execute killed above its threshold at rank "+rank);
            hit(h,target,p,.1F);check(!target.isAlive(),"Execute failed below its threshold at rank "+rank);target.discard();
        }
        var combined=anvil(p,enchanted(h,Items.DIAMOND_SWORD,"execute",3),book(h,"bloodthirsty",3));
        p.setItemSlot(EquipmentSlot.MAINHAND,combined);p.setHealth(10);
        target=mob(h,EntityTypes.COW);target.setHealth(.45F);hit(h,target,p,.1F);
        check(!target.isAlive() && Math.abs(p.getHealth()-(10+.45*.15))<.001,"Execute/Bloodthirsty settled different hit damage");target.discard();
        System.out.println("ENCHANTMENT AUDIT: execute thresholds, absorption and totem death protection passed");h.succeed();
    }
    private static void syncEquipment(LivingEntity p) throws Exception {
        var method=LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");method.setAccessible(true);method.invoke(p);
    }
    public static void enchantmentEquipmentAndGrowthLifecycle(GameTestHelper h) throws Exception {
        var p=player(h);
        var slots=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
        var armor=List.of(Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS);
        for(int i=0;i<4;i++) {p.setItemSlot(slots.get(i),enchanted(h,armor.get(i),"life_tree",4));syncEquipment(p);check(Math.abs(p.getMaxHealth()-(20+8*(i+1)))<.01,"Life Tree equipped health mismatch");}
        for(int i=0;i<4;i++) {p.setItemSlot(slots.get(i),ItemStack.EMPTY);syncEquipment(p);check(Math.abs(p.getMaxHealth()-(44-8*i))<.01,"Life Tree health persisted after removal");}
        var sword=enchanted(h,Items.DIAMOND_SWORD,"devoured",4);p.setItemSlot(EquipmentSlot.MAINHAND,sword);syncEquipment(p);
        double base=p.getAttributeValue(Attributes.ATTACK_DAMAGE);p.getRandom().setSeed(12345);
        for(int i=0;i<160;i++){var target=mob(h,EntityTypes.COW);hit(h,target,p,100);target.discard();}
        syncEquipment(p);
        check(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE)-base-5)<.001,"Devoured growth did not update equipped damage or exceeded cap");
        var ops=h.getLevel().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var restored=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,sword).getOrThrow()).getOrThrow();
        p.setItemSlot(EquipmentSlot.MAINHAND,restored);syncEquipment(p);
        check(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE)-base-5)<.001,"Devoured lost damage on item save/reload");
        p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));p.setItemSlot(EquipmentSlot.OFFHAND,restored);syncEquipment(p);
        check(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE)-base)<.001,"Devoured remained active after switching to offhand");
        System.out.println("ENCHANTMENT AUDIT: four armor slots equip/remove, Devoured cap, equipped refresh, item save/reload and offhand passed");h.succeed();
    }
    public static void enchantmentRangerActualBowTicks(GameTestHelper h) throws Exception {
        var p=player(h);
        var tick=LivingEntity.class.getDeclaredMethod("updateUsingItem",ItemStack.class);tick.setAccessible(true);
        for(boolean special:List.of(false,true)) {
            var bow=special?enchanted(h,Items.BOW,"ranger",1):new ItemStack(Items.BOW);
            p.setItemSlot(EquipmentSlot.MAINHAND,bow);p.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            for(int i=0;i<10;i++)tick.invoke(p,bow);
            int elapsed=p.getTicksUsingItem();
            check(elapsed==(special?20:10),"Ranger did not change real draw ticks: "+elapsed);
            check(Math.abs(BowItem.getPowerForTime(elapsed)-(special?1:5F/12))<.001,"Ranger actual shot power mismatch");
            if(special){tick.invoke(p,bow);check(p.getTicksUsingItem()==21,"Ranger accelerated beyond initial draw");}
            p.stopUsingItem();
        }
        System.out.println("ENCHANTMENT AUDIT: actual bow item-use ticks reach full power in 10 ticks; normal bow remains 20 ticks");h.succeed();
    }
    public static void enchantmentCombatLevelsAndConditions(GameTestHelper h) throws Exception {
        var p=player(h);var level=h.getLevel();long previous=level.getDefaultClockTime();
        try {
            for(int rank=1;rank<=4;rank++) {
                p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(h,Items.DIAMOND_SWORD,"clean_water",rank));
                for(boolean nether:List.of(false,true)) {
                    var target=mob(h,nether?EntityTypes.HOGLIN:EntityTypes.COW);float health=target.getHealth();hit(h,target,p,1);
                    check(Math.abs(health-target.getHealth()-(nether?1+rank*2.5:1))<.001,"Clean Water target/rank mismatch");target.discard();
                }
                for(String name:List.of("moon","sun"))for(long time:List.of(1000L,18000L)) {
                    level.getServer().clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(),time);level.updateSkyBrightness();
                    p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(h,Items.DIAMOND_SWORD,name,rank));
                    var target=mob(h,EntityTypes.COW);hit(h,target,p,1);
                    boolean active=name.equals("moon")?level.isBrightOutside():level.isDarkOutside();
                    check(Math.abs(target.getHealth()-(9-(active?rank:0)))<.001,"Day/night enchantment mismatch: "+name);target.discard();
                }
            }
            for(int rank=1;rank<=3;rank++) {
                p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(h,Items.DIAMOND_SWORD,"bloodthirsty",rank));p.setHealth(10);
                var target=mob(h,EntityTypes.COW);hit(h,target,p,4);
                check(Math.abs(p.getHealth()-(10+.2*rank))<.001,"Bloodthirsty rank mismatch");target.discard();
                target=mob(h,EntityTypes.COW);target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,1));target.setAbsorptionAmount(5);p.setHealth(10);hit(h,target,p,4);
                check(Math.abs(p.getHealth()-10)<.001,"Bloodthirsty healed from fully absorbed damage");target.discard();
            }
            p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(h,Items.DIAMOND_SWORD,"frantic",1));syncEquipment(p);
            for(float health:List.of(20F,10F,2F)) {
                p.setHealth(health);var target=mob(h,EntityTypes.COW);hit(h,target,p,1);
                double expected=1+(1-health/20)*.1*p.getAttributeValue(Attributes.ATTACK_DAMAGE);
                check(Math.abs((10-target.getHealth())-expected)<.001,"Frantic health fraction mismatch");target.discard();
            }
            p.setHealth(20);p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            for(int rank=1;rank<=3;rank++) {
                p.setItemSlot(EquipmentSlot.CHEST,enchanted(h,Items.LEATHER_CHESTPLATE,"counter_attack",rank));
                var attacker=mob(h,EntityTypes.COW);attacker.setDeltaMovement(Vec3.ZERO);
                p.invulnerableTime=0;p.hurtServer(level,level.damageSources().mobAttack(attacker),1);
                check(Math.abs(attacker.getDeltaMovement().horizontalDistance()-rank*.5)<.001,"Counter Attack rank mismatch");attacker.discard();
            }
        } finally { level.getServer().clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(),previous);level.updateSkyBrightness(); }
        System.out.println("ENCHANTMENT AUDIT: all combat ranks, positive/negative target and time conditions, absorption and knockback passed");h.succeed();
    }
    public static void enchantmentRelicBookPool(GameTestHelper h) {
        var v=(Villager)mob(h,EntityTypes.VILLAGER);
        v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(ModVillagers.RELIC_DEALER.get())));
        v.getRandom().setSeed(919);
        var names=new HashSet<String>();var customer=player(h);
        for(int i=0;i<384;i++) {
            v.getOffers().clear();com.pancake.surviving_the_aftermath.common.util.LegacyTrades.addOffers(v);
            check(v.getOffers().size()==1,"Relic trade missing");
            var offered=v.getOffers().get(0).getResult();
            check(offered.is(Items.ENCHANTED_BOOK),"Relic result is not a book");
            for(String name:LegacyEnchantments.NAMES) {
                var applied=anvil(customer,new ItemStack(itemFor(name)),offered);
                if(!applied.isEmpty() && LegacyEnchantments.level(applied,name)>0)names.add(name);
            }
        }
        check(names.containsAll(LegacyEnchantments.NAMES),"Relic pool omitted enchantments: "+names);v.discard();
        System.out.println("ENCHANTMENT AUDIT: relic dealer offers all ten usable enchanted books");h.succeed();
    }
}
