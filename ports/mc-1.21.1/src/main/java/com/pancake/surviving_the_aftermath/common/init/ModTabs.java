package com.pancake.surviving_the_aftermath.common.init;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SurvivingTheAftermath.MOD_ID);
    public static final java.util.function.Supplier<CreativeModeTab> TAB = TABS.register("tab", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup." + SurvivingTheAftermath.MOD_ID))
        .icon(() -> ModItems.NETHER_CORE.get().getDefaultInstance())
        .displayItems((params, output) -> ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get()))).build());
}
