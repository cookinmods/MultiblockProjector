package com.multiblockprojector.api;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entry that enforces an empty (air) position.
 * Any block placed here is considered incorrect.
 */
public record AirEntry() implements BlockEntry {
    @Override
    public BlockState displayState(long tick) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean matches(BlockState placed) {
        return placed.isAir();
    }
}
