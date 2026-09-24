package com.pancake.surviving_the_aftermath.client;

import net.minecraft.client.sounds.AudioStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;

/** A new listener joins the current song position rather than starting the recording again. */
public final class OffsetAudioStream implements AudioStream {
    private final AudioStream source;
    private ByteBuffer remainder;
    public OffsetAudioStream(AudioStream source, int elapsedTicks) throws IOException {
        this.source = source;
        AudioFormat format = source.getFormat();
        long bytes = (long) (format.getFrameRate() * elapsedTicks / 20.0) * format.getFrameSize();
        while (bytes > 0) {
            ByteBuffer chunk = source.read((int) Math.min(bytes, 16384));
            if (!chunk.hasRemaining()) break;
            int consumed = (int) Math.min(bytes, chunk.remaining());
            chunk.position(chunk.position() + consumed);
            bytes -= consumed;
            if (chunk.hasRemaining()) remainder = chunk;
        }
    }
    @Override public AudioFormat getFormat() { return source.getFormat(); }
    @Override public ByteBuffer read(int expectedSize) throws IOException {
        if (remainder != null) { ByteBuffer result = remainder; remainder = null; return result; }
        return source.read(expectedSize);
    }
    @Override public void close() throws IOException { source.close(); }
}
