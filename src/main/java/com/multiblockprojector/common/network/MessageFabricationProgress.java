package com.multiblockprojector.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.multiblockprojector.UniversalProjector.rl;

public class MessageFabricationProgress implements CustomPacketPayload {

    public static final Type<MessageFabricationProgress> TYPE = new Type<>(rl("fabrication_progress"));

    public static final StreamCodec<FriendlyByteBuf, MessageFabricationProgress> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, p -> p.current,
            ByteBufCodecs.INT, p -> p.total,
            MessageFabricationProgress::new
        );

    private final int current;
    private final int total;

    public MessageFabricationProgress(int current, int total) {
        this.current = current;
        this.total = total;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void sendToClient(ServerPlayer player, int current, int total) {
        PacketDistributor.sendToPlayer(player, new MessageFabricationProgress(current, total));
    }

    public static void handleClientSide(MessageFabricationProgress packet, Player player) {
        player.displayClientMessage(
            Component.literal("Building... " + packet.current + "/" + packet.total + " blocks")
                .withStyle(net.minecraft.ChatFormatting.GOLD),
            true
        );
    }
}
