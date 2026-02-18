package com.multiblockprojector.common.network;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.items.ProjectorItem;
import com.multiblockprojector.common.projector.MultiblockProjection;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

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
        // Only allow in creative mode
        if (!player.isCreative()) {
            return;
        }

        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof ProjectorItem)) {
            return;
        }

        Settings settings = ProjectorItem.getSettings(stack);
        if (settings.getMode() != Settings.Mode.PROJECTION || settings.getMultiblock() == null) {
            return;
        }

        // Perform auto-build on server side
        performAutoBuild(player, settings, stack, packet.buildPos);
    }

    private static void performAutoBuild(Player player, Settings settings, ItemStack held, BlockPos pos) {
        // Create the projection to get block positions and states
        MultiblockDefinition multiblock = settings.getMultiblock();
        var variant = MultiblockProjection.getVariantFromSettings(multiblock, settings);
        MultiblockProjection projection = new MultiblockProjection(player.level(), multiblock, variant);
        projection.setRotation(settings.getRotation());
        projection.setFlip(settings.isMirrored());

        Level level = player.level();
        List<BlockPos> failedPlacements = new ArrayList<>();
        final int[] blocksPlaced = {0};

        // Process all layers and place blocks
        projection.processAll((layer, info) -> {
            BlockPos worldPos = pos.offset(info.tPos);
            // Use tick=0 to get the first/default display state for placement
            BlockState targetState = info.getDisplayState(level, worldPos, 0);

            // Check if we can place the block here
            if (level.isInWorldBounds(worldPos) && level.getWorldBorder().isWithinBounds(worldPos)) {
                try {
                    // Force place the block (server-side)
                    level.setBlock(worldPos, targetState, 3); // Flag 3 = update + notify clients

                    // Trigger block updates to ensure proper multiblock formation
                    level.updateNeighborsAt(worldPos, targetState.getBlock());

                    blocksPlaced[0]++;
                } catch (Exception e) {
                    failedPlacements.add(worldPos);
                }
            } else {
                failedPlacements.add(worldPos);
            }

            return false; // Continue processing all blocks
        });

        if (!failedPlacements.isEmpty()) {
            // Some blocks couldn't be placed - show warning
            player.displayClientMessage(
                Component.literal("Auto-build failed! " + failedPlacements.size() + " blocks couldn't be placed.")
                    .withStyle(net.minecraft.ChatFormatting.RED),
                true
            );
        } else {
            // Success! Update settings and return to nothing selected mode
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setPos(null);
            settings.setPlaced(false);
            settings.applyTo(held);

            // Show success message
            player.displayClientMessage(
                Component.literal("Auto-build completed! Placed " + blocksPlaced[0] + " blocks.")
                    .withStyle(net.minecraft.ChatFormatting.GREEN),
                true
            );
        }
    }
}
