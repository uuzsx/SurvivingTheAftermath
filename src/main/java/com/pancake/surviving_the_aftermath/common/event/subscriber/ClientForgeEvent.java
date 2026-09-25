package com.pancake.surviving_the_aftermath.common.event.subscriber;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.client.ClientAftermathBars;
import com.pancake.surviving_the_aftermath.client.RaidBarText;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = SurvivingTheAftermath.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvent {
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientAftermathBars.clear(); com.pancake.surviving_the_aftermath.client.ClientRaidMusic.clear(); }

    @SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) com.pancake.surviving_the_aftermath.client.ClientRaidMusic.tick();
    }

    @SubscribeEvent
    public static void netherRaidProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent bossEvent = event.getBossEvent();
        var aftermath = ClientAftermathBars.get(bossEvent.getId());
        if (aftermath != null) {
            var graphics = event.getGuiGraphics();
            ResourceLocation resource = aftermath.texture();
            int[] offset = aftermath.offsets();

            if (resource == null || offset == null || offset.length != 6) {
                return;
            }

            int frameWidth = offset[0];
            int frameHeight = offset[1];
            int barWidth = offset[2];
            int barHeight = offset[3];
            int frameOffset = offset[4];
            int barOffset = offset[5];

            var font = net.minecraft.client.Minecraft.getInstance().font;
            var labels = RaidBarText.split(bossEvent.getName());
            int titleY = Math.max(4, event.getY() - 10);
            int frameY = labels == null ? event.getY() - 10 : titleY + font.lineHeight + 3;
            int waveY = frameY + frameHeight + 2;

            // Keep the frame and fill aligned while reserving both text rows.
            //渲染进度条框
            graphics.blit(resource, (graphics.guiWidth() - frameWidth) / 2, frameY,
                    0, frameOffset, frameWidth, frameHeight);
            //渲染进度条
            graphics.blit(resource, (graphics.guiWidth() - barWidth) / 2, frameY + barOffset,
                    0, 0, (int) (barWidth * event.getBossEvent().getProgress()), barHeight);
            if (labels != null) {
                graphics.drawCenteredString(font, labels.difficulty(), graphics.guiWidth() / 2, titleY,
                        RaidBarText.difficultyColor(System.nanoTime() / 1_000_000L));
                graphics.drawCenteredString(font, labels.wave(), graphics.guiWidth() / 2, waveY, 0xFFFFFFFF);
                // The next boss bar starts below the wave label, including its own title space.
                event.setIncrement(waveY + font.lineHeight + 6 + 10 - event.getY());
            } else {
                graphics.drawCenteredString(font, bossEvent.getName(), graphics.guiWidth() / 2, event.getY() + 2, 0xFFFFFFFF);
                event.setIncrement(frameHeight);
            }
            event.setCanceled(true);
        }
    }
}
