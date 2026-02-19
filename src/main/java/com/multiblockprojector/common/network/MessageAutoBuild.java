package com.multiblockprojector.common.network;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.fabrication.FabricationManager;
import com.multiblockprojector.common.fabrication.FabricationTask;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.items.CreativeProjectorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.multiblockprojector.UniversalProjector.rl;

/**
 * Network packet for triggering auto-build on server side
 */
public class MessageAutoBuild implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MessageAutoBuild> TYPE =
        new CustomPacketPayload.Type<>(rl("auto_build"));

    public static final StreamCodec<FriendlyByteBuf, MessageAutoBuild> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, p -> p.buildPos,
            net.minecraft.network.codec.ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], Enum::ordinal), p -> p.hand,
            MessageAutoBuild::new
        );

    private final BlockPos buildPos;
    private final InteractionHand hand;

    public MessageAutoBuild(BlockPos buildPos, InteractionHand hand) {
        this.buildPos = buildPos;
        this.hand = hand;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void sendToServer(BlockPos buildPos, InteractionHand hand) {
        PacketDistributor.sendToServer(new MessageAutoBuild(buildPos, hand));
    }

    public static void handleServerSide(MessageAutoBuild packet, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof CreativeProjectorItem)) {
            return;
        }

        Settings settings = AbstractProjectorItem.getSettings(stack);
        if (settings.getMode() != Settings.Mode.PROJECTION || settings.getMultiblock() == null) {
            return;
        }

        if (FabricationManager.hasActiveTask(player.getUUID())) {
            player.displayClientMessage(
                Component.literal("A build is already in progress!")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        MultiblockDefinition multiblock = settings.getMultiblock();

        FabricationTask task = new FabricationTask(serverPlayer, player.level(), packet.buildPos, packet.hand, multiblock, settings);
        FabricationManager.addTask(serverPlayer, task);

        // Reset projector to default mode
        settings.setMode(Settings.Mode.NOTHING_SELECTED);
        settings.setPos(null);
        settings.setPlaced(false);
        settings.applyTo(stack);

        player.displayClientMessage(
            Component.literal("Building started! Placing " + task.getTotalBlocks() + " blocks...")
                .withStyle(ChatFormatting.GOLD), true);
    }
}
