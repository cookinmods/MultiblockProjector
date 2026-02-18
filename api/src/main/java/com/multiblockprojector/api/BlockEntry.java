package com.multiblockprojector.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a block requirement at a position in a multiblock structure.
 * Sealed to {@link SingleBlock} (exact block) and {@link BlockGroup} (any of several blocks).
 */
public sealed interface BlockEntry permits SingleBlock, BlockGroup {
    /**
     * Returns the block state to display at the given game tick.
     * For {@link SingleBlock}, always returns the same state.
     * For {@link BlockGroup}, cycles through options.
     */
    BlockState displayState(long tick);

    /**
     * Returns true if the placed block state satisfies this entry.
     */
    boolean matches(BlockState placed);
}
