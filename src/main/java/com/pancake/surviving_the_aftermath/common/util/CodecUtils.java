package com.pancake.surviving_the_aftermath.common.util;


import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;


import java.util.*;

public class CodecUtils {
    public static net.minecraft.resources.Identifier modifierId(String name) {
        var id = net.minecraft.resources.Identifier.tryParse(name);
        return id != null ? id : com.pancake.surviving_the_aftermath.SurvivingTheAftermath.asResource("legacy/" + UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static <T> com.mojang.serialization.MapCodec<T> mapCodec(com.mojang.serialization.Codec<T> codec) {
        if (codec instanceof com.mojang.serialization.MapCodec.MapCodecCodec<T> map) return map.codec();
        return codec.fieldOf("value");
    }

    private static final Codec<UUID> UUID_STRING = Codec.STRING.comapFlatMap(value -> {
        try { return com.mojang.serialization.DataResult.success(UUID.fromString(value)); }
        catch (IllegalArgumentException exception) { return com.mojang.serialization.DataResult.error(() -> "Invalid UUID: " + value); }
    }, UUID::toString);
    // Write primitive keys for maps, while accepting the old {uuid: ...} list entries.
    public static final Codec<UUID> UUID_CODEC = Codec.either(UUID_STRING, UUID_STRING.fieldOf("uuid").codec())
            .xmap(either -> either.map(value -> value, value -> value), com.mojang.datafixers.util.Either::left);

    public static final Codec<AttributeModifier.Operation> ATTRIBUTE_MODIFIER_OPERATION_CODEC = Codec.INT
            .xmap(AttributeModifier.Operation.BY_ID::apply, AttributeModifier.Operation::id);
    public static final Codec<AttributeModifier> ATTRIBUTE_MODIFIER_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(CodecUtils::modifierId, Object::toString).fieldOf("name").forGetter(AttributeModifier::id),
            Codec.DOUBLE.fieldOf("amount").forGetter(AttributeModifier::amount),
            ATTRIBUTE_MODIFIER_OPERATION_CODEC.fieldOf("operation").forGetter(AttributeModifier::operation)
    ).apply(instance, AttributeModifier::new));

    public static final Codec<MobEffectInstance> MOB_EFFECT_INSTANCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(MobEffectInstance::getEffect),
            Codec.INT.fieldOf("duration").forGetter(MobEffectInstance::getDuration),
            Codec.INT.fieldOf("amplifier").forGetter(MobEffectInstance::getAmplifier)
    ).apply(instance, MobEffectInstance::new));



    //T
    public static <T> Codec<Set<T>> setOf(Codec<T> codec) {
        return Codec.list(codec).xmap(HashSet::new, ArrayList::new);
    }

    public static <K,V> Codec<Map<K,V>> mapOf(Codec<K> codec, Codec<V> codec2) {
        return Codec.unboundedMap(codec, codec2).xmap(HashMap::new, HashMap::new);
    }

}
