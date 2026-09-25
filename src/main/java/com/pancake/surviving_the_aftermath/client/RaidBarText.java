package com.pancake.surviving_the_aftermath.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Uses the structured boss title so language changes never affect the HUD layout. */
public final class RaidBarText {
    private static final String TITLE = "message.surviving_the_aftermath.nether_raid.wave";
    private static final String WAVE = "message.surviving_the_aftermath.nether_raid.wave_count";
    // Warm, readable highlights from the Nether Raid bar's red/orange/gold palette.
    private static final int[] COLORS = {0xFFEF6040, 0xFFFF9535, 0xFFFFCC55};
    private RaidBarText() {}

    public record Labels(Component difficulty, Component wave) {}

    public static Labels split(Component title) {
        if (!(title.getContents() instanceof TranslatableContents contents)
                || !TITLE.equals(contents.getKey()) || contents.getArgs().length != 3) return null;
        Object[] args = contents.getArgs();
        Component difficulty = args[0] instanceof Component component
                ? component : Component.literal(String.valueOf(args[0]));
        return new Labels(difficulty, Component.translatable(WAVE, args[1], args[2]));
    }

    public static int difficultyColor(long milliseconds) {
        double phase = Math.floorMod(milliseconds, 6000L) / 2000.0;
        int segment = (int) phase;
        double blend = (1 - Math.cos((phase - segment) * Math.PI)) / 2;
        int from = COLORS[segment], to = COLORS[(segment + 1) % COLORS.length];
        int red = mix(from >> 16 & 255, to >> 16 & 255, blend);
        int green = mix(from >> 8 & 255, to >> 8 & 255, blend);
        int blue = mix(from & 255, to & 255, blend);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int mix(int from, int to, double blend) {
        return (int) Math.round(from + (to - from) * blend);
    }
}
