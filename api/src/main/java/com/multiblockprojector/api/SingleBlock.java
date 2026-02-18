package com.multiblockprojector.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entry requiring an exact block type.
 */
public record SingleBlock(BlockState state) implements BlockEntry {
    @Override
    public BlockState displayState(long tick) {
        return state;
    }

    @Override
    public boolean matches(BlockState placed) {
        return placed.is(state.getBlock());
    }
}
