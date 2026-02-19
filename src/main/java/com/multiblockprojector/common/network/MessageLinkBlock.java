package com.multiblockprojector.common.network;

import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.multiblockprojector.UniversalProjector.rl;

/**
 * Client→Server packet for linking energy sources or containers to fabricator items.
 */
public class MessageLinkBlock implements CustomPacketPayload {

    public static final Type<MessageLinkBlock> TYPE = new Type<>(rl("link_block"));

    public static final StreamCodec<FriendlyByteBuf, MessageLinkBlock> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, p -> p.targetPos,
            ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], Enum::ordinal), p -> p.hand,
            ByteBufCodecs.INT, p -> p.linkType,
            MessageLinkBlock::new
        );

    public static final int LINK_ENERGY = 0;
    public static final int LINK_CONTAINER = 1;

    private final BlockPos targetPos;
    private final InteractionHand hand;
    private final int linkType;

    public MessageLinkBlock(BlockPos targetPos, InteractionHand hand, int linkType) {
        this.targetPos = targetPos;
        this.hand = hand;
        this.linkType = linkType;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToServer(BlockPos targetPos, InteractionHand hand, int linkType) {
        PacketDistributor.sendToServer(new MessageLinkBlock(targetPos, hand, linkType));
    }

    public static void handleServerSide(MessageLinkBlock packet, Player player) {
        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof AbstractProjectorItem)) return;

        Settings settings = AbstractProjectorItem.getSettings(stack);
        ResourceLocation dim = player.level().dimension().location();

        if (packet.linkType == LINK_ENERGY) {
            BlockEntity be = player.level().getBlockEntity(packet.targetPos);
            if (be != null) {
                var energyCap = player.level().getCapability(Capabilities.EnergyStorage.BLOCK, packet.targetPos, null);
                if (energyCap != null) {
                    settings.setLinkedEnergy(packet.targetPos, dim);
                    settings.applyTo(stack);
                    player.displayClientMessage(
                        Component.literal("Linked to Energy Source at " +
                            packet.targetPos.getX() + ", " + packet.targetPos.getY() + ", " + packet.targetPos.getZ())
                            .withStyle(ChatFormatting.GREEN),
                        false
                    );
                    return;
                }
            }
            player.displayClientMessage(
                Component.literal("No energy source found at that position")
                    .withStyle(ChatFormatting.RED),
                false
            );
        } else if (packet.linkType == LINK_CONTAINER) {
            var itemCap = player.level().getCapability(Capabilities.ItemHandler.BLOCK, packet.targetPos, null);
            if (itemCap != null) {
                settings.setLinkedChest(packet.targetPos, dim);
                settings.applyTo(stack);
                player.displayClientMessage(
                    Component.literal("Linked to Container at " +
                        packet.targetPos.getX() + ", " + packet.targetPos.getY() + ", " + packet.targetPos.getZ())
                        .withStyle(ChatFormatting.GREEN),
                    false
                );
            } else {
                player.displayClientMessage(
                    Component.literal("No container found at that position")
                        .withStyle(ChatFormatting.RED),
                    false
                );
            }
        }
    }
}
