package com.multiblockprojector.api;

import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Extended interface for multiblocks that support variable sizes.
 * Allows selection between predefined size presets.
 */
public interface IVariableSizeMultiblock extends IUniversalMultiblock {

    /**
     * @return List of available size presets for this multiblock
     */
    List<SizePreset> getSizePresets();

    /**
     * Get the structure at a specific size.
     * @param world The level
     * @param size The desired size (from a SizePreset)
     * @return Block structure at the specified size
     */
    List<StructureBlockInfo> getStructureAtSize(@Nonnull Level world, Vec3i size);

    /**
     * @return true since this is a variable-size multiblock
     */
    default boolean isVariableSize() {
        return true;
    }

    /**
     * Default implementation returns structure at first (smallest) preset size
     */
    @Override
    default List<StructureBlockInfo> getStructure(@Nonnull Level world) {
        List<SizePreset> presets = getSizePresets();
        if (presets.isEmpty()) {
            return List.of();
        }
        return getStructureAtSize(world, presets.get(0).size());
    }

    /**
     * Default implementation returns first (smallest) preset size
     */
    @Override
    default Vec3i getSize(@Nonnull Level world) {
        List<SizePreset> presets = getSizePresets();
        if (presets.isEmpty()) {
            return new Vec3i(1, 1, 1);
        }
        return presets.get(0).size();
    }

    /**
     * Represents a predefined size option for a variable-size multiblock.
     */
    record SizePreset(String name, Vec3i size, Component displayName) {

        /**
         * Convenience constructor that creates display name from name
         */
        public SizePreset(String name, Vec3i size) {
            this(name, size, Component.translatable("gui.multiblockprojector.size." + name.toLowerCase()));
        }

        /**
         * @return Human-readable size string (e.g., "3x4x3")
         */
        public String getSizeString() {
            return size.getX() + "x" + size.getY() + "x" + size.getZ();
        }

        /**
         * @return Full display text with dimensions (e.g., "Small (3x4x3)")
         */
        public Component getFullDisplayName() {
            return Component.literal(displayName.getString() + " (" + getSizeString() + ")");
        }
    }
}
