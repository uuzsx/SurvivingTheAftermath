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
        var customer=player(h);
        var books = com.pancake.surviving_the_aftermath.common.util.ModEnchantedBooks.all(h.getLevel().registryAccess());
        var seen=new HashSet<String>();var foods=new HashSet<Item>();var identities=new Random(919);
        for(int sample=0;sample<256;sample++) {
            var v=EntityTypes.VILLAGER.create(h.getLevel(), EntitySpawnReason.EVENT);
            v.setUUID(new UUID(identities.nextLong(),identities.nextLong()));
            v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(ModVillagers.RELIC_DEALER.get())).withLevel(5));
            check(v.getOffers().size()==15,"Master must have exactly 15 offers");
            for(var offer:v.getOffers()) {
                if(!offer.getResult().is(Items.ENCHANTED_BOOK)){
                    var food=offer.getResult().getItem();foods.add(food);
                    int expected=food==ModItems.RAW_FALUKORV.get() || food==ModItems.COOKED_FALUKORV.get() || food==ModItems.EGG_TART.get() ? 1 : 2;
                    check(offer.getBaseCostA().getCount()==expected,"Master food price not reduced");continue;
                }
                var book=books.stream().filter(b->ItemStack.isSameItemSameComponents(b.stack(),offer.getResult())).findFirst().orElseThrow();
                check(offer.getBaseCostA().getCount()==new int[]{2,4,5,7,9}[book.rank()-1],"Wrong discounted book price");
                var budget=new ItemStack(ModItems.NETHER_CORE.get(),10);var ordinaryBook=new ItemStack(Items.BOOK);
                check(offer.take(budget,ordinaryBook) && ordinaryBook.isEmpty(),"Normal guaranteed cores cannot buy this book");
                if(seen.add(book.name()+":"+book.rank())) {
                    var applied=anvil(customer,new ItemStack(itemFor(book.name())),offer.getResult());
                    check(!applied.isEmpty() && LegacyEnchantments.level(applied,book.name())==book.rank(),"Unusable relic book "+book.name()+book.rank());
                }
            }
            v.discard();
        }
        check(seen.size()==31 && foods.size()==6,"Random merchant pool omitted ranks or foods: "+seen+" food count="+foods.size());
        System.out.println("ENCHANTMENT AUDIT: relic dealer pool covers all ten usable enchanted books, all 31 ranks and six foods across 256 merchants");h.succeed();
    }

    public static void relicCatalogueProgression(GameTestHelper h) throws Exception {
        var v=(Villager)mob(h,EntityTypes.VILLAGER);
        v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(ModVillagers.RELIC_DEALER.get())));v.setVillagerXp(1);
        int[] sizes={3,6,9,12,15};int[] costs={3,2,2,2,2};
        var upgrade=Villager.class.getDeclaredMethod("increaseMerchantCareer",net.minecraft.server.level.ServerLevel.class);upgrade.setAccessible(true);
        for(int tier=1;tier<=5;tier++) {
            check(v.getVillagerData().level()==tier,"Career did not advance");
            check(v.getOffers().size()==sizes[tier-1],"Unexpected catalogue size at tier "+tier);
            check(v.getOffers().get(0).getBaseCostA().getCount()==costs[tier-1],"Prior item not discounted at tier "+tier);
            check(v.getOffers().stream().allMatch(o->o.getBaseCostA().is(ModItems.NETHER_CORE.get())),"Currency changed");
            if(tier==1) {
                var offer=v.getOffers().get(0);var cores=offer.getCostA().copy();var paper=offer.getCostB().copy();
                check(offer.take(cores,paper) && cores.isEmpty() && paper.isEmpty(),"Core/book payment cannot complete");
                v.notifyTrade(offer);check(v.getVillagerXp()==11,"Trading did not award career XP");
            }
            check(v.getOffers().get(0).getUses()==1,"Upgrade reset purchased stock");
            for(int a=0;a<v.getOffers().size();a++)for(int b=a+1;b<v.getOffers().size();b++)
                check(!ItemStack.isSameItemSameComponents(v.getOffers().get(a).getResult(),v.getOffers().get(b).getResult()),"Duplicate stock");
            if(tier<5)upgrade.invoke(v ,h.getLevel());
        }
        check(v.getOffers().stream().filter(o->!o.getResult().is(Items.ENCHANTED_BOOK)).count()==5,"Dealer should have five foods");
        var tick=Villager.class.getDeclaredMethod("customServerAiStep",net.minecraft.server.level.ServerLevel.class);tick.setAccessible(true);
        // Avoid vanilla's legitimate initial catch-up restock; exercise its daily interval instead.
        var restockTime=Villager.class.getDeclaredField("lastRestockGameTime");restockTime.setAccessible(true);
        restockTime.setLong(v,h.getLevel().getGameTime());
        v.tickCount=200;tick.invoke(v ,h.getLevel());
        check(v.getOffers().get(0).getUses()==0,"Dealer without workstation never restocks");
        v.getOffers().get(0).increaseUses();v.tickCount=400;tick.invoke(v ,h.getLevel());
        check(v.getOffers().get(0).getUses()==1,"Restock limit bypassed");
        v.discard();System.out.println("RELIC CATALOGUE CHECK: five tiers 3/6/9/12/15, discounts, inventory retention and timed restock");h.succeed();
    }


    public static void relicLegacySaveMigration(GameTestHelper h) throws Exception {
        var v=(Villager)mob(h,EntityTypes.VILLAGER);
        v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(ModVillagers.RELIC_DEALER.get())));v.setVillagerXp(1);
        var rankFour=com.pancake.surviving_the_aftermath.common.util.ModEnchantedBooks.all(h.getLevel().registryAccess()).stream().filter(b->b.rank()==4).findFirst().orElseThrow().stack();
        var legacy=new MerchantOffer(new ItemCost(ModItems.NETHER_CORE.get(),25),Optional.of(new ItemCost(Items.BOOK)),rankFour,12,30,.2F);
        for(int i=0;i<3;i++)legacy.increaseUses();
        v.getOffers().clear();v.getOffers().add(legacy);
        var expensiveFood=new MerchantOffer(new ItemCost(ModItems.NETHER_CORE.get(),10),Optional.empty(),new ItemStack(ModItems.HAMBURGER.get()),16,5,0F);
        expensiveFood.increaseUses();expensiveFood.increaseUses();v.getOffers().add(expensiveFood);
        var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess());
        v.saveWithoutId(output);var saved=output.buildResult();v.discard();
        var loaded=EntityTypes.VILLAGER.create(h.getLevel(), EntitySpawnReason.EVENT);
        loaded.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved));
        var update=Villager.class.getDeclaredMethod("updateSpecialPrices",Player.class);update.setAccessible(true);
        var customer=player(h);update.invoke(loaded,customer);
        check(loaded.getOffers().size()==3,"Legacy high-rank book lost or new stock absent");
        check(loaded.getOffers().get(0).getUses()==3,"Migration reset purchases");
        check(loaded.getOffers().get(0).getBaseCostA().getCount()==9,"Legacy price not normalized");
        check(loaded.getOffers().get(1).getBaseCostA().getCount()==3 && loaded.getOffers().get(1).getUses()==2,"Legacy food price/stock not migrated");
        update.invoke(loaded,customer);update.invoke(loaded,customer);
        check(loaded.getOffers().size()==3 && loaded.getOffers().get(0).getUses()==3,"Opening duplicated/refilled stock");
        loaded.getOffers().get(0).setToOutOfStock();update.invoke(loaded,customer);
        check(loaded.getOffers().get(0).isOutOfStock(),"Reopening refilled exhausted offer");
        loaded.discard();System.out.println("RELIC MIGRATION CHECK: real entity save/load, old high-rank books and used stock retained, no reroll/refill on reopen");h.succeed();
    }


    public static void relicCreativeBookPlacement(GameTestHelper h) {
        for(boolean operator:new boolean[]{false,true,false}) {
            CreativeModeTabs.tryRebuildTabContents(h.getLevel().enabledFeatures(),operator,h.getLevel().registryAccess());
            var mod=ModTabs.TAB.get();
            var ingredients=BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.INGREDIENTS);
            check(mod.getDisplayItems().stream().filter(com.pancake.surviving_the_aftermath.common.util.ModEnchantedBooks::isModBook).count()==31,"Mod tab missing book ranks after rebuild");
            check(ingredients.getDisplayItems().stream().noneMatch(com.pancake.surviving_the_aftermath.common.util.ModEnchantedBooks::isModBook),"Mod books remain in ingredients");
            check(ingredients.getDisplayItems().stream().anyMatch(i->i.is(Items.ENCHANTED_BOOK)),"Vanilla books removed");
            check(CreativeModeTabs.searchTab().getDisplayItems().stream().filter(com.pancake.surviving_the_aftermath.common.util.ModEnchantedBooks::isModBook).count()==31,"Search lost mod books");
        }
        System.out.println("RELIC CREATIVE CHECK: all 31 ranks in mod tab and search, no mod books in ingredients, vanilla preserved through rebuilds");h.succeed();
    }
}
