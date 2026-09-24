package com.pancake.surviving_the_aftermath.common.enchantment;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.*;
import java.util.List;

/** Historical enchantment rules, applied once through damage/attribute hooks rather than a second attack. */
@EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class LegacyEnchantments {
    public static final List<String> NAMES = List.of("counter_attack", "bloodthirsty", "clean_water", "life_tree", "devoured", "frantic", "execute", "ranger", "moon", "sun");
    public static final String DEVOURED_DATA = "surviving_the_aftermath.devoured";

    public static int level(ItemStack stack, String name) {
        for (var entry : stack.getTagEnchantments().entrySet()) {
            if (entry.getKey().is(SurvivingTheAftermath.asResource(name))) return Math.max(0, Math.min(entry.getIntValue(), entry.getKey().value().getMaxLevel()));
        }
        return 0;
    }

    private static Player meleePlayer(DamageSource source) {
        return source.getEntity() instanceof Player player && source.getDirectEntity() == player ? player : null;
    }

    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent event) {
        Player player = meleePlayer(event.getSource());
        if (player == null) return;
        var stack = player.getMainHandItem();
        float bonus = 0;
        if (event.getEntity().getType().builtInRegistryHolder().is(ModTags.NETHER_MOB)) bonus += 2.5F * level(stack, "clean_water");
        bonus += (1 - player.getHealth() / player.getMaxHealth()) * .1F * level(stack, "frantic") * (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // These day/night assignments match the historical implementation, including its reversed names.
        if (player.level().isBrightOutside()) bonus += level(stack, "moon");
        if (player.level().isDarkOutside()) bonus += level(stack, "sun");
        event.setAmount(event.getAmount() + bonus);
    }

    // Complete the original hit before vanilla applies health loss and death protection.
    // Post-damage setHealth/die bypasses the normal death-protection pipeline.
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void execute(LivingDamageEvent.Pre event) {
        var player = meleePlayer(event.getSource());
        var target = event.getEntity();
        if (player == null || !target.isAlive()) return;
        int rank = level(player.getMainHandItem(), "execute");
        float threshold = switch (rank) { case 1 -> .01F; case 2 -> .03F; case 3 -> .05F; default -> 0; };
        // NeoForge applies absorption AFTER Pre; fully absorbed hits must not execute.
        float healthDamage = Math.max(0, event.getNewDamage() - target.getAbsorptionAmount());
        if (rank > 0 && healthDamage > 0 && (target.getHealth() - healthDamage) / target.getMaxHealth() < threshold) {
            event.setNewDamage(Math.max(event.getNewDamage(), target.getHealth() + target.getAbsorptionAmount()));
        }
    }

    @SubscribeEvent
    public static void afterDamage(LivingDamageEvent.Post event) {
        if (event.getHealthDamage() <= 0) return;
        if (event.getSource().getEntity() instanceof Player player) {
            player.heal(event.getHealthDamage() * .05F * level(player.getMainHandItem(), "bloodthirsty"));
        }
        var target = event.getEntity();
        if (target instanceof Player player && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            int rank = 0;
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) rank = Math.max(rank, level(player.getItemBySlot(slot), "counter_attack"));
            if (rank > 0) {
                double strength = player.getAttributeValue(Attributes.ATTACK_KNOCKBACK) + rank;
                if (player.isSprinting() && player.getAttackStrengthScale(.5F) > .9F) strength++;
                attacker.knockback(strength * .5, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
                player.setDeltaMovement(player.getDeltaMovement().multiply(.6, 1, .6));
                player.setSprinting(false);
            }
        }
    }

    @SubscribeEvent
    public static void killed(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        var stack = player.getMainHandItem();
        int rank = level(stack, "devoured");
        if (rank == 0 || player.getRandom().nextInt(10) >= rank) return;
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        float old = data.getFloatOr(DEVOURED_DATA, 0);
        data.putFloat(DEVOURED_DATA, Math.min(rank + 1, Math.max(0, old) + .1F));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    @SubscribeEvent
    public static void useItem(LivingEntityUseItemEvent.Tick event) {
        int rank = level(event.getItem(), "ranger");
        if (rank > 0 && event.getItem().is(Items.BOW) && event.getDuration() > event.getItem().getUseDuration(event.getEntity()) - 20) {
            event.setDuration(Math.max(0, event.getDuration() - rank));
        }
    }

    @SubscribeEvent
    public static void attributes(ItemAttributeModifierEvent event) {
        var stack = event.getItemStack();
        int rank = level(stack, "devoured");
        float amount = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloatOr(DEVOURED_DATA, 0);
        if (rank > 0 && Float.isFinite(amount) && amount > 0) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(SurvivingTheAftermath.asResource("devoured_enchantment"), Math.min(rank + 1, amount), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        int life = level(stack, "life_tree");
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (life > 0 && equippable != null && List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).contains(equippable.slot())) {
            var slot = equippable.slot();
            event.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(SurvivingTheAftermath.asResource("life_tree_" + slot.getName()), life * .1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.bySlot(slot));
        }
    }
}
