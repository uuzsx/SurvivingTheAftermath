package com.pancake.surviving_the_aftermath.common.capability;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.api.IAftermath;
import com.pancake.surviving_the_aftermath.common.init.ModCapability;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AftermathCap {
    private static final AftermathManager AFTERMATH_MANAGER = AftermathManager.getInstance();
    private final ServerLevel level;

    public AftermathCap(ServerLevel level) { this.level = level;}

    public CompoundTag serializeNBT() {
        CompoundTag compoundTag = new CompoundTag();
        AFTERMATH_MANAGER.getAftermathMap().forEach((uuid, aftermath) ->
                IAftermath.CODEC.get().encodeStart(NbtOps.INSTANCE, aftermath)
                .resultOrPartial(SurvivingTheAftermath.LOGGER::error)
                .ifPresent(tag -> {
                    compoundTag.put(uuid.toString(), tag);
                }));
        return compoundTag;
    }

    public void deserializeNBT(CompoundTag compoundTag) {
        for (String uuid : compoundTag.getAllKeys()) {
            CompoundTag tag = compoundTag.getCompound(uuid);
            try {
                AFTERMATH_MANAGER.create(level, UUID.fromString(uuid), tag);
            } catch (IllegalArgumentException exception) {
                SurvivingTheAftermath.LOGGER.error("Invalid saved aftermath id {}", uuid, exception);
            }
        }
    }

    public static Optional<AftermathCap> get(Level level) {
        return level instanceof ServerLevel && level.dimension() == Level.OVERWORLD
                ? Optional.of(level.getData(ModCapability.AFTERMATH_CAP)) : Optional.empty();
    }

    public void tick() {
        AFTERMATH_MANAGER.tick();
    }

}
