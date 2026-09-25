package com.pancake.surviving_the_aftermath.common.event.subscriber;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.client.ClientAftermathBars;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;


@net.neoforged.fml.common.EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientForgeEvent {
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientAftermathBars.clear(); com.pancake.surviving_the_aftermath.client.ClientRaidMusic.clear(); }

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        com.pancake.surviving_the_aftermath.client.ClientRaidMusic.tick();
    }

    @SubscribeEvent
    public static void netherRaidProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bossEvent = event.getBossEvent();
        var aftermath = ClientAftermathBars.get(bossEvent.getId());
        if (aftermath != null) {
            var graphics = event.getGuiGraphics();
            ResourceLocation resource = aftermath.texture();
            int[] offset = aftermath.offsets();

            if (resource == null || offset == null) {
                return;
            }

            int frameWidth = offset[0];
            int frameHeight = offset[1];
            int barWidth = offset[2];
            int barHeight = offset[3];
            int frameOffset = offset[4];
            int barOffset = offset[5];

            //渲染进度条框
            graphics.blit(resource, (graphics.guiWidth() - frameWidth) / 2, event.getY() - 10,
                    0, frameOffset, frameWidth, frameHeight);
            //渲染进度条
            graphics.blit(resource, (graphics.guiWidth() - barWidth) / 2, event.getY() - 10 + barOffset,
                    0, 0, (int) (barWidth * event.getBossEvent().getProgress()), barHeight);
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,
                    bossEvent.getName(), graphics.guiWidth() / 2, event.getY() + 2, 0xFFFFFFFF);
            event.setIncrement(frameHeight);
            event.setCanceled(true);
        }
    }
}
