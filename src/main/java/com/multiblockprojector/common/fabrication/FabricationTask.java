package com.multiblockprojector.common.fabrication;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.network.MessageFabricationProgress;
import com.multiblockprojector.common.projector.MultiblockProjection;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Server-side state machine for animated multiblock fabrication.
 * Places blocks one at a time with a configurable tick delay.
 */
public class FabricationTask {

    private static final int TICKS_PER_BLOCK = 1;

    private final UUID playerId;
    private final Level level;
    private final InteractionHand hand;
    private final List<PlacementEntry> queue;
    private int currentIndex = 0;
    private int tickCounter = 0;
    private boolean completed = false;

    public record PlacementEntry(BlockPos worldPos, BlockState state) {}

    public FabricationTask(ServerPlayer player, Level level, BlockPos origin, InteractionHand hand,
                           MultiblockDefinition multiblock, Settings settings) {
        this.playerId = player.getUUID();
        this.level = level;
        this.hand = hand;

        // Build the placement queue from the projection
        var variant = MultiblockProjection.getVariantFromSettings(multiblock, settings);
        MultiblockProjection projection = new MultiblockProjection(level, multiblock, variant);
        projection.setRotation(settings.getRotation());
        projection.setFlip(settings.isMirrored());

        List<PlacementEntry> entries = new ArrayList<>();
        projection.processAll((layer, info) -> {
            BlockPos worldPos = origin.offset(info.tPos);
            BlockState targetState = info.getDisplayState(level, worldPos, 0);
            if (!targetState.isAir()) {
                entries.add(new PlacementEntry(worldPos, targetState));
            }
            return false;
        });

        this.queue = sorted(entries);
    }

    /**
     * Constructor with pre-resolved placements (used by MBFs to place actual inventory blocks
     * instead of display states, supporting BlockGroup flexibility).
     */
    public FabricationTask(ServerPlayer player, Level level, InteractionHand hand,
                           List<PlacementEntry> resolvedPlacements) {
        this.playerId = player.getUUID();
        this.level = level;
        this.hand = hand;
        this.queue = sorted(resolvedPlacements);
    }

    private static List<PlacementEntry> sorted(List<PlacementEntry> entries) {
        // Sort bottom-to-top (ascending Y), then by X, then by Z within each layer
        entries.sort(Comparator.comparingInt((PlacementEntry e) -> e.worldPos.getY())
            .thenComparingInt(e -> e.worldPos.getX())
            .thenComparingInt(e -> e.worldPos.getZ()));
        return entries;
    }

    /**
     * Tick the task. Returns true if the task is complete.
     */
    public boolean tick(ServerPlayer player) {
        if (completed || currentIndex >= queue.size()) {
            complete(player);
            return true;
        }

        // If the next block's chunk is unloaded, finish everything instantly
        PlacementEntry next = queue.get(currentIndex);
        if (!level.isLoaded(next.worldPos)) {
            completeInstantly();
            complete(player);
            return true;
        }

        tickCounter++;
        if (tickCounter >= TICKS_PER_BLOCK) {
            tickCounter = 0;

            PlacementEntry entry = queue.get(currentIndex);
            if (level.isInWorldBounds(entry.worldPos) && level.getWorldBorder().isWithinBounds(entry.worldPos)) {
                level.setBlock(entry.worldPos, entry.state, 3);
                level.updateNeighborsAt(entry.worldPos, entry.state.getBlock());
            }
            currentIndex++;

            // Send progress update
            MessageFabricationProgress.sendToClient(player, currentIndex, queue.size());

            if (currentIndex >= queue.size()) {
                complete(player);
                return true;
            }
        }
        return false;
    }

    /**
     * Complete the task instantly (e.g., player logout).
     */
    public void completeInstantly() {
        for (int i = currentIndex; i < queue.size(); i++) {
            PlacementEntry entry = queue.get(i);
            if (level.isInWorldBounds(entry.worldPos) && level.getWorldBorder().isWithinBounds(entry.worldPos)) {
                level.setBlock(entry.worldPos, entry.state, 3);
                level.updateNeighborsAt(entry.worldPos, entry.state.getBlock());
            }
        }
        completed = true;
    }

    private void complete(ServerPlayer player) {
        if (!completed) {
            completed = true;

            // Reset the projector settings
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof AbstractProjectorItem) {
                Settings settings = AbstractProjectorItem.getSettings(stack);
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(stack);
            }

            player.displayClientMessage(
                Component.literal("Fabrication complete! Placed " + queue.size() + " blocks.")
                    .withStyle(ChatFormatting.GREEN),
                true
            );
        }
    }

    public UUID getPlayerId() { return playerId; }
    public boolean isCompleted() { return completed; }
    public int getTotalBlocks() { return queue.size(); }
}
