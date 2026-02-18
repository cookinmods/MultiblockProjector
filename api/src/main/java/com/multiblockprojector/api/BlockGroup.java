package com.multiblockprojector.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A block entry that accepts any of several block types.
 * Cycles through options in the preview renderer.
 *
 * <p>Note: equality is identity-based on block states.
 * Do not rely on {@code equals()} for deduplication.</p>
 */
public record BlockGroup(Component label, List<BlockState> options) implements BlockEntry {
    @Override
    public BlockState displayState(long tick) {
        return options.get((int) ((tick / 20) % options.size()));
    }

    @Override
    public boolean matches(BlockState placed) {
        return options.stream().anyMatch(o -> placed.is(o.getBlock()));
    }
}
