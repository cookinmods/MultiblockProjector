package com.multiblockprojector.api;

import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * The block layout of a multiblock structure.
 * Size is auto-computed from the block map bounding box.
 */
public record MultiblockStructure(Map<BlockPos, BlockEntry> blocks, BlockPos size) {

    /**
     * Convenience constructor that auto-computes size from block positions.
     */
    public MultiblockStructure(Map<BlockPos, BlockEntry> blocks) {
        this(Map.copyOf(blocks), computeBounds(blocks));
    }

    private static BlockPos computeBounds(Map<BlockPos, BlockEntry> blocks) {
        int maxX = 0, maxY = 0, maxZ = 0;
        for (BlockPos pos : blocks.keySet()) {
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        return new BlockPos(maxX, maxY, maxZ);
    }
}
