package com.multiblockprojector.common.projector;

import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.IVariableSizeMultiblock;
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
import java.util.ArrayList;
import java.util.List;
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
    final IUniversalMultiblock multiblock;
    final Level realWorld;
    final Level templateWorld;
    final StructurePlaceSettings settings = new StructurePlaceSettings();
    final Int2ObjectMap<List<StructureTemplate.StructureBlockInfo>> layers = new Int2ObjectArrayMap<>();
    final BlockPos.MutableBlockPos offset = new BlockPos.MutableBlockPos();
    final int blockcount;
    final Vec3i customSize; // For variable-size multiblocks
    boolean isDirty = true;

    public MultiblockProjection(@Nonnull Level world, @Nonnull IUniversalMultiblock multiblock) {
        this(world, multiblock, null);
    }

    /**
     * Create a projection with an optional custom size for variable-size multiblocks.
     * @param world The world
     * @param multiblock The multiblock to project
     * @param customSize For variable-size multiblocks, the specific size to use. Null for default size.
     */
    public MultiblockProjection(@Nonnull Level world, @Nonnull IUniversalMultiblock multiblock, Vec3i customSize) {
        Objects.requireNonNull(world, "World cannot be null!");
        Objects.requireNonNull(multiblock, "Multiblock cannot be null!");

        this.multiblock = multiblock;
        this.realWorld = world;
        this.customSize = customSize;

        // Get structure at specific size for variable-size multiblocks
        List<StructureTemplate.StructureBlockInfo> blocks;
        if (customSize != null && multiblock instanceof IVariableSizeMultiblock varMultiblock) {
            blocks = varMultiblock.getStructureAtSize(world, customSize);
        } else {
            blocks = multiblock.getStructure(world);
        }

        // Create template world using IE's TemplateWorldCreator if available
        this.templateWorld = createTemplateWorld(blocks);

        this.blockcount = blocks.size();
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            int layer = info.pos().getY();
            List<StructureTemplate.StructureBlockInfo> list = this.layers.get(layer);
            if (list == null) {
                list = new ArrayList<>();
                this.layers.put(layer, list);
            }
            list.add(info);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Level createTemplateWorld(List<StructureTemplate.StructureBlockInfo> blocks) {
        try {
            // Try to use IE's TemplateWorldCreator if available
            Class<?> creatorClass = Class.forName("blusunrize.immersiveengineering.api.utils.TemplateWorldCreator");
            Object creator = creatorClass.getField("CREATOR").get(null);
            Object creatorInstance = creator.getClass().getMethod("getValue").invoke(creator);
            
            return (Level) creatorInstance.getClass()
                .getMethod("makeWorld", List.class, Predicate.class, net.minecraft.core.RegistryAccess.class)
                .invoke(creatorInstance, blocks, (Predicate<BlockPos>) pos -> true, realWorld.registryAccess());
        } catch (Exception e) {
            // Fallback: use real world for now - works for test multiblocks
            return realWorld;
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
        return this.layers.get(layer).size();
    }
    
    public Level getTemplateWorld() {
        return this.templateWorld;
    }
    
    public IUniversalMultiblock getMultiblock() {
        return this.multiblock;
    }

    /**
     * Helper to get the size Vec3i for a multiblock based on settings.
     * Returns null for non-variable-size multiblocks.
     */
    public static Vec3i getSizeFromSettings(IUniversalMultiblock multiblock, Settings settings) {
        if (multiblock instanceof IVariableSizeMultiblock varMultiblock) {
            var presets = varMultiblock.getSizePresets();
            int index = settings.getSizePresetIndex();
            if (!presets.isEmpty() && index >= 0 && index < presets.size()) {
                return presets.get(index).size();
            } else if (!presets.isEmpty()) {
                return presets.get(0).size();
            }
        }
        return null;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof MultiblockProjection other) {
            return this.multiblock.getUniqueName().equals(other.multiblock.getUniqueName()) &&
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
        
        List<StructureTemplate.StructureBlockInfo> blocks = this.layers.get(layer);
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
        
        for (StructureTemplate.StructureBlockInfo info : blocks) {
            if (predicate.test(new Info(this, info))) {
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
            List<StructureTemplate.StructureBlockInfo> blocks = this.layers.get(layer);
            for (StructureTemplate.StructureBlockInfo info : blocks) {
                if (predicate.test(layer, new Info(this, info))) {
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
        // Use custom size if set, otherwise get from multiblock
        Vec3i size = this.customSize != null ? this.customSize : this.multiblock.getSize(this.realWorld);
        
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
        
        /** The multiblock being processed */
        public final IUniversalMultiblock multiblock;
        
        /** Transformed Template Position */
        public final BlockPos tPos;
        
        public final Level templateWorld;
        
        public final StructureTemplate.StructureBlockInfo tBlockInfo;
        
        public Info(MultiblockProjection projection, StructureTemplate.StructureBlockInfo templateBlockInfo) {
            this.multiblock = projection.multiblock;
            this.templateWorld = projection.templateWorld;
            this.settings = projection.settings;
            this.tBlockInfo = templateBlockInfo;
            this.tPos = StructureTemplate.calculateRelativePosition(this.settings, templateBlockInfo.pos()).subtract(projection.offset);
        }
        
        /** Convenience method for getting the state with mirror and rotation already applied */
        public BlockState getModifiedState(Level realWorld, BlockPos realPos) {
            // Get the original block state from the template block info
            BlockState originalState = this.tBlockInfo.state();
            
            // Apply transformations
            return originalState
                    .mirror(this.settings.getMirror())
                    .rotate(realWorld, realPos, this.settings.getRotation());
        }
        
        public BlockState getRawState() {
            return this.templateWorld.getBlockState(this.tBlockInfo.pos());
        }
    }
}