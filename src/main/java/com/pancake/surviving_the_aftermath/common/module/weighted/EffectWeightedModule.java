package com.pancake.surviving_the_aftermath.common.module.weighted;

import com.mojang.serialization.Codec;
import com.pancake.surviving_the_aftermath.api.module.IWeightedModule;
import com.pancake.surviving_the_aftermath.common.init.ModAftermathModule;
import com.pancake.surviving_the_aftermath.common.util.CodecUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

public class EffectWeightedModule extends BaseWeightedModule<MobEffectInstance> {
    public static final String IDENTIFIER = "effect_weighted";

    public static final Codec<EffectWeightedModule> CODEC = Codec.list(Weighted.codec(CodecUtils.MOB_EFFECT_INSTANCE_CODEC))
            .xmap(EffectWeightedModule::new, EffectWeightedModule::getList);

    public EffectWeightedModule(List<Weighted<MobEffectInstance>> list) {
        super(list);
    }

    public EffectWeightedModule() {
    }

    @Override
    public Codec<? extends IWeightedModule<MobEffectInstance>> codec() {
        return CODEC;
    }

    @Override
    public IWeightedModule<MobEffectInstance> type() {
        return ModAftermathModule.EFFECT_WEIGHTED.get();
    }

    public static class Builder {
        private List<Weighted<MobEffectInstance>> effects;

        public Builder add(MobEffectInstance effect, int weight){
            effects.add(new Weighted<>(effect, weight));
            return this;
        }
        public Builder add(String effect, int duration, int amplifier, int weight){
            effects.add(new Weighted<>(new MobEffectInstance(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect)).orElseThrow(), duration, amplifier), weight));
            return this;
        }

        public EffectWeightedModule build(){
            return new EffectWeightedModule(effects);
        }
    }
}
