package com.pancake.surviving_the_aftermath.common.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import java.util.List;

/** Unspawned entities retain their UUID, equipment and effects across a saved encounter. */
public record RaidEntranceState(List<CompoundTag> pending, int delay, int blockedTicks) {
    public static final RaidEntranceState EMPTY = new RaidEntranceState(List.of(), 0, 0);
    public static final Codec<RaidEntranceState> CODEC = RecordCodecBuilder.create(i -> i.group(
            CompoundTag.CODEC.listOf().fieldOf("pending").forGetter(RaidEntranceState::pending),
            Codec.intRange(0, 200).fieldOf("delay").forGetter(RaidEntranceState::delay),
            Codec.intRange(0, 200).fieldOf("blocked_ticks").forGetter(RaidEntranceState::blockedTicks)
    ).apply(i, RaidEntranceState::new));
    public RaidEntranceState {
        pending = pending.stream().map(CompoundTag::copy).toList();
    }
}
