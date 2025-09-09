package com.multiblockprojector.common.network;

import com.multiblockprojector.common.items.ProjectorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.multiblockprojector.UniversalProjector.rl;

/**
 * Network packet for synchronizing projector settings between client and server
 */
public class MessageProjectorSync implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<MessageProjectorSync> TYPE = 
        new CustomPacketPayload.Type<>(rl("projector_sync"));
    
    public static final StreamCodec<FriendlyByteBuf, MessageProjectorSync> STREAM_CODEC = 
        StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG, p -> p.settingsNbt,
            net.minecraft.network.codec.ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], Enum::ordinal), p -> p.hand,
            MessageProjectorSync::new
        );
    
    private final net.minecraft.nbt.CompoundTag settingsNbt;
    private final InteractionHand hand;
    
    public MessageProjectorSync(net.minecraft.nbt.CompoundTag settingsNbt, InteractionHand hand) {
        this.settingsNbt = settingsNbt;
        this.hand = hand;
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void sendToServer(Settings settings, InteractionHand hand) {
        PacketDistributor.sendToServer(new MessageProjectorSync(settings.toNbt(), hand));
    }
    
    public static void sendToClient(Player player, Settings settings, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new MessageProjectorSync(settings.toNbt(), hand));
        }
    }
    
    public static void handleServerSide(MessageProjectorSync packet, Player player) {
        ItemStack stack = player.getItemInHand(packet.hand);
        if (stack.getItem() instanceof ProjectorItem) {
            Settings settings = new Settings(packet.settingsNbt);
            settings.applyTo(stack);
        }
    }
    
    public static void handleClientSide(MessageProjectorSync packet, Player player) {
        ItemStack stack = player.getItemInHand(packet.hand);
        if (stack.getItem() instanceof ProjectorItem) {
            Settings settings = new Settings(packet.settingsNbt);
            settings.applyTo(stack);
        }
    }
}