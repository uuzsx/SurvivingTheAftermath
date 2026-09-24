package com.pancake.surviving_the_aftermath.common.init;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.registries.*;
import java.util.*;
public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, SurvivingTheAftermath.MOD_ID);
    private static final Map<String,RegistryObject<Enchantment>> VALUES = new LinkedHashMap<>();
    static {
        VALUES.put("counter_attack", ENCHANTMENTS.register("counter_attack", () -> new Legacy("counter_attack", 3, EnchantmentCategory.ARMOR, new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})));
        VALUES.put("bloodthirsty", ENCHANTMENTS.register("bloodthirsty", () -> new Legacy("bloodthirsty", 3, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
        VALUES.put("clean_water", ENCHANTMENTS.register("clean_water", () -> new Legacy("clean_water", 4, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
        VALUES.put("life_tree", ENCHANTMENTS.register("life_tree", () -> new Legacy("life_tree", 4, EnchantmentCategory.ARMOR, new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})));
        VALUES.put("devoured", ENCHANTMENTS.register("devoured", () -> new Legacy("devoured", 4, EnchantmentCategory.VANISHABLE, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
        VALUES.put("frantic", ENCHANTMENTS.register("frantic", () -> new Legacy("frantic", 1, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
        VALUES.put("execute", ENCHANTMENTS.register("execute", () -> new Legacy("execute", 3, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
        VALUES.put("ranger", ENCHANTMENTS.register("ranger", () -> new Legacy("ranger", 1, EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
        VALUES.put("moon", ENCHANTMENTS.register("moon", () -> new Legacy("moon", 4, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
        VALUES.put("sun", ENCHANTMENTS.register("sun", () -> new Legacy("sun", 4, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND})));
    }
    public static Enchantment get(String name) { return VALUES.get(name).get(); }
    private static class Legacy extends Enchantment {
        private final String name; private final int max;
        Legacy(String name, int max, EnchantmentCategory category, EquipmentSlot[] slots) { super(Rarity.RARE, category, slots); this.name=name; this.max=max; }
        public int getMaxLevel() { return max; }
        public boolean isTreasureOnly() { return true; }
        public boolean canEnchant(net.minecraft.world.item.ItemStack stack) {
            return name.equals("devoured") ? stack.getItem() instanceof net.minecraft.world.item.TieredItem || stack.getItem() instanceof net.minecraft.world.item.TridentItem : super.canEnchant(stack);
        }
        public int getMinCost(int level) { return switch(name) { case "counter_attack" -> 10+level*7; case "life_tree" -> 15+(level-1)*9; case "execute" -> 20; case "ranger" -> 12+(level-1)*20; default -> super.getMinCost(level); }; }
        public int getMaxCost(int level) { return switch(name) { case "counter_attack", "execute" -> 50; case "life_tree" -> super.getMinCost(level)+50; case "ranger" -> getMinCost(level)+50; default -> super.getMaxCost(level); }; }
        protected boolean checkCompatibility(Enchantment other) {
            if (name.equals("clean_water") && other instanceof DamageEnchantment) return false;
            if ((name.equals("sun") || name.equals("moon")) && other instanceof Legacy l && (l.name.equals("sun") || l.name.equals("moon"))) return false;
            return super.checkCompatibility(other);
        }
    }
}
