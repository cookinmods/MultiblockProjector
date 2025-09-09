package com.multiblockprojector.api.adapters;

import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Adapter for Immersive Engineering multiblocks
 */
public class IEMultiblockAdapter {
    
    /**
     * Register all IE multiblocks with the universal handler
     */
    public static void registerIEMultiblocks() {
        try {
            // Use reflection to avoid compile-time dependency
            Class<?> handlerClass = Class.forName("blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler");
            Object getMultiblocksMethod = handlerClass.getMethod("getMultiblocks").invoke(null);
            
            @SuppressWarnings("unchecked")
            List<Object> ieMultiblocks = (List<Object>) getMultiblocksMethod;
            
            for (Object ieMultiblock : ieMultiblocks) {
                IUniversalMultiblock universal = new IEMultiblockWrapper(ieMultiblock);
                UniversalMultiblockHandler.registerMultiblock(universal);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to load IE multiblocks", e);
        }
    }
    
    /**
     * Wrapper class for IE multiblocks using reflection
     */
    private static class IEMultiblockWrapper implements IUniversalMultiblock {
        private final Object ieMultiblock;
        
        public IEMultiblockWrapper(Object ieMultiblock) {
            this.ieMultiblock = ieMultiblock;
        }
        
        @Override
        public ResourceLocation getUniqueName() {
            try {
                return (ResourceLocation) ieMultiblock.getClass().getMethod("getUniqueName").invoke(ieMultiblock);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get unique name", e);
            }
        }
        
        @Override
        public Component getDisplayName() {
            try {
                return (Component) ieMultiblock.getClass().getMethod("getDisplayName").invoke(ieMultiblock);
            } catch (Exception e) {
                return Component.literal(getUniqueName().getPath());
            }
        }
        
        @Override
        @SuppressWarnings("unchecked")
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            try {
                return (List<StructureBlockInfo>) ieMultiblock.getClass()
                    .getMethod("getStructure", Level.class)
                    .invoke(ieMultiblock, world);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get structure", e);
            }
        }
        
        @Override
        public Vec3i getSize(@Nonnull Level world) {
            try {
                return (Vec3i) ieMultiblock.getClass()
                    .getMethod("getSize", Level.class)
                    .invoke(ieMultiblock, world);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get size", e);
            }
        }
        
        @Override
        public float getManualScale() {
            try {
                return (Float) ieMultiblock.getClass().getMethod("getManualScale").invoke(ieMultiblock);
            } catch (Exception e) {
                return 1.0f;
            }
        }
        
        @Override
        public String getModId() {
            return "immersiveengineering";
        }
        
        @Override
        public String getCategory() {
            // Categorize IE multiblocks based on their name
            String name = getUniqueName().getPath();
            if (name.contains("furnace") || name.contains("coke") || name.contains("alloy")) {
                return "processing";
            } else if (name.contains("generator") || name.contains("lightning")) {
                return "power";
            } else if (name.contains("assembler") || name.contains("press") || name.contains("workbench")) {
                return "crafting";
            } else if (name.contains("tank") || name.contains("silo")) {
                return "storage";
            } else if (name.contains("excavator") || name.contains("drill")) {
                return "mining";
            }
            return "general";
        }
        
        /**
         * Get the original IE multiblock object for advanced operations
         */
        public Object getOriginalMultiblock() {
            return ieMultiblock;
        }
    }
}