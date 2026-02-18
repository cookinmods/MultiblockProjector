package com.multiblockprojector.common.adapters;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.BlockGroup;
import com.multiblockprojector.api.MultiblockCategory;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockStructure;
import com.multiblockprojector.api.SingleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Adapter for Blood Magic altar tiers with cycling rune support.
 * Blood Magic uses a tiered altar system with runes, pillars, and capstones.
 * Rune positions use {@link BlockGroup} entries so the preview cycles through
 * all acceptable rune types for each tier.
 */
public class BloodMagicMultiblockAdapter {

    /**
     * A named definition pairing a registry ID with its definition.
     */
    public record NamedDefinition(ResourceLocation id, MultiblockDefinition definition) {}

    private static final MultiblockCategory ALTAR_CATEGORY = new MultiblockCategory(
        ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar"),
        Component.literal("Altar")
    );

    // Block states loaded via reflection from Blood Magic
    private static BlockState bloodAltarBlock;
    private static BlockState bloodstoneBlock;
    private static BlockState bloodstoneBrickBlock;
    private static BlockState hellforgedBlock;
    private static BlockState crystalClusterBlock;

    // All rune block states - basic runes (11 types)
    private static final List<BlockState> BASIC_RUNES = new ArrayList<>();
    private static final List<BlockState> ALL_RUNES = new ArrayList<>(); // Basic + Tier-2 (21 types)

    // Default rune (blank) for creative auto-build
    private static BlockState defaultRuneBlock;

    // Fallback blocks if reflection fails
    private static final BlockState FALLBACK_ALTAR = Blocks.OBSIDIAN.defaultBlockState();
    private static final BlockState FALLBACK_RUNE = Blocks.NETHER_BRICKS.defaultBlockState();
    private static final BlockState FALLBACK_PILLAR = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState FALLBACK_GLOWSTONE = Blocks.GLOWSTONE.defaultBlockState();
    private static final BlockState FALLBACK_BLOODSTONE = Blocks.RED_NETHER_BRICKS.defaultBlockState();
    private static final BlockState FALLBACK_HELLFORGED = Blocks.NETHERITE_BLOCK.defaultBlockState();
    private static final BlockState FALLBACK_CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();

    // Fallback runes for when Blood Magic isn't loaded
    private static final List<BlockState> FALLBACK_BASIC_RUNES = List.of(
        Blocks.NETHER_BRICKS.defaultBlockState(),
        Blocks.RED_NETHER_BRICKS.defaultBlockState(),
        Blocks.CHISELED_NETHER_BRICKS.defaultBlockState(),
        Blocks.CRACKED_NETHER_BRICKS.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
    );

    private static boolean blocksLoaded = false;

    // Basic rune field names (11 types)
    private static final String[] BASIC_RUNE_NAMES = {
        "RUNE_BLANK", "RUNE_SPEED", "RUNE_SACRIFICE", "RUNE_SELF_SACRIFICE",
        "RUNE_CAPACITY", "RUNE_CAPACITY_AUGMENTED", "RUNE_CHARGING",
        "RUNE_ACCELERATION", "RUNE_DISLOCATION", "RUNE_ORB", "RUNE_EFFICIENCY"
    };

    // Tier-2 rune field names (10 types - no RUNE_2_BLANK)
    private static final String[] TIER2_RUNE_NAMES = {
        "RUNE_2_SPEED", "RUNE_2_SACRIFICE", "RUNE_2_SELF_SACRIFICE",
        "RUNE_2_CAPACITY", "RUNE_2_CAPACITY_AUGMENTED", "RUNE_2_CHARGING",
        "RUNE_2_ACCELERATION", "RUNE_2_DISLOCATION", "RUNE_2_ORB", "RUNE_2_EFFICIENCY"
    };

    /**
     * Load Blood Magic blocks via reflection
     */
    private static void loadBlocks() {
        if (blocksLoaded) return;
        blocksLoaded = true;

        try {
            Class<?> bmBlocksClass = Class.forName("wayoftime.bloodmagic.common.block.BMBlocks");

            bloodAltarBlock = getBlockStateFromHolder(bmBlocksClass, "BLOOD_ALTAR");
            bloodstoneBlock = getBlockStateFromHolder(bmBlocksClass, "BLOODSTONE");
            bloodstoneBrickBlock = getBlockStateFromHolder(bmBlocksClass, "BLOODSTONE_BRICK");
            hellforgedBlock = getBlockStateFromHolder(bmBlocksClass, "HELLFORGED_BLOCK");
            crystalClusterBlock = getBlockStateFromHolder(bmBlocksClass, "CRYSTAL_CLUSTER");

            // Load all basic runes
            for (String runeName : BASIC_RUNE_NAMES) {
                BlockState runeState = getBlockStateFromHolder(bmBlocksClass, runeName);
                if (runeState != null) {
                    BASIC_RUNES.add(runeState);
                    ALL_RUNES.add(runeState);
                    if (runeName.equals("RUNE_BLANK")) {
                        defaultRuneBlock = runeState;
                    }
                }
            }

            // Load all tier-2 runes
            for (String runeName : TIER2_RUNE_NAMES) {
                BlockState runeState = getBlockStateFromHolder(bmBlocksClass, runeName);
                if (runeState != null) {
                    ALL_RUNES.add(runeState);
                }
            }

            UniversalProjector.LOGGER.info("Successfully loaded Blood Magic blocks: {} basic runes, {} total runes",
                BASIC_RUNES.size(), ALL_RUNES.size());
        } catch (Exception e) {
            UniversalProjector.LOGGER.warn("Failed to load Blood Magic blocks via reflection, using fallbacks: {}", e.getMessage());
            bloodAltarBlock = FALLBACK_ALTAR;
            bloodstoneBlock = FALLBACK_BLOODSTONE;
            bloodstoneBrickBlock = FALLBACK_PILLAR;
            hellforgedBlock = FALLBACK_HELLFORGED;
            crystalClusterBlock = FALLBACK_CRYSTAL;

            // Use fallback runes
            BASIC_RUNES.addAll(FALLBACK_BASIC_RUNES);
            ALL_RUNES.addAll(FALLBACK_BASIC_RUNES);
            defaultRuneBlock = FALLBACK_RUNE;
        }

        // Ensure we have at least one rune
        if (BASIC_RUNES.isEmpty()) {
            BASIC_RUNES.add(FALLBACK_RUNE);
            ALL_RUNES.add(FALLBACK_RUNE);
        }
        if (defaultRuneBlock == null) {
            defaultRuneBlock = BASIC_RUNES.get(0);
        }
    }

    /**
     * Get block state from a BlockWithItemHolder field
     */
    private static BlockState getBlockStateFromHolder(Class<?> bmBlocksClass, String fieldName) {
        try {
            Field field = bmBlocksClass.getField(fieldName);
            Object holder = field.get(null);

            // BlockWithItemHolder has a block() method that returns DeferredHolder<Block, ?>
            Method blockMethod = holder.getClass().getMethod("block");
            Object deferredHolder = blockMethod.invoke(holder);

            // DeferredHolder has a get() method
            Method getMethod = deferredHolder.getClass().getMethod("get");
            Block block = (Block) getMethod.invoke(deferredHolder);

            return block.defaultBlockState();
        } catch (Exception e) {
            UniversalProjector.LOGGER.debug("Failed to load Blood Magic block {}: {}", fieldName, e.getMessage());
            return null;
        }
    }

    // Helper methods to get the effective block states
    private static BlockState getAltarBlock() {
        return bloodAltarBlock != null ? bloodAltarBlock : FALLBACK_ALTAR;
    }

    private static BlockState getRuneBlock() {
        return defaultRuneBlock != null ? defaultRuneBlock : FALLBACK_RUNE;
    }

    private static BlockState getPillarBlock() {
        return bloodstoneBrickBlock != null ? bloodstoneBrickBlock : FALLBACK_PILLAR;
    }

    private static BlockState getT3Capstone() {
        return FALLBACK_GLOWSTONE; // Glowstone is always available
    }

    private static BlockState getT4Capstone() {
        return bloodstoneBlock != null ? bloodstoneBlock : FALLBACK_BLOODSTONE;
    }

    private static BlockState getT5Capstone() {
        return hellforgedBlock != null ? hellforgedBlock : FALLBACK_HELLFORGED;
    }

    private static BlockState getT6Capstone() {
        return crystalClusterBlock != null ? crystalClusterBlock : FALLBACK_CRYSTAL;
    }

    /**
     * Get runes for a specific altar tier.
     * Tier 2: Basic runes only (11)
     * Tier 3+: All runes (21)
     */
    private static List<BlockState> getRunesForTier(int tier) {
        if (tier <= 2) {
            return new ArrayList<>(BASIC_RUNES);
        } else {
            return new ArrayList<>(ALL_RUNES);
        }
    }

    /**
     * Discover all Blood Magic altar tiers and return them as named definitions.
     *
     * @return list of discovered Blood Magic multiblock definitions
     */
    public static List<NamedDefinition> discover() {
        List<NamedDefinition> results = new ArrayList<>();
        try {
            loadBlocks();

            results.add(createTier1());
            results.add(createTier2());
            results.add(createTier3());
            results.add(createTier4());
            results.add(createTier5());
            results.add(createTier6());

            UniversalProjector.LOGGER.info("Discovered 6 Blood Magic altar tiers");
        } catch (Exception e) {
            UniversalProjector.LOGGER.error("Failed to discover Blood Magic multiblocks", e);
        }
        return results;
    }

    // ============================================
    // Tier 1: Weak - Just the Blood Altar (no runes)
    // ============================================
    private static NamedDefinition createTier1() {
        return new NamedDefinition(
            ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_1"),
            MultiblockDefinition.fixed(
                Component.literal("Blood Altar - Tier 1 (Weak)"),
                "bloodmagic",
                ALTAR_CATEGORY,
                new BlockPos(1, 1, 1),
                (variant, level) -> {
                    Map<BlockPos, BlockEntry> blockMap = new LinkedHashMap<>();
                    blockMap.put(BlockPos.ZERO, new SingleBlock(getAltarBlock()));
                    return new MultiblockStructure(blockMap);
                }
            )
        );
    }

    // ============================================
    // Tier 2: Apprentice - Altar + 8 runes in 3x3 below
    // ============================================
    private static NamedDefinition createTier2() {
        int tier = 2;
        return new NamedDefinition(
            ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_2"),
            MultiblockDefinition.fixed(
                Component.literal("Blood Altar - Tier 2 (Apprentice)"),
                "bloodmagic",
                ALTAR_CATEGORY,
                new BlockPos(3, 2, 3),
                (variant, level) -> {
                    Map<BlockPos, BlockEntry> blockMap = new LinkedHashMap<>();

                    // Altar at center, y=1
                    blockMap.put(new BlockPos(1, 1, 1), new SingleBlock(getAltarBlock()));

                    // 8 runes in 3x3 pattern below altar (y=0), excluding center
                    for (int x = 0; x < 3; x++) {
                        for (int z = 0; z < 3; z++) {
                            if (x == 1 && z == 1) continue; // Skip center (below altar)
                            blockMap.put(new BlockPos(x, 0, z), new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    return new MultiblockStructure(blockMap);
                }
            )
        );
    }

    // ============================================
    // Tier 3: Mage - Larger rune ring + 4 pillars with glowstone caps
    // ============================================
    private static NamedDefinition createTier3() {
        int tier = 3;
        return new NamedDefinition(
            ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_3"),
            MultiblockDefinition.fixed(
                Component.literal("Blood Altar - Tier 3 (Mage)"),
                "bloodmagic",
                ALTAR_CATEGORY,
                new BlockPos(7, 4, 7),
                (variant, level) -> {
                    Map<BlockPos, BlockEntry> blockMap = new LinkedHashMap<>();
                    int centerX = 3, centerZ = 3, altarY = 2;

                    // Altar at center
                    blockMap.put(new BlockPos(centerX, altarY, centerZ), new SingleBlock(getAltarBlock()));

                    // 3x3 runes below altar (y=1)
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            blockMap.put(new BlockPos(centerX + dx, altarY - 1, centerZ + dz), new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Outer rune ring at y=0, distance 3
                    for (int i = -2; i <= 2; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                            new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                            new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                            new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // 4 pillars at corners with glowstone caps
                    int[][] pillarPositions = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
                    for (int[] pos : pillarPositions) {
                        for (int dy = -1; dy <= 0; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        // Glowstone cap on top
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                            new SingleBlock(getT3Capstone()));
                    }

                    return new MultiblockStructure(blockMap);
                }
            )
        );
    }

    // ============================================
    // Tier 4: Master - Even larger with outer pillars
    // ============================================
    private static NamedDefinition createTier4() {
        int tier = 4;
        return new NamedDefinition(
            ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_4"),
            MultiblockDefinition.fixed(
                Component.literal("Blood Altar - Tier 4 (Master)"),
                "bloodmagic",
                ALTAR_CATEGORY,
                new BlockPos(11, 6, 11),
                (variant, level) -> {
                    Map<BlockPos, BlockEntry> blockMap = new LinkedHashMap<>();
                    int centerX = 5, centerZ = 5, altarY = 3;

                    // Altar at center
                    blockMap.put(new BlockPos(centerX, altarY, centerZ), new SingleBlock(getAltarBlock()));

                    // Tier 2 runes (3x3 below altar)
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            blockMap.put(new BlockPos(centerX + dx, altarY - 1, centerZ + dz), new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 3 outer rune ring at distance 3
                    for (int i = -2; i <= 2; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                            new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                            new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                            new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 3 inner pillars with glowstone
                    int[][] innerPillars = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
                    for (int[] pos : innerPillars) {
                        for (int dy = -1; dy <= 0; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                            new SingleBlock(getT3Capstone()));
                    }

                    // Tier 4 outer rune ring at distance 5
                    for (int i = -3; i <= 3; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 3, centerZ + 5),
                            new BlockPos(centerX + i, altarY - 3, centerZ - 5),
                            new BlockPos(centerX + 5, altarY - 3, centerZ + i),
                            new BlockPos(centerX - 5, altarY - 3, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 4 outer pillars at distance 5
                    int[][] outerPillars = {{5, 5}, {5, -5}, {-5, 5}, {-5, -5}};
                    for (int[] pos : outerPillars) {
                        for (int dy = -2; dy <= 1; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        // Bloodstone cap
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 2, centerZ + pos[1]),
                            new SingleBlock(getT4Capstone()));
                    }

                    return new MultiblockStructure(blockMap);
                }
            )
        );
    }

    // ============================================
    // Tier 5: Archmage - Large ring with hellforged caps
    // ============================================
    private static NamedDefinition createTier5() {
        int tier = 5;
        return new NamedDefinition(
            ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_5"),
            MultiblockDefinition.fixed(
                Component.literal("Blood Altar - Tier 5 (Archmage)"),
                "bloodmagic",
                ALTAR_CATEGORY,
                new BlockPos(17, 7, 17),
                (variant, level) -> {
                    Map<BlockPos, BlockEntry> blockMap = new LinkedHashMap<>();
                    int centerX = 8, centerZ = 8, altarY = 4;

                    // Altar at center
                    blockMap.put(new BlockPos(centerX, altarY, centerZ), new SingleBlock(getAltarBlock()));

                    // Tier 2 runes (3x3 below altar)
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            blockMap.put(new BlockPos(centerX + dx, altarY - 1, centerZ + dz), new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 3 rune ring at distance 3
                    for (int i = -2; i <= 2; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                            new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                            new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                            new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 3 pillars with glowstone
                    int[][] tier3Pillars = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
                    for (int[] pos : tier3Pillars) {
                        for (int dy = -1; dy <= 0; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                            new SingleBlock(getT3Capstone()));
                    }

                    // Tier 4 rune ring at distance 5
                    for (int i = -3; i <= 3; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 3, centerZ + 5),
                            new BlockPos(centerX + i, altarY - 3, centerZ - 5),
                            new BlockPos(centerX + 5, altarY - 3, centerZ + i),
                            new BlockPos(centerX - 5, altarY - 3, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 4 pillars with bloodstone
                    int[][] tier4Pillars = {{5, 5}, {5, -5}, {-5, 5}, {-5, -5}};
                    for (int[] pos : tier4Pillars) {
                        for (int dy = -2; dy <= 1; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 2, centerZ + pos[1]),
                            new SingleBlock(getT4Capstone()));
                    }

                    // Tier 5 rune ring at distance 8
                    for (int i = -6; i <= 6; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 4, centerZ + 8),
                            new BlockPos(centerX + i, altarY - 4, centerZ - 8),
                            new BlockPos(centerX + 8, altarY - 4, centerZ + i),
                            new BlockPos(centerX - 8, altarY - 4, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 5 corner capstones (hellforged)
                    int[][] tier5Caps = {{8, 8}, {8, -8}, {-8, 8}, {-8, -8}};
                    for (int[] pos : tier5Caps) {
                        blockMap.put(new BlockPos(centerX + pos[0], altarY - 4, centerZ + pos[1]),
                            new SingleBlock(getT5Capstone()));
                    }

                    return new MultiblockStructure(blockMap);
                }
            )
        );
    }

    // ============================================
    // Tier 6: Transcendent - Largest altar with crystal caps
    // ============================================
    private static NamedDefinition createTier6() {
        int tier = 6;
        return new NamedDefinition(
            ResourceLocation.fromNamespaceAndPath("bloodmagic", "altar_tier_6"),
            MultiblockDefinition.fixed(
                Component.literal("Blood Altar - Tier 6 (Transcendent)"),
                "bloodmagic",
                ALTAR_CATEGORY,
                new BlockPos(23, 9, 23),
                (variant, level) -> {
                    Map<BlockPos, BlockEntry> blockMap = new LinkedHashMap<>();
                    int centerX = 11, centerZ = 11, altarY = 5;

                    // Altar at center
                    blockMap.put(new BlockPos(centerX, altarY, centerZ), new SingleBlock(getAltarBlock()));

                    // Tier 2 runes (3x3 below altar)
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            blockMap.put(new BlockPos(centerX + dx, altarY - 1, centerZ + dz), new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 3 rune ring at distance 3
                    for (int i = -2; i <= 2; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 2, centerZ + 3),
                            new BlockPos(centerX + i, altarY - 2, centerZ - 3),
                            new BlockPos(centerX + 3, altarY - 2, centerZ + i),
                            new BlockPos(centerX - 3, altarY - 2, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 3 pillars with glowstone
                    int[][] tier3Pillars = {{3, 3}, {3, -3}, {-3, 3}, {-3, -3}};
                    for (int[] pos : tier3Pillars) {
                        for (int dy = -1; dy <= 0; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 1, centerZ + pos[1]),
                            new SingleBlock(getT3Capstone()));
                    }

                    // Tier 4 rune ring at distance 5
                    for (int i = -3; i <= 3; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 3, centerZ + 5),
                            new BlockPos(centerX + i, altarY - 3, centerZ - 5),
                            new BlockPos(centerX + 5, altarY - 3, centerZ + i),
                            new BlockPos(centerX - 5, altarY - 3, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 4 pillars with bloodstone
                    int[][] tier4Pillars = {{5, 5}, {5, -5}, {-5, 5}, {-5, -5}};
                    for (int[] pos : tier4Pillars) {
                        for (int dy = -2; dy <= 1; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 2, centerZ + pos[1]),
                            new SingleBlock(getT4Capstone()));
                    }

                    // Tier 5 rune ring at distance 8
                    for (int i = -6; i <= 6; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 4, centerZ + 8),
                            new BlockPos(centerX + i, altarY - 4, centerZ - 8),
                            new BlockPos(centerX + 8, altarY - 4, centerZ + i),
                            new BlockPos(centerX - 8, altarY - 4, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 5 corner capstones (hellforged)
                    int[][] tier5Caps = {{8, 8}, {8, -8}, {-8, 8}, {-8, -8}};
                    for (int[] pos : tier5Caps) {
                        blockMap.put(new BlockPos(centerX + pos[0], altarY - 4, centerZ + pos[1]),
                            new SingleBlock(getT5Capstone()));
                    }

                    // Tier 6 rune ring at distance 11
                    for (int i = -9; i <= 9; i++) {
                        BlockPos[] positions = {
                            new BlockPos(centerX + i, altarY - 5, centerZ + 11),
                            new BlockPos(centerX + i, altarY - 5, centerZ - 11),
                            new BlockPos(centerX + 11, altarY - 5, centerZ + i),
                            new BlockPos(centerX - 11, altarY - 5, centerZ + i)
                        };
                        for (BlockPos pos : positions) {
                            blockMap.put(pos, new BlockGroup(
                                Component.literal("Any Rune"),
                                getRunesForTier(tier)
                            ));
                        }
                    }

                    // Tier 6 tall pillars at distance 11
                    int[][] tier6Pillars = {{11, 11}, {11, -11}, {-11, 11}, {-11, -11}};
                    for (int[] pos : tier6Pillars) {
                        for (int dy = -4; dy <= 2; dy++) {
                            blockMap.put(new BlockPos(centerX + pos[0], altarY + dy, centerZ + pos[1]),
                                new SingleBlock(getPillarBlock()));
                        }
                        // Crystal cluster cap on top
                        blockMap.put(new BlockPos(centerX + pos[0], altarY + 3, centerZ + pos[1]),
                            new SingleBlock(getT6Capstone()));
                    }

                    return new MultiblockStructure(blockMap);
                }
            )
        );
    }
}
