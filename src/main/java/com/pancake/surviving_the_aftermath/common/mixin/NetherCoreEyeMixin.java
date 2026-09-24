package com.pancake.surviving_the_aftermath.common.mixin;

import com.pancake.surviving_the_aftermath.common.init.ModItems;
import com.pancake.surviving_the_aftermath.common.util.NetherCoreFlight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EyeOfEnder.class)
public abstract class NetherCoreEyeMixin extends Entity implements NetherCoreFlight {
    @Unique private Vec3 aftermath$destination;
    @Unique private int aftermath$age;
    @Unique private boolean aftermath$returnItem = true;

    protected NetherCoreEyeMixin(EntityType<?> type, Level level) { super(type, level); }

    @Override
    public void aftermath$launch(Vec3 destination, boolean returnItem) {
        Vec3 delta = destination.subtract(position());
        double distance = delta.horizontalDistance();
        aftermath$destination = distance > 12
                ? position().add(delta.x / distance * 12, 6, delta.z / distance * 12) : destination;
        aftermath$age = 0;
        aftermath$returnItem = returnItem;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void aftermath$tick(CallbackInfo ci) {
        var eye = (EyeOfEnder)(Object)this;
        if (!eye.getItem().is(ModItems.NETHER_CORE.get()) || !(level() instanceof ServerLevel server)) return;
        ci.cancel();
        if (isRemoved()) return;
        super.tick();
        // Never execute the vanilla random break/drop branch, including after a chunk reload.
        if (aftermath$destination == null || ++aftermath$age >= 60) {
            aftermath$land(server);
            return;
        }
        Vec3 delta = aftermath$destination.subtract(position());
        Vec3 movement = delta.lengthSqr() < 0.04 ? Vec3.ZERO : delta.normalize().scale(Math.min(0.45, delta.length() * 0.18));
        Vec3 next = position().add(movement);
        if (!server.hasChunk(net.minecraft.core.BlockPos.containing(next).getX() >> 4,
                net.minecraft.core.BlockPos.containing(next).getZ() >> 4)) {
            aftermath$land(server);
            return;
        }
        var hit = server.clip(new ClipContext(position(), next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            aftermath$land(server);
            return;
        }
        setDeltaMovement(movement);
        setPos(next);
    }

    @Unique private void aftermath$land(ServerLevel server) {
        if (aftermath$returnItem) {
            var drop = new ItemEntity(server, getX(), getY(), getZ(), ((EyeOfEnder)(Object)this).getItem().copyWithCount(1));
            drop.setDeltaMovement(Vec3.ZERO);
            drop.setDefaultPickUpDelay();
            drop.setUnlimitedLifetime();
            drop.setInvulnerable(true);
            // Spawn first, discard only after success. A cancelled spawn can safely retry next tick.
            if (!server.addFreshEntity(drop)) return;
        }
        discard();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void aftermath$save(ValueOutput output, CallbackInfo ci) {
        if (!((EyeOfEnder)(Object)this).getItem().is(ModItems.NETHER_CORE.get())) return;
        Vec3 target = aftermath$destination == null ? position() : aftermath$destination;
        output.putDouble("AftermathCoreX", target.x);
        output.putDouble("AftermathCoreY", target.y);
        output.putDouble("AftermathCoreZ", target.z);
        output.putInt("AftermathCoreAge", aftermath$age);
        output.putBoolean("AftermathCoreReturn", aftermath$returnItem);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void aftermath$load(ValueInput input, CallbackInfo ci) {
        aftermath$destination = new Vec3(input.getDoubleOr("AftermathCoreX", getX()),
                input.getDoubleOr("AftermathCoreY", getY()), input.getDoubleOr("AftermathCoreZ", getZ()));
        aftermath$age = Math.max(0, input.getIntOr("AftermathCoreAge", 60));
        aftermath$returnItem = input.getBooleanOr("AftermathCoreReturn", true);
    }
}
