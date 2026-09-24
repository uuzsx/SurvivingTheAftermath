package com.pancake.surviving_the_aftermath.common.item;

import com.pancake.surviving_the_aftermath.common.util.NetherCoreLocator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;

public class NetherCoreItem extends Item {
    public static final String TOOLTIP = "item.surviving_the_aftermath.nether_core.tooltip";
    public NetherCoreItem(Properties properties) { super(properties); }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) NetherCoreLocator.use(serverPlayer, hand);
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
