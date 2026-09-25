package com.pancake.surviving_the_aftermath.common.item;

import com.pancake.surviving_the_aftermath.common.util.NetherCoreLocator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public class NetherCoreItem extends Item {
    public NetherCoreItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) NetherCoreLocator.use(serverPlayer, hand);
        return InteractionResult.SUCCESS;
    }

}
