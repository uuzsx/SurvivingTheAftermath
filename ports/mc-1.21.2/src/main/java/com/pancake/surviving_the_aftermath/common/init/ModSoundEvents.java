package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;

public class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT, SurvivingTheAftermath.MOD_ID);

    public static final Supplier<SoundEvent> ORCHELIAS_VOX = register("orchelias_vox");
    private static Supplier<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(SurvivingTheAftermath.asResource(name)));
    }
}
