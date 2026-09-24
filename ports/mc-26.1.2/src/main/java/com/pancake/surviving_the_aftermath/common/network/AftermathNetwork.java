package com.pancake.surviving_the_aftermath.common.network;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.client.ClientAftermathBars;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

public final class AftermathNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(BarPacket.TYPE, BarPacket.STREAM_CODEC,
            (packet, context) -> context.enqueueWork(() -> ClientAftermathBars.accept(packet)));
    }
    public static void sendBar(ServerPlayer player, UUID id, Identifier texture, int[] offsets) {
        if (texture != null && offsets != null && offsets.length == 6)
            PacketDistributor.sendToPlayer(player, new BarPacket(id, texture, offsets.clone()));
    }
    public static void removeBar(ServerPlayer player, UUID id) {
        PacketDistributor.sendToPlayer(player, new BarPacket(id, null, new int[0]));
    }
    public record BarPacket(UUID id, Identifier texture, int[] offsets) implements CustomPacketPayload {
        public static final Type<BarPacket> TYPE = new Type<>(SurvivingTheAftermath.asResource("boss_bar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BarPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> packet.encode(buffer), BarPacket::decode);
        @Override public Type<BarPacket> type() { return TYPE; }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(id); buffer.writeBoolean(texture != null);
            if (texture != null) { buffer.writeIdentifier(texture); buffer.writeVarIntArray(offsets); }
        }
        public static BarPacket decode(FriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            return buffer.readBoolean() ? new BarPacket(id, buffer.readIdentifier(), buffer.readVarIntArray(6))
                    : new BarPacket(id, null, new int[0]);
        }
    }
}
