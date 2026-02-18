package com.multiblockprojector.common.projector;

import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockDefinition.SizeVariant;
import com.multiblockprojector.api.MultiblockStructure;
import com.multiblockprojector.common.registry.MultiblockIndex;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Core projection system for multiblock structures
 * Ported from Immersive Petroleum and updated for 1.21.1 NeoForge
 *
 * @author Original: TwistedGate, Port: UniversalProjector Team
 */
public class MultiblockProjection {

    /** Simple inner record for layer storage. */
    record BlockInfo(BlockPos pos, BlockEntry entry) {}

    final MultiblockDefinition multiblock;
    final Level realWorld;
    final SizeVariant resolvedVariant;
    final StructurePlaceSettings settings = new StructurePlaceSettings();
    final Int2ObjectMap<List<BlockInfo>> layers = new Int2ObjectArrayMap<>();
    final BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos();
    final int blockcount;
    boolean isDirty = true;

    /**
     * Create a projection with an optional size variant for variable-size multiblocks.
     * @param world The world
     * @param definition The multiblock definition to project
     * @param variant For variable-size multiblocks, the specific variant to use. Null for default variant.
     */
    public MultiblockProjection(@Nonnull Level world, @Nonnull MultiblockDefinition definition, @Nullable SizeVariant variant) {
        Objects.requireNonNull(world, "World cannot be null!");
        Objects.requireNonNull(definition, "Multiblock definition cannot be null!");

        this.multiblock = definition;
        this.realWorld = world;
        this.resolvedVariant = variant != null ? variant : definition.getDefaultVariant();

        // Get structure from the definition's structure provider
        MultiblockStructure structure = definition.structureProvider().create(resolvedVariant, world);

        // Organize blocks into layers by Y coordinate
        Map<BlockPos, BlockEntry> blocks = structure.blocks();
        this.blockcount = blocks.size();
        for (Map.Entry<BlockPos, BlockEntry> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockEntry blockEntry = entry.getValue();
            int layer = pos.getY();
            this.layers.computeIfAbsent(layer, k -> new ArrayList<>()).add(new BlockInfo(pos, blockEntry));
        }
    }

    public MultiblockProjection setRotation(Rotation rotation) {
        if (this.settings.getRotation() != rotation) {
            this.settings.setRotation(rotation);
            this.isDirty = true;
        }
        return this;
    }

    /**
     * Sets the mirrored state.
     * true = Mirror.FRONT_BACK, false = Mirror.NONE
     */
    public MultiblockProjection setFlip(boolean mirror) {
        Mirror m = mirror ? Mirror.FRONT_BACK : Mirror.NONE;
        if (this.settings.getMirror() != m) {
            this.settings.setMirror(m);
            this.isDirty = true;
        }
        return this;
    }

    public void reset() {
        this.settings.setRotation(Rotation.NONE);
        this.settings.setMirror(Mirror.NONE);
        this.offset.set(0, 0, 0);
    }

    /** Total amount of blocks present in the multiblock */
    public int getBlockCount() {
        return this.blockcount;
    }

    /** Amount of layers in this projection */
    public int getLayerCount() {
        return this.layers.size();
    }

    public int getLayerSize(int layer) {
        if (layer < 0 || layer >= this.layers.size()) {
            return 0;
        }
        List<BlockInfo> list = this.layers.get(layer);
        return list != null ? list.size() : 0;
    }

    public MultiblockDefinition getMultiblock() {
        return this.multiblock;
    }

    /**
     * Helper to get the SizeVariant for a multiblock based on settings.
     * Returns null for non-variable-size multiblocks.
     */
    @Nullable
    public static SizeVariant getVariantFromSettings(MultiblockDefinition definition, Settings settings) {
        if (definition.isVariableSize()) {
            var variants = definition.variants();
            int index = settings.getSizePresetIndex();
            if (!variants.isEmpty() && index >= 0 && index < variants.size()) {
                return variants.get(index);
            } else if (!variants.isEmpty()) {
                return variants.get(0);
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof MultiblockProjection other) {
            // Compare by registry ID if available, fall back to displayName
            var index = MultiblockIndex.get();
            var thisId = index.getId(this.multiblock);
            var otherId = index.getId(other.multiblock);
            boolean sameMultiblock;
            if (thisId.isPresent() && otherId.isPresent()) {
                sameMultiblock = thisId.get().equals(otherId.get());
            } else {
                sameMultiblock = this.multiblock.displayName().getString()
                    .equals(other.multiblock.displayName().getString());
            }
            return sameMultiblock &&
                   this.settings.getMirror() == other.settings.getMirror() &&
                   this.settings.getRotation() == other.settings.getRotation();
        }
        return false;
    }

    /**
     * Single-Layer based projection processing
     */
    public boolean process(int layer, Predicate<Info> predicate) {
        updateData();

        List<BlockInfo> blocks = this.layers.get(layer);
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }

        for (BlockInfo blockInfo : blocks) {
            if (predicate.test(new Info(this, blockInfo))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Multi-Layer based projection processing
     */
    public boolean processAll(BiPredicate<Integer, Info> predicate) {
        updateData();

        for (int layer = 0; layer < getLayerCount(); layer++) {
            List<BlockInfo> blocks = this.layers.get(layer);
            if (blocks == null) continue;
            for (BlockInfo blockInfo : blocks) {
                if (predicate.test(layer, new Info(this, blockInfo))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateData() {
        if (!this.isDirty) return;
        this.isDirty = false;

        boolean mirrored = this.settings.getMirror() == Mirror.FRONT_BACK;
        Rotation rotation = this.settings.getRotation();
        // Use size from the resolved variant
        BlockPos dimensions = this.resolvedVariant.dimensions();
        Vec3i size = dimensions;

        // Align corners first
        if (!mirrored) {
            switch (rotation) {
                case CLOCKWISE_90 -> this.offset.set(1 - size.getZ(), 0, 0);
                case CLOCKWISE_180 -> this.offset.set(1 - size.getX(), 0, 1 - size.getZ());
                case COUNTERCLOCKWISE_90 -> this.offset.set(0, 0, 1 - size.getX());
                default -> this.offset.set(0, 0, 0);
            }
        } else {
            switch (rotation) {
                case NONE -> this.offset.set(1 - size.getX(), 0, 0);
                case CLOCKWISE_90 -> this.offset.set(1 - size.getZ(), 0, 1 - size.getX());
                case CLOCKWISE_180 -> this.offset.set(0, 0, 1 - size.getZ());
                default -> this.offset.set(0, 0, 0);
            }
        }

        // Center the whole thing
        int x = ((rotation.ordinal() % 2 == 0) ? size.getX() : size.getZ()) / 2;
        int z = ((rotation.ordinal() % 2 == 0) ? size.getZ() : size.getX()) / 2;
        this.offset.setWithOffset(this.offset, x, 0, z);
    }

    public static final class Info {
        /** Currently applied template transformation */
        public final StructurePlaceSettings settings;

        /** The multiblock definition being processed */
        public final MultiblockDefinition multiblock;

        /** Transformed Template Position */
        public final BlockPos tPos;

        /** The block entry at this position */
        public final BlockEntry blockEntry;

        /** The original structure position */
        public final BlockPos structurePos;

        public Info(MultiblockProjection projection, BlockInfo blockInfo) {
            this.multiblock = projection.multiblock;
            this.settings = projection.settings;
            this.blockEntry = blockInfo.entry();
            this.structurePos = blockInfo.pos();
            this.tPos = StructureTemplate.calculateRelativePosition(this.settings, blockInfo.pos()).subtract(projection.offset);
        }

        /**
         * Returns the display state for this block at the given tick, with mirror and rotation applied.
         */
        public BlockState getDisplayState(Level realWorld, BlockPos realPos, long tick) {
            BlockState originalState = this.blockEntry.displayState(tick);

            // Apply transformations
            return originalState
                    .mirror(this.settings.getMirror())
                    .rotate(realWorld, realPos, this.settings.getRotation());
        }

        /**
         * Returns true if the placed block state satisfies this entry's requirement.
         */
        public boolean matches(BlockState placed) {
            return this.blockEntry.matches(placed);
        }
    }
}
