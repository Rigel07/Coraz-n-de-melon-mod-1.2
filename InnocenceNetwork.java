package com.corazondemelon.innocence;

import com.corazondemelon.CorazonDeMelon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class InnocenceNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CorazonDeMelon.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private InnocenceNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
    }

    public static void sendTo(ServerPlayer player, float xp) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncPacket(xp));
    }

    public record SyncPacket(float xp) {
        public static void encode(SyncPacket msg, FriendlyByteBuf buf) {
            buf.writeFloat(msg.xp);
        }

        public static SyncPacket decode(FriendlyByteBuf buf) {
            return new SyncPacket(buf.readFloat());
        }

        public static void handle(SyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientInnocenceData.update(msg.xp));
            ctx.get().setPacketHandled(true);
        }
    }
}
