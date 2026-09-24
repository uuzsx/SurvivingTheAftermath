package com.pancake.surviving_the_aftermath.common.util;

import com.pancake.surviving_the_aftermath.common.init.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class RaidPortal {
    private RaidPortal() {}

    public static boolean isArena(ServerLevel level, BlockPos pos) {
        return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(ModStructures.NETHER_RAID)).isValid();
    }
}
