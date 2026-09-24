package com.pancake.surviving_the_aftermath.common.enchantment;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.init.ModTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.*;
import java.util.List;

/** Historical enchantment rules, applied once through damage/attribute hooks rather than a second attack. */
@EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID)
public final class LegacyEnchantments {
    public static final List<String> NAMES = List.of("counter_attack", "bloodthirsty", "clean_water", "life_tree", "devoured", "frantic", "execute", "ranger", "moon", "sun");
    public static final String DEVOURED_DATA = "surviving_the_aftermath.devoured";

    public static int level(ItemStack stack, String name) {
        var enchantment = com.pancake.surviving_the_aftermath.common.init.ModEnchantments.get(name);
        return Math.max(0, Math.min(enchantment.getMaxLevel(), stack.getEnchantmentLevel(enchantment)));
    }

    private static Player meleePlayer(DamageSource source) {
        return source.getEntity() instanceof Player player && source.getDirectEntity() == player ? player : null;
    }

    @SubscribeEvent
    public static void incoming(LivingHurtEvent event) {
        Player player = meleePlayer(event.getSource());
        if (player == null) return;
        var stack = player.getMainHandItem();
        float bonus = 0;
        if (event.getEntity().getType().builtInRegistryHolder().is(ModTags.NETHER_MOB)) bonus += 2.5F * level(stack, "clean_water");
        bonus += (1 - player.getHealth() / player.getMaxHealth()) * .1F * level(stack, "frantic") * (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        // These day/night assignments match the historical implementation, including its reversed names.
        if (player.level().isDay()) bonus += level(stack, "moon");
        if (player.level().isNight()) bonus += level(stack, "sun");
        event.setAmount(event.getAmount() + bonus);
    }

    @SubscribeEvent
    public static void afterDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0) return;
        if (event.getSource().getEntity() instanceof Player player) {
            player.heal(event.getAmount() * .05F * level(player.getMainHandItem(), "bloodthirsty"));
        }
        var target = event.getEntity();
        if (meleePlayer(event.getSource()) != null && target.isAlive()) {
            int rank = level(meleePlayer(event.getSource()).getMainHandItem(), "execute");
            float threshold = switch (rank) { case 1 -> .01F; case 2 -> .03F; case 3 -> .05F; default -> 0; };
            if ((target.getHealth() - event.getAmount()) / target.getMaxHealth() < threshold) {
                // Keep the killing player's damage source and normal death events, including raid no-loot policy.
                event.setAmount(Math.max(event.getAmount(), target.getHealth()));
            }
        }
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
        var data = stack.getOrCreateTag();
        float old = data.getFloat(DEVOURED_DATA);
        data.putFloat(DEVOURED_DATA, Math.min(rank + 1, Math.max(0, old) + .1F));
        stack.setTag(data);
    }

    @SubscribeEvent
    public static void useItem(LivingEntityUseItemEvent.Tick event) {
        int rank = level(event.getItem(), "ranger");
        if (rank > 0 && event.getItem().is(Items.BOW) && event.getDuration() > event.getItem().getUseDuration() - 20) {
            event.setDuration(Math.max(0, event.getDuration() - rank));
        }
    }

    @SubscribeEvent
    public static void attributes(ItemAttributeModifierEvent event) {
        var stack = event.getItemStack();
        int rank = level(stack, "devoured");
        float amount = stack.hasTag() ? stack.getTag().getFloat(DEVOURED_DATA) : 0;
        if (event.getSlotType() == EquipmentSlot.MAINHAND && rank > 0 && Float.isFinite(amount) && amount > 0) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(java.util.UUID.fromString("412c831f-22ea-43b8-b74b-d172019ad3d2"), "devoured_enchantment", Math.min(rank + 1, amount), AttributeModifier.Operation.ADDITION));
        }
        int life = level(stack, "life_tree");
        var equippable = stack.getItem() instanceof ArmorItem armor ? armor : null;
        if (life > 0 && equippable != null && event.getSlotType() == equippable.getEquipmentSlot() && List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).contains(equippable.getEquipmentSlot())) {
            var slot = equippable.getEquipmentSlot();
            event.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(java.util.UUID.nameUUIDFromBytes(("aftermath_life_tree_" + slot.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "life_tree_enchantment", life * .1, AttributeModifier.Operation.MULTIPLY_BASE));
        }
    }
}
