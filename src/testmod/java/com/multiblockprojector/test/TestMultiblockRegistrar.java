package com.multiblockprojector.test;

import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.BlockGroup;
import com.multiblockprojector.api.MultiblockCategory;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockDefinition.SizeVariant;
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
import java.util.List;
import java.util.Map;

/**
 * Test multiblocks that exercise every API feature.
 * Registered at LOWEST priority so they never conflict with real multiblocks.
 *
 * <p>Tests covered:
 * <ul>
 *   <li>{@code MultiblockDefinition.fixed()} — single-size convenience factory</li>
 *   <li>{@code MultiblockDefinition} constructor — variable-size with multiple SizeVariants</li>
 *   <li>{@code SingleBlock} — exact block matching</li>
 *   <li>{@code BlockGroup} — accepts-any-of matching with cycling preview</li>
 *   <li>{@code MultiblockStructure} auto-computed size</li>
 *   <li>{@code MultiblockCategory} — predefined and custom categories</li>
 *   <li>{@code SizeVariant.isDefault} — default variant selection</li>
 * </ul>
 */
@EventBusSubscriber(modid = "multiblockprojector", bus = EventBusSubscriber.Bus.MOD)
public class TestMultiblockRegistrar {

    /** Custom category to verify third-party categories work. */
    private static final MultiblockCategory TEST_CATEGORY = new MultiblockCategory(
        ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test"),
        Component.literal("Test")
    );

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterEvent event) {
        event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {

            // --- 1. Fixed-size with SingleBlock (simplest possible usage) ---
            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_furnace"),
                MultiblockDefinition.fixed(
                    Component.literal("Test Furnace"),
                    "multiblockprojector",
                    MultiblockCategory.PROCESSING,
                    new BlockPos(3, 2, 3),
                    (variant, level) -> buildTestFurnace()
                )
            );

            // --- 2. Fixed-size with BlockGroup (cycling preview) ---
            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_beacon"),
                MultiblockDefinition.fixed(
                    Component.literal("Test Beacon"),
                    "multiblockprojector",
                    MultiblockCategory.POWER,
                    new BlockPos(3, 2, 3),
                    (variant, level) -> buildTestBeacon()
                )
            );

            // --- 3. Variable-size with default variant ---
            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_tower"),
                new MultiblockDefinition(
                    Component.literal("Test Tower"),
                    "multiblockprojector",
                    MultiblockCategory.GENERAL,
                    List.of(
                        new SizeVariant(Component.literal("Short"), new BlockPos(1, 4, 1), false),
                        new SizeVariant(Component.literal("Medium"), new BlockPos(1, 8, 1), true),
                        new SizeVariant(Component.literal("Tall"), new BlockPos(1, 12, 1), false)
                    ),
                    (variant, level) -> buildTestTower(variant.dimensions().getY())
                )
            );

            // --- 4. Variable-size with BlockGroup + custom category ---
            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_wall"),
                new MultiblockDefinition(
                    Component.literal("Test Wall"),
                    "multiblockprojector",
                    TEST_CATEGORY,
                    List.of(
                        new SizeVariant(Component.literal("Small"), new BlockPos(3, 3, 1), false),
                        new SizeVariant(Component.literal("Medium"), new BlockPos(5, 4, 1), true),
                        new SizeVariant(Component.literal("Large"), new BlockPos(7, 5, 1), false)
                    ),
                    (variant, level) -> buildTestWall(
                        variant.dimensions().getX(),
                        variant.dimensions().getY()
                    )
                )
            );
        });
    }

    /** 3x2x3 furnace surrounded by stone bricks. Tests {@link SingleBlock}. */
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

    /**
     * 3x2x3 beacon base with cycling mineral blocks.
     * Tests {@link BlockGroup} — base accepts any of iron/gold/diamond/emerald blocks.
     */
    private static MultiblockStructure buildTestBeacon() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();

        BlockGroup mineralBlock = new BlockGroup(
            Component.literal("Mineral Block"),
            List.of(
                Blocks.IRON_BLOCK.defaultBlockState(),
                Blocks.GOLD_BLOCK.defaultBlockState(),
                Blocks.DIAMOND_BLOCK.defaultBlockState(),
                Blocks.EMERALD_BLOCK.defaultBlockState()
            )
        );

        // 3x3 base of mineral blocks
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.put(new BlockPos(x, 0, z), mineralBlock);
            }
        }

        // Beacon on top center
        blocks.put(new BlockPos(1, 1, 1), new SingleBlock(Blocks.BEACON.defaultBlockState()));

        return new MultiblockStructure(blocks);
    }

    /** Variable-height tower. Tests parameterized {@link MultiblockDefinition.StructureProvider}. */
    private static MultiblockStructure buildTestTower(int height) {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        for (int y = 0; y < height; y++) {
            blocks.put(new BlockPos(0, y, 0), new SingleBlock(Blocks.COBBLESTONE.defaultBlockState()));
        }
        return new MultiblockStructure(blocks);
    }

    /**
     * Variable-size wall with cycling border blocks.
     * Tests {@link BlockGroup} + variable sizing together.
     */
    private static MultiblockStructure buildTestWall(int width, int height) {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();

        BlockGroup borderBlock = new BlockGroup(
            Component.literal("Border"),
            List.of(
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
            )
        );

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean isBorder = x == 0 || x == width - 1 || y == 0 || y == height - 1;
                if (isBorder) {
                    blocks.put(new BlockPos(x, y, 0), borderBlock);
                } else {
                    blocks.put(new BlockPos(x, y, 0), new SingleBlock(Blocks.GLASS.defaultBlockState()));
                }
            }
        }

        return new MultiblockStructure(blocks);
    }
}
