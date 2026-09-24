package com.pancake.surviving_the_aftermath.common.network;
import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.client.ClientAftermathBars;
import com.pancake.surviving_the_aftermath.client.ClientRaidMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

public final class AftermathNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(BarPacket.TYPE, BarPacket.STREAM_CODEC,
            (packet, context) -> context.enqueueWork(() -> ClientAftermathBars.accept(packet)));
        event.registrar("2").playToClient(MusicPacket.TYPE, MusicPacket.STREAM_CODEC,
            (packet, context) -> context.enqueueWork(() -> ClientRaidMusic.accept(packet)));
    }
    public static void playMusic(ServerPlayer player, UUID id, BlockPos center, int elapsedTicks) {
        PacketDistributor.sendToPlayer(player, new MusicPacket(id, player.level().dimension().location(), center, elapsedTicks));
    }
    public static void stopMusic(ServerPlayer player, UUID id) {
        PacketDistributor.sendToPlayer(player, new MusicPacket(id, null, BlockPos.ZERO, 0));
    }
    public record MusicPacket(UUID id, ResourceLocation dimension, BlockPos center, int elapsedTicks) implements CustomPacketPayload {
        public static final Type<MusicPacket> TYPE = new Type<>(SurvivingTheAftermath.asResource("raid_music"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MusicPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> packet.encode(buffer), MusicPacket::decode);
        @Override public Type<MusicPacket> type() { return TYPE; }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(id); buffer.writeBoolean(dimension != null);
            if (dimension != null) { buffer.writeResourceLocation(dimension); buffer.writeBlockPos(center); buffer.writeVarInt(elapsedTicks); }
        }
        public static MusicPacket decode(FriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            return buffer.readBoolean() ? new MusicPacket(id, buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readVarInt())
                    : new MusicPacket(id, null, BlockPos.ZERO, 0);
        }
    }
    public static void sendBar(ServerPlayer player, UUID id, ResourceLocation texture, int[] offsets) {
        if (texture != null && offsets != null && offsets.length == 6)
            PacketDistributor.sendToPlayer(player, new BarPacket(id, texture, offsets.clone()));
    }
    public static void removeBar(ServerPlayer player, UUID id) {
        PacketDistributor.sendToPlayer(player, new BarPacket(id, null, new int[0]));
    }
    public record BarPacket(UUID id, ResourceLocation texture, int[] offsets) implements CustomPacketPayload {
        public static final Type<BarPacket> TYPE = new Type<>(SurvivingTheAftermath.asResource("boss_bar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BarPacket> STREAM_CODEC = StreamCodec.of((buffer, packet) -> packet.encode(buffer), BarPacket::decode);
        @Override public Type<BarPacket> type() { return TYPE; }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(id); buffer.writeBoolean(texture != null);
            if (texture != null) { buffer.writeResourceLocation(texture); buffer.writeVarIntArray(offsets); }
        }
        public static BarPacket decode(FriendlyByteBuf buffer) {
            UUID id = buffer.readUUID();
            return buffer.readBoolean() ? new BarPacket(id, buffer.readResourceLocation(), buffer.readVarIntArray(6))
                    : new BarPacket(id, null, new int[0]);
        }
    }
}
