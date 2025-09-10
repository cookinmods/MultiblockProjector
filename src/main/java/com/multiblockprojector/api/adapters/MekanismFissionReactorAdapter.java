// TEMPORARILY DISABLED FOR DEBUGGING - FOCUS ON OTHER MODS FIRST
/*
package com.multiblockprojector.api.adapters;

import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MekanismFissionReactorAdapter {
    
    public static void registerFissionReactorVariants() {
        try {
            System.out.println("Registering Mekanism Fission Reactor variants...");
            
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(3, 3, 3));
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(5, 5, 5));
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(7, 7, 7));
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(9, 9, 9));
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(11, 11, 11));
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(13, 13, 13));
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(15, 15, 15));
            UniversalMultiblockHandler.registerMultiblock(new FissionReactorMultiblock(17, 17, 17));
            
        } catch (Exception e) {
            System.err.println("Failed to register fission reactor variants: " + e.getMessage());
            throw new RuntimeException("Failed to register fission reactor variants", e);
        }
    }
    
    private static class FissionReactorMultiblock implements IUniversalMultiblock {
        private final int width, height, depth;
        private final String name;
        private final List<StructureBlockInfo> structure;
        
        public FissionReactorMultiblock(int width, int height, int depth) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.name = String.format("fission_reactor_%dx%dx%d", width, height, depth);
            this.structure = createFissionReactorStructure(width, height, depth);
        }
        
        private List<StructureBlockInfo> createFissionReactorStructure(int width, int height, int depth) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            
            try {
                Map<String, BlockState> mekanismBlocks = getMekanismBlocks();
                
                if (mekanismBlocks.isEmpty()) {
                    System.out.println("No Mekanism blocks available, using placeholder blocks");
                    return createPlaceholderStructure(width, height, depth);
                }
                
                BlockState casingBlock = mekanismBlocks.get("fission_reactor_casing");
                BlockState portBlock = mekanismBlocks.get("fission_reactor_port");
                BlockState logicAdapterBlock = mekanismBlocks.get("fission_reactor_logic_adapter");
                
                if (casingBlock == null || portBlock == null || logicAdapterBlock == null) {
                    System.out.println("Missing required Mekanism blocks, using placeholder structure");
                    return createPlaceholderStructure(width, height, depth);
                }
                
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        for (int z = 0; z < depth; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            
                            boolean isCorner = (x == 0 || x == width - 1) && (z == 0 || z == depth - 1);
                            boolean isEdge = x == 0 || x == width - 1 || y == 0 || y == height - 1 || z == 0 || z == depth - 1;
                            boolean isFace = (x == 0 || x == width - 1) || (y == 0 || y == height - 1) || (z == 0 || z == depth - 1);
                            
                            if (isEdge && !isCorner) {
                                if (y == 0 && x == width / 2 && z == 0) {
                                    blocks.add(new StructureBlockInfo(pos, portBlock, null));
                                } else if (y == 0 && x == 0 && z == depth / 2) {
                                    blocks.add(new StructureBlockInfo(pos, logicAdapterBlock, null));
                                } else if (isFace) {
                                    blocks.add(new StructureBlockInfo(pos, casingBlock, null));
                                }
                            } else if (isCorner && y == 0) {
                                blocks.add(new StructureBlockInfo(pos, casingBlock, null));
                            }
                        }
                    }
                }
                
                System.out.println("Created fission reactor structure (" + width + "x" + height + "x" + depth + ") with " + blocks.size() + " blocks");
                
            } catch (Exception e) {
                System.err.println("Error creating fission reactor structure: " + e.getMessage());
                return createPlaceholderStructure(width, height, depth);
            }
            
            return blocks;
        }
        
        private List<StructureBlockInfo> createPlaceholderStructure(int width, int height, int depth) {
            List<StructureBlockInfo> blocks = new ArrayList<>();
            
            try {
                Class<?> blocksClass = Class.forName("net.minecraft.world.level.block.Blocks");
                Object ironBlock = blocksClass.getField("IRON_BLOCK").get(null);
                
                if (ironBlock instanceof Block block) {
                    BlockState state = block.defaultBlockState();
                    
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            for (int z = 0; z < depth; z++) {
                                boolean isEdge = x == 0 || x == width-1 || y == 0 || y == height-1 || z == 0 || z == depth-1;
                                if (isEdge) {
                                    blocks.add(new StructureBlockInfo(
                                        new BlockPos(x, y, z),
                                        state,
                                        null
                                    ));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to create placeholder structure: " + e.getMessage());
            }
            
            return blocks;
        }
        
        private Map<String, BlockState> getMekanismBlocks() {
            Map<String, BlockState> blocks = new HashMap<>();
            
            try {
                Class<?> mekanismBlocksClass = Class.forName("mekanism.common.registries.MekanismBlocks");
                
                Object casingField = getFieldValue(mekanismBlocksClass, "FISSION_REACTOR_CASING");
                Object portField = getFieldValue(mekanismBlocksClass, "FISSION_REACTOR_PORT");
                Object logicField = getFieldValue(mekanismBlocksClass, "FISSION_REACTOR_LOGIC_ADAPTER");
                
                if (casingField != null && portField != null && logicField != null) {
                    blocks.put("fission_reactor_casing", getBlockState(casingField));
                    blocks.put("fission_reactor_port", getBlockState(portField));
                    blocks.put("fission_reactor_logic_adapter", getBlockState(logicField));
                }
                
            } catch (Exception e) {
                System.out.println("Could not load Mekanism blocks via reflection: " + e.getMessage());
            }
            
            return blocks;
        }
        
        private Object getFieldValue(Class<?> clazz, String fieldName) {
            try {
                return clazz.getField(fieldName).get(null);
            } catch (Exception e) {
                return null;
            }
        }
        
        private BlockState getBlockState(Object blockObject) {
            try {
                if (blockObject.getClass().getSimpleName().contains("BlockRegistryObject")) {
                    Object block = blockObject.getClass().getMethod("get").invoke(blockObject);
                    if (block instanceof Block) {
                        return ((Block) block).defaultBlockState();
                    }
                } else if (blockObject instanceof Block) {
                    return ((Block) blockObject).defaultBlockState();
                }
            } catch (Exception e) {
                System.err.println("Failed to get block state: " + e.getMessage());
            }
            return null;
        }
        
        @Override
        public ResourceLocation getUniqueName() {
            return ResourceLocation.fromNamespaceAndPath("mekanism", name);
        }
        
        @Override
        public Component getDisplayName() {
            return Component.literal(String.format("Fission Reactor (%dx%dx%d)", width, height, depth));
        }
        
        @Override
        public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
            return new ArrayList<>(structure);
        }
        
        @Override
        public Vec3i getSize(@Nonnull Level world) {
            return new Vec3i(width, height, depth);
        }
        
        @Override
        public float getManualScale() {
            if (width >= 15) return 0.3f;
            if (width >= 11) return 0.4f;
            if (width >= 7) return 0.6f;
            if (width >= 5) return 0.8f;
            return 1.0f;
        }
        
        @Override
        public String getModId() {
            return "mekanism";
        }
        
        @Override
        public String getCategory() {
            return "power";
        }
    }
}
*/