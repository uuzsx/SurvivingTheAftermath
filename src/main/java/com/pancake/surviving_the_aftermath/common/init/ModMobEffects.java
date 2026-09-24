package com.pancake.surviving_the_aftermath.common.init;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.effect.CowardiceEffect;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.Supplier;

public class ModMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT, SurvivingTheAftermath.MOD_ID);

    //懦弱
    public static final net.neoforged.neoforge.registries.DeferredHolder<MobEffect, MobEffect> COWARDICE = MOB_EFFECTS.register("cowardice", CowardiceEffect::new);
}
