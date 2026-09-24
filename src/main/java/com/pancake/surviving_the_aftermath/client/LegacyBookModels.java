package com.pancake.surviving_the_aftermath.client;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Items;
@net.neoforged.fml.common.EventBusSubscriber(modid=SurvivingTheAftermath.MOD_ID,bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD,value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class LegacyBookModels {
    private static final java.util.List<String> TEXTURES=java.util.List.of("counter_attack","bloodthirsty","clean_water","life_tree","devoured","frantic","execute","moon","sun");
    @net.neoforged.bus.api.SubscribeEvent
    public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(Items.ENCHANTED_BOOK,SurvivingTheAftermath.asResource("special"), (stack,world,entity,seed) -> {
                var enchantments=stack.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS);
                if(enchantments==null || enchantments.keySet().size()!=1) return 0;
                var id=enchantments.keySet().iterator().next().unwrapKey().orElseThrow().location();
                if(id==null || !id.getNamespace().equals(SurvivingTheAftermath.MOD_ID)) return 0;
                int index=TEXTURES.indexOf(id.getPath());
                return index<0 ? 0 : (index+1)/10F;
        }));
    }
}
