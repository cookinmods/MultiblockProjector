package com.multiblockprojector.test;

import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.MultiblockCategory;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockStructure;
import com.multiblockprojector.api.ProjectorAPI;
import com.multiblockprojector.api.SingleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = "multiblockprojector", bus = EventBusSubscriber.Bus.MOD)
public class TestMultiblockRegistrar {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterEvent event) {
        event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_furnace"),
                MultiblockDefinition.fixed(
                    Component.literal("Test Furnace"),
                    "multiblockprojector",
                    MultiblockCategory.GENERAL,
                    new BlockPos(3, 2, 3),
                    (variant, level) -> buildTestFurnace()
                )
            );

            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_tower"),
                MultiblockDefinition.fixed(
                    Component.literal("Test Tower"),
                    "multiblockprojector",
                    MultiblockCategory.GENERAL,
                    new BlockPos(1, 8, 1),
                    (variant, level) -> buildTestTower()
                )
            );
        });
    }

    private static MultiblockStructure buildTestFurnace() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x == 1 && z == 1) {
                    blocks.put(new BlockPos(x, 0, z), new SingleBlock(Blocks.FURNACE.defaultBlockState()));
                } else {
                    blocks.put(new BlockPos(x, 0, z), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));
                }
            }
        }
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if ((x == 0 || x == 2) && (z == 0 || z == 2)) {
                    blocks.put(new BlockPos(x, 1, z), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));
                }
            }
        }
        return new MultiblockStructure(blocks);
    }

    private static MultiblockStructure buildTestTower() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        for (int y = 0; y < 8; y++) {
            blocks.put(new BlockPos(0, y, 0), new SingleBlock(Blocks.COBBLESTONE.defaultBlockState()));
        }
        return new MultiblockStructure(blocks);
    }
}
