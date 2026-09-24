package com.pancake.surviving_the_aftermath.client;

import com.pancake.surviving_the_aftermath.common.init.ModSoundEvents;
import com.pancake.surviving_the_aftermath.common.network.AftermathNetwork.MusicPacket;
import com.pancake.surviving_the_aftermath.common.util.RaidMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ClientRaidMusic {
    private static final Map<UUID, BuildingSong> PLAYING = new HashMap<>();

    public static void accept(MusicPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (packet.dimension() == null) {
            BuildingSong old = PLAYING.remove(packet.id());
            if (old != null) { old.finish(); client.getSoundManager().stop(old); }
            return;
        }
        if (PLAYING.containsKey(packet.id()) || client.level == null
                || !client.level.dimension().location().equals(packet.dimension())
                || packet.elapsedTicks() < 0 || packet.elapsedTicks() >= RaidMusic.DURATION_TICKS) return;
        BuildingSong song = new BuildingSong(client.level, packet);
        PLAYING.put(packet.id(), song);
        client.getSoundManager().play(song);
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        PLAYING.values().removeIf(song -> {
            if (client.level != song.level || song.isStopped()) {
                song.finish(); client.getSoundManager().stop(song); return true;
            }
            return song.age > 20 && !client.getSoundManager().isActive(song);
        });
    }

    public static void clear() {
        var sounds = Minecraft.getInstance().getSoundManager();
        PLAYING.values().forEach(song -> { song.finish(); sounds.stop(song); });
        PLAYING.clear();
    }

    private static final class BuildingSong extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final int offset;
        private int age;

        private BuildingSong(ClientLevel level, MusicPacket packet) {
            super(ModSoundEvents.ORCHELIAS_VOX.get(), SoundSource.RECORDS, RandomSource.create());
            this.level = level;
            offset = packet.elapsedTicks();
            x = packet.center().getX() + 0.5;
            y = packet.center().getY() + 0.5;
            z = packet.center().getZ() + 0.5;
            volume = 1.0F;
            relative = false;
            looping = false;
            attenuation = Attenuation.LINEAR;
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (Minecraft.getInstance().level != level || ++age + offset >= RaidMusic.DURATION_TICKS) stop();
        }
        private void finish() { stop(); }

        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return buffers.getStream(sound.getPath(), false).thenApply(stream -> {
                try { return offset == 0 ? stream : new OffsetAudioStream(stream, offset); }
                catch (IOException error) {
                    try { stream.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
                    throw new CompletionException(error);
                }
            });
        }
    }

}
