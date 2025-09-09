package com.multiblockprojector.api;

import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Universal interface for multiblock structures from any mod
 * Acts as a wrapper around various mod-specific multiblock systems
 */
public interface IUniversalMultiblock {
    
    /**
     * @return Unique identifier for this multiblock
     */
    ResourceLocation getUniqueName();
    
    /**
     * @return Human-readable display name
     */
    Component getDisplayName();
    
    /**
     * @return List of blocks that make up this multiblock structure
     */
    List<StructureBlockInfo> getStructure(@Nonnull Level world);
    
    /**
     * @return Size of the multiblock in blocks (width, height, depth)
     */
    Vec3i getSize(@Nonnull Level world);
    
    /**
     * @return Scale factor for rendering in GUI/manual (default 1.0f)
     */
    default float getManualScale() {
        return 1.0f;
    }
    
    /**
     * @return The mod that provides this multiblock
     */
    String getModId();
    
    /**
     * @return Whether this multiblock can be mirrored
     */
    default boolean canBeMirrored() {
        return true;
    }
    
    /**
     * @return Category/type of this multiblock for organization
     */
    default String getCategory() {
        return "general";
    }
}