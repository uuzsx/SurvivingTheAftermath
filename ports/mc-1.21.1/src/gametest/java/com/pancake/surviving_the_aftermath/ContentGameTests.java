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
import net.minecraft.world.entity.npc.Villager;
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

@net.neoforged.neoforge.gametest.GameTestHolder(SurvivingTheAftermath.MOD_ID)
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class ContentGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
    private static FakePlayer player(GameTestHelper h) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "content_test")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        p.setInvulnerable(false);
        try { var field=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); field.setAccessible(true);field.setInt(p,0); } catch(ReflectiveOperationException e) {throw new RuntimeException(e);}
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.moveTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(3, 2, 3))));
        return p;
    }
    private static ItemStack enchanted(GameTestHelper h, Item item, String name, int rank) {
        var stack = new ItemStack(item);
        stack.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, SurvivingTheAftermath.asResource(name))), rank);
        return stack;
    }
    private static Mob mob(GameTestHelper h, EntityType<? extends Mob> type) {
        var mob = type.create(h.getLevel());
        mob.moveTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 2, 3))));
        mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "stability_empty")
    public static void restoredRegistryAndLanguage(GameTestHelper h) throws Exception {
        var ids = List.of("diamond_flint_and_steel","raw_falukorv","cooked_falukorv","egg_tart","stack_of_egg_tarts","hamburger","tianjin_pancake","nether_core","music_disk_orchelias_vox");
        for (String locale : List.of("zh_cn", "en_us")) {
            try (var stream = ContentGameTests.class.getResourceAsStream("/assets/surviving_the_aftermath/lang/" + locale + ".json"); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var lang = JsonParser.parseReader(reader).getAsJsonObject();
                for (String id : ids) {
                    var item = BuiltInRegistries.ITEM.get(SurvivingTheAftermath.asResource(id));
                    check(item != null && item != Items.AIR, "Missing item registry entry: " + id);
                    check(lang.has(item.getDescriptionId()), "Missing translated item: " + id + " / " + locale);
                    check(ContentGameTests.class.getResource("/assets/surviving_the_aftermath/models/item/"+id+".json") != null, "Missing model " + id);
                }
                check(lang.has(ModMobEffects.COWARDICE.get().getDescriptionId()), "Cowardice would display its translation key");
                if (locale.equals("zh_cn")) check(lang.get(ModMobEffects.COWARDICE.get().getDescriptionId()).getAsString().equals("懦弱"), "Incorrect Cowardice Chinese name");
                for (String id : LegacyEnchantments.NAMES) {
                    var enchantment = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, SurvivingTheAftermath.asResource(id)));
                    check(lang.has("enchantment.surviving_the_aftermath." + id), "Missing enchantment translation: " + id);
                    check(enchantment.value().getMaxLevel() > 0, "Unusable enchantment " + id);
                }
            }
        }
        var clean=enchanted(h, Items.DIAMOND_SWORD,"clean_water",1).getTagEnchantments().keySet().iterator().next();
        var sharp=h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
        check(!Enchantment.areCompatible(clean, sharp), "Clean Water stacked with Sharpness");
        check(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(ModTabs.TAB.get()) != null, "Missing creative tab");
        h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void restoredFoodConsumption(GameTestHelper h) {
        var items=List.of(ModItems.RAW_FALUKORV.get(),ModItems.COOKED_FALUKORV.get(),ModItems.EGG_TART.get(),ModItems.STACK_OF_EGG_TARTS.get(),ModItems.HAMBURGER.get(),ModItems.TIANJIN_PANCAKE.get());
        int[] nutrition={3,10,3,9,7,8}; float[] saturation={1.8F,280,12,108,56,224};
        for(int i=0;i<items.size();i++) {
            var p=player(h); p.getFoodData().setFoodLevel(0);
            var stack=new ItemStack(items.get(i),2); var food=stack.get(DataComponents.FOOD);
            check(food.nutrition()==nutrition[i] && Math.abs(food.saturation()-saturation[i])<.001, "Food values changed for " + items.get(i));
            stack.finishUsingItem(h.getLevel(),p);
            check(stack.getCount()==1 && p.getFoodData().getFoodLevel()==nutrition[i], "Food could not be eaten " + items.get(i));
            switch(i) {
                case 1 -> effect(p,MobEffects.DAMAGE_BOOST,4800,0);
                case 2 -> effect(p,MobEffects.HEALTH_BOOST,4800,0);
                case 3 -> { effect(p,MobEffects.HEALTH_BOOST,3600,1);effect(p,MobEffects.CONFUSION,60,0); }
                case 4 -> effect(p,MobEffects.REGENERATION,6000,0);
                case 5 -> { effect(p,MobEffects.SATURATION,4800,0);effect(p,MobEffects.LUCK,2400,0); }
            }
        }
        h.succeed();
    }
    private static void effect(Player p, Holder<MobEffect> effect, int ticks, int amplifier) {
        var active=p.getEffect(effect);check(active!=null && active.getDuration()==ticks && active.getAmplifier()==amplifier,"Missing or changed food effect " + effect);
    }

    @GameTest(template = "stability_empty")
    public static void cowardicePricesAndLegacyTrades(GameTestHelper h) throws Exception {
        var v=(Villager)mob(h,EntityType.VILLAGER);
        v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.RELIC_DEALER.get()));
        var offers=v.getOffers();
        check(offers.size()==1 && offers.getFirst().getBaseCostA().is(ModItems.NETHER_CORE.get()) && offers.getFirst().getResult().is(Items.ENCHANTED_BOOK),"Relic dealer cannot exchange cores for enchanted books");
        check(!offers.getFirst().getResult().get(DataComponents.STORED_ENCHANTMENTS).isEmpty(), "Relic dealer sold an unenchanted book");
        offers.clear(); offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,16),new ItemStack(Items.BREAD),12,1,.05F));
        var p=player(h); var update=Villager.class.getDeclaredMethod("updateSpecialPrices",Player.class);update.setAccessible(true);
        p.addEffect(new MobEffectInstance(ModMobEffects.COWARDICE,100,0)); update.invoke(v,p);
        check(offers.getFirst().getCostA().getCount()==21,"Cowardice did not restore the original price penalty");
        offers.getFirst().resetSpecialPriceDiff();p.removeEffect(ModMobEffects.COWARDICE);update.invoke(v,p);
        check(offers.getFirst().getCostA().getCount()==16,"Cowardice price leaked to an unaffected player");
        p.addEffect(new MobEffectInstance(ModMobEffects.COWARDICE,100,1));update.invoke(v,p);
        check(offers.getFirst().getCostA().getCount()==21,"Second level penalty differs from legacy rule");
        for(String profession:List.of("farmer","butcher")) {
            var seller=(Villager)mob(h,EntityType.VILLAGER);
            seller.setVillagerData(seller.getVillagerData().setProfession(BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(profession))).setLevel(4));
            check(seller.getOffers().stream().anyMatch(o->BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).getNamespace().equals(SurvivingTheAftermath.MOD_ID)),"Missing food trades: " + profession);
            seller.discard();
        }
        v.discard(); h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void restoredDiscInJukebox(GameTestHelper h) {
        var pos=new BlockPos(3,2,3);h.setBlock(pos,Blocks.JUKEBOX);
        var box=(JukeboxBlockEntity)h.getBlockEntity(pos);box.setTheItem(new ItemStack(ModItems.MUSIC_DISK_ORCHELIAS_VOX.get()));
        check(box.getSongPlayer().isPlaying(),"Restored music disc cannot play in a jukebox");
        check(box.getComparatorOutput()==15,"Music disc lost comparator signal");
        check(Math.abs(box.getSongPlayer().getSong().lengthInSeconds()-269.599)<.01,"Music disc length mismatch");
        box.setTheItem(ItemStack.EMPTY);check(!box.getSongPlayer().isPlaying(),"Jukebox did not stop on disc removal");h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void restoredCombatEnchantments(GameTestHelper h) {
        var p=player(h);var level=h.getLevel();
        // Exact incoming damage and real health loss, rather than just testing the hook helper.
        for(String name:List.of("clean_water","bloodthirsty","frantic","moon","sun","execute")) {
            var target=mob(h,name.equals("clean_water")?EntityType.HOGLIN:EntityType.COW);
            p.setHealth(10);p.setItemSlot(EquipmentSlot.MAINHAND,enchanted(h,Items.DIAMOND_SWORD,name,name.equals("frantic")?1:3));
            float before=target.getHealth();float damage=4;
            if(name.equals("execute")){target.setHealth(.5F);damage=.1F;}
            target.hurt(level.damageSources().playerAttack(p),damage);
            switch(name) {
                case "clean_water" -> check(Math.abs(before-target.getHealth()-11.5F)<.01,"Clean Water damage missing");
                case "bloodthirsty" -> check(Math.abs(p.getHealth()-10.6F)<.01,"Bloodthirsty did not heal");
                case "frantic" -> check(before-target.getHealth()>4,"Frantic damage missing");
                case "moon" -> check(Math.abs(before-target.getHealth()-(level.isDay()?7:4))<.01,"Moon legacy day condition missing");
                case "sun" -> check(Math.abs(before-target.getHealth()-(level.isNight()?7:4))<.01,"Sun legacy night condition missing");
                case "execute" -> check(!target.isAlive(),"Execute did not finish a low-health target");
            }
            target.discard();
        }
        p.setHealth(20);p.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);p.setItemSlot(EquipmentSlot.CHEST,enchanted(h,Items.LEATHER_CHESTPLATE,"counter_attack",3));
        var attacker=mob(h,EntityType.ZOMBIE);p.hurt(level.damageSources().mobAttack(attacker),2);
        check(attacker.getDeltaMovement().horizontalDistanceSqr()>0,"Counter Attack failed to push the attacker");attacker.discard();
        var bow=enchanted(h,Items.BOW,"ranger",1);var use=new LivingEntityUseItemEvent.Tick(p,bow,bow.getUseDuration(p));NeoForge.EVENT_BUS.post(use);
        check(use.getDuration()==bow.getUseDuration(p)-1,"Ranger did not accelerate bow drawing");h.succeed();
    }

    @GameTest(template = "stability_empty")
    public static void restoredGrowthEnchantments(GameTestHelper h) {
        var p=player(h);var sword=enchanted(h,Items.DIAMOND_SWORD,"devoured",4);p.setItemSlot(EquipmentSlot.MAINHAND,sword);p.getRandom().setSeed(12345);
        for(int i=0;i<32;i++){var target=mob(h,EntityType.COW);target.hurt(h.getLevel().damageSources().playerAttack(p),100);target.discard();}
        float growth=sword.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getFloat(LegacyEnchantments.DEVOURED_DATA);
        check(growth>0 && growth<=5,"Devoured kill growth missing or above cap");
        var attack=sword.getAttributeModifiers().modifiers().stream().filter(m->m.modifier().id().equals(SurvivingTheAftermath.asResource("devoured_enchantment"))).findFirst().orElseThrow();
        check(Math.abs(attack.modifier().amount()-growth)<.001,"Devoured data has no damage modifier");
        var helmet=enchanted(h,Items.LEATHER_HELMET,"life_tree",4);
        var health=helmet.getAttributeModifiers().modifiers().stream().filter(m->m.attribute().equals(Attributes.MAX_HEALTH)).findFirst().orElseThrow();
        check(Math.abs(health.modifier().amount()-.4)<.001 && health.slot()==EquipmentSlotGroup.HEAD,"Life Tree has no matching armor health modifier");
        h.succeed();
    }
}
