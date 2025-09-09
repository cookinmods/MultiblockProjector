package com.multiblockprojector.api;

import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.core.BlockPos;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple test multiblock for development purposes
 */
public class TestMultiblock implements IUniversalMultiblock {
    
    public static final TestMultiblock SIMPLE_FURNACE = new TestMultiblock(
        ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_furnace"),
        "Test Furnace",
        3, 2, 3
    );
    
    public static final TestMultiblock SMALL_TOWER = new TestMultiblock(
        ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_tower"),
        "Test Tower", 
        1, 5, 1
    );
    
    private final ResourceLocation name;
    private final String displayName;
    private final int width, height, depth;
    
    public TestMultiblock(ResourceLocation name, String displayName, int width, int height, int depth) {
        this.name = name;
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.depth = depth;
    }
    
    @Override
    public ResourceLocation getUniqueName() {
        return name;
    }
    
    @Override
    public Component getDisplayName() {
        return Component.literal(displayName);
    }
    
    @Override
    public List<StructureBlockInfo> getStructure(@Nonnull Level world) {
        List<StructureBlockInfo> blocks = new ArrayList<>();
        
        if (name.getPath().equals("test_furnace")) {
            // Create a simple 3x2x3 furnace-like structure
            // Bottom layer
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos pos = new BlockPos(x, 0, z);
                    if (x == 1 && z == 1) {
                        // Center: furnace
                        blocks.add(new StructureBlockInfo(pos, Blocks.FURNACE.defaultBlockState(), null));
                    } else {
                        // Edges: stone bricks
                        blocks.add(new StructureBlockInfo(pos, Blocks.STONE_BRICKS.defaultBlockState(), null));
                    }
                }
            }
            // Top layer
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if ((x == 0 || x == 2) && (z == 0 || z == 2)) {
                        BlockPos pos = new BlockPos(x, 1, z);
                        blocks.add(new StructureBlockInfo(pos, Blocks.STONE_BRICKS.defaultBlockState(), null));
                    }
                }
            }
        } else if (name.getPath().equals("test_tower")) {
            // Create a simple 1x5x1 tower
            for (int y = 0; y < 5; y++) {
                BlockPos pos = new BlockPos(0, y, 0);
                if (y == 0 || y == 4) {
                    blocks.add(new StructureBlockInfo(pos, Blocks.STONE_BRICKS.defaultBlockState(), null));
                } else {
                    blocks.add(new StructureBlockInfo(pos, Blocks.STONE.defaultBlockState(), null));
                }
            }
        }
        
        return blocks;
    }
    
    @Override
    public Vec3i getSize(@Nonnull Level world) {
        return new Vec3i(width, height, depth);
    }
    
    @Override
    public String getModId() {
        return "multiblockprojector";
    }
    
    @Override
    public String getCategory() {
        return "test";
    }
    
    @Override
    public float getManualScale() {
        return 1.0f;
    }
    
    public static void registerTestMultiblocks() {
        UniversalMultiblockHandler.registerMultiblock(SIMPLE_FURNACE);
        UniversalMultiblockHandler.registerMultiblock(SMALL_TOWER);
    }
}