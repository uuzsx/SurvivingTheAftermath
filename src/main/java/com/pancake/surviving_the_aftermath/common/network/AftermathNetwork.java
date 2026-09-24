package com.pancake.surviving_the_aftermath.common.network;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.client.ClientAftermathBars;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class AftermathNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SurvivingTheAftermath.asResource("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, BarPacket.class, BarPacket::encode, BarPacket::decode, BarPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void sendBar(ServerPlayer player, UUID id, ResourceLocation texture, int[] offsets) {
        if (texture != null && offsets != null && offsets.length == 6)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BarPacket(id, texture, offsets.clone()));
    }
    public static void removeBar(ServerPlayer player, UUID id) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BarPacket(id, null, new int[0]));
    }
    public record BarPacket(UUID id, ResourceLocation texture, int[] offsets) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(id);
            buffer.writeBoolean(texture != null);
            if (texture != null) { buffer.writeResourceLocation(texture); buffer.writeVarIntArray(offsets); }
        }
        public static BarPacket decode(FriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            return buffer.readBoolean() ? new BarPacket(id, buffer.readResourceLocation(), buffer.readVarIntArray(6))
                    : new BarPacket(id, null, new int[0]);
        }
        public static void handle(BarPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAftermathBars.accept(packet)));
            context.setPacketHandled(true);
        }
    }
}
