package com.pancake.surviving_the_aftermath.common.item;

import com.pancake.surviving_the_aftermath.api.AftermathManager;
import com.pancake.surviving_the_aftermath.common.raid.NetherRaid;
import com.pancake.surviving_the_aftermath.common.util.RaidPortal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class DiamondFlintAndSteelItem extends FlintAndSteelItem {
    public static final String REQUIRED = "message.surviving_the_aftermath.nether_raid.diamond_required";
    public static final String UNAVAILABLE = "message.surviving_the_aftermath.nether_raid.unavailable";
    public static final String TOOLTIP = "item.surviving_the_aftermath.diamond_flint_and_steel.tooltip";

    public DiamondFlintAndSteelItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos clicked = context.getClickedPos();
        BlockPos pos = level.getBlockState(clicked).is(Blocks.NETHER_PORTAL)
                ? clicked : clicked.relative(context.getClickedFace());
        if (!RaidPortal.isArena(level, pos) && !RaidPortal.isArena(level, clicked)) return super.useOn(context);
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) return InteractionResult.FAIL;
        PortalShape shape = PortalShape.findPortalShape(level, pos, PortalShape::isValid, Direction.Axis.X).orElse(null);
        if (shape == null || !AftermathManager.getInstance().create(new NetherRaid(level, pos), level, pos, player)) {
            player.displayClientMessage(Component.translatable(UNAVAILABLE), true);
            return InteractionResult.FAIL;
        }
        shape.createPortalBlocks(level);
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        context.getItemInHand().hurtAndBreak(1, player, context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(TOOLTIP).withStyle(ChatFormatting.GRAY));
    }
}
