package com.multiblockprojector.client;

import com.multiblockprojector.common.projector.MultiblockProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side manager for tracking incorrect blocks during building mode
 */
public class BlockValidationManager {

    private static final Map<BlockPos, Set<BlockPos>> INCORRECT_BLOCKS = new HashMap<>();

    /**
     * Validate all blocks in a projection and mark incorrect ones
     */
    public static boolean validateProjection(BlockPos projectionCenter, MultiblockProjection projection, Level level) {
        Set<BlockPos> oldIncorrectBlocks = INCORRECT_BLOCKS.getOrDefault(projectionCenter, new HashSet<>());
        Set<BlockPos> incorrectBlocks = new HashSet<>();

        // Process each layer of the projection
        for (int layer = 0; layer < projection.getLayerCount(); layer++) {
            projection.process(layer, info -> {
                BlockPos worldPos = projectionCenter.offset(info.tPos);
                BlockState actualState = level.getBlockState(worldPos);

                // Don't validate air block entries
                BlockState displayState = info.getDisplayState(level, worldPos, 0);
                if (displayState.isAir()) {
                    return false;
                }

                // Check if the actual block matches the requirement
                boolean matches = info.matches(actualState);

                if (!matches) {
                    // Block is incorrect if it's not air and doesn't match
                    if (!actualState.isAir()) {
                        incorrectBlocks.add(worldPos.immutable());
                    }
                }

                return false; // Continue processing
            });
        }

        // Check if new incorrect blocks were added (blocks that weren't incorrect before)
        Set<BlockPos> newIncorrectBlocks = new HashSet<>(incorrectBlocks);
        newIncorrectBlocks.removeAll(oldIncorrectBlocks);
        boolean hasNewIncorrectBlocks = !newIncorrectBlocks.isEmpty();

        // Update the incorrect blocks map
        if (incorrectBlocks.isEmpty()) {
            INCORRECT_BLOCKS.remove(projectionCenter);
        } else {
            INCORRECT_BLOCKS.put(projectionCenter.immutable(), incorrectBlocks);
        }

        return hasNewIncorrectBlocks;
    }

    /**
     * Check if a specific block position is marked as incorrect
     */
    public static boolean isIncorrectBlock(BlockPos pos) {
        for (Set<BlockPos> incorrectSet : INCORRECT_BLOCKS.values()) {
            if (incorrectSet.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all incorrect blocks for a specific projection
     */
    public static Set<BlockPos> getIncorrectBlocks(BlockPos projectionCenter) {
        return INCORRECT_BLOCKS.getOrDefault(projectionCenter, new HashSet<>());
    }

    /**
     * Clear validation data for a specific projection
     */
    public static void clearValidation(BlockPos projectionCenter) {
        INCORRECT_BLOCKS.remove(projectionCenter);
    }

    /**
     * Clear all validation data
     */
    public static void clearAll() {
        INCORRECT_BLOCKS.clear();
    }

    /**
     * Check if a projection is complete (no incorrect blocks and all blocks placed)
     */
    public static boolean isProjectionComplete(BlockPos projectionCenter, MultiblockProjection projection, Level level) {
        // First check if there are any incorrect blocks
        Set<BlockPos> incorrectBlocks = getIncorrectBlocks(projectionCenter);
        if (!incorrectBlocks.isEmpty()) {
            return false;
        }

        // Check if all required blocks are placed
        for (int layer = 0; layer < projection.getLayerCount(); layer++) {
            boolean[] hasIncompleteBlocks = {false};

            projection.process(layer, info -> {
                BlockPos worldPos = projectionCenter.offset(info.tPos);
                BlockState actualState = level.getBlockState(worldPos);

                // Skip air block entries
                BlockState displayState = info.getDisplayState(level, worldPos, 0);
                if (displayState.isAir()) {
                    return false;
                }

                // Check if block is missing or incorrect
                boolean matches = info.matches(actualState);

                if (actualState.isAir() || !matches) {
                    hasIncompleteBlocks[0] = true;
                    return true; // Stop processing
                }

                return false; // Continue processing
            });

            if (hasIncompleteBlocks[0]) {
                return false;
            }
        }

        return true; // All blocks are correctly placed
    }
}
