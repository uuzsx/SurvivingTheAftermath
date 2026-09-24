package com.pancake.surviving_the_aftermath.common.mixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
 @Invoker("readAdditionalSaveData") void aftermath$readAdditional(ValueInput input);
}
