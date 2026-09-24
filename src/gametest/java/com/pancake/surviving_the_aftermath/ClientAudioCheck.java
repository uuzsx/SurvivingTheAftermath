package com.pancake.surviving_the_aftermath;

import com.pancake.surviving_the_aftermath.client.OffsetAudioStream;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Uses Minecraft's actual client decoder without opening a game window or an audio device. */
public final class ClientAudioCheck {
    private static AudioStream stream() throws Exception {
        return new JOrbisAudioStream(ClientAudioCheck.class.getResourceAsStream("/assets/surviving_the_aftermath/sounds/orchelias_vox.ogg"));
    }
    private static byte[] readAll(AudioStream stream) throws Exception {
        var output = new ByteArrayOutputStream();
        while (true) {
            ByteBuffer chunk = stream.read(16384);
            if (!chunk.hasRemaining()) break;
            byte[] bytes = new byte[chunk.remaining()];
            chunk.get(bytes); output.write(bytes);
        }
        return output.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        byte[] all;
        int frameSize;
        float frameRate;
        try (AudioStream input = stream()) {
            if (input.getFormat().getChannels() != 1) throw new AssertionError("Spatial recording must decode to mono");
            frameSize = input.getFormat().getFrameSize();
            frameRate = input.getFormat().getFrameRate();
            all = readAll(input);
        }
        for (int offset : new int[]{0, 1, 17, 2000, 5391, 6000}) {
            try (AudioStream input = new OffsetAudioStream(stream(), offset)) {
                int bytes = (int) Math.min(all.length, (long) (frameRate * offset / 20.0) * frameSize);
                if (!Arrays.equals(Arrays.copyOfRange(all, bytes, all.length), readAll(input))) {
                    throw new AssertionError("Client stream seek differed at tick " + offset);
                }
            }
        }
        System.out.println("CLIENT AUDIO CHECK PASSED: Minecraft mono decoding and six seek/end-of-stream cases");
    }
}
