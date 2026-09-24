package com.pancake.surviving_the_aftermath.common.mixin;

import com.pancake.surviving_the_aftermath.common.util.RaidMobLoot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Slime.class)
public abstract class SlimeMixin {
    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean aftermath$inheritDungeonIdentity(Level level, Entity child) {
        if (child instanceof Mob mob) RaidMobLoot.inherit((Slime) (Object) this, mob);
        return level.addFreshEntity(child);
    }
}
