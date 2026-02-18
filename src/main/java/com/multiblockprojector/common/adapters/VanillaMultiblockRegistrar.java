package com.multiblockprojector.common.adapters;

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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registers vanilla Minecraft multiblock structures.
 */
@EventBusSubscriber(modid = "multiblockprojector", bus = EventBusSubscriber.Bus.MOD)
public class VanillaMultiblockRegistrar {

    private static final String MOD_ID = "minecraft";

    private static final BlockGroup BEACON_MINERAL = new BlockGroup(
        Component.literal("Mineral Block"),
        List.of(
            Blocks.IRON_BLOCK.defaultBlockState(),
            Blocks.GOLD_BLOCK.defaultBlockState(),
            Blocks.DIAMOND_BLOCK.defaultBlockState(),
            Blocks.EMERALD_BLOCK.defaultBlockState(),
            Blocks.NETHERITE_BLOCK.defaultBlockState()
        )
    );

    private static final BlockGroup PRISMARINE = new BlockGroup(
        Component.literal("Prismarine"),
        List.of(
            Blocks.PRISMARINE.defaultBlockState(),
            Blocks.PRISMARINE_BRICKS.defaultBlockState(),
            Blocks.DARK_PRISMARINE.defaultBlockState(),
            Blocks.SEA_LANTERN.defaultBlockState()
        )
    );

    private static final BlockGroup SOUL_BLOCK = new BlockGroup(
        Component.literal("Soul Block"),
        List.of(
            Blocks.SOUL_SAND.defaultBlockState(),
            Blocks.SOUL_SOIL.defaultBlockState()
        )
    );

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
            registerBeacons(helper);
            registerNetherPortals(helper);
            registerConduit(helper);
            registerIronGolem(helper);
            registerSnowGolem(helper);
            registerWither(helper);
        });
    }

    // ---- Beacons ----

    private static void registerBeacons(RegisterEvent.RegisterHelper<MultiblockDefinition> helper) {
        helper.register(
            rl("beacon"),
            new MultiblockDefinition(
                Component.literal("Beacon"),
                MOD_ID,
                MultiblockCategory.POWER,
                List.of(
                    new SizeVariant(Component.literal("Tier 1"), new BlockPos(3, 2, 3), false),
                    new SizeVariant(Component.literal("Tier 2"), new BlockPos(5, 3, 5), false),
                    new SizeVariant(Component.literal("Tier 3"), new BlockPos(7, 4, 7), false),
                    new SizeVariant(Component.literal("Tier 4"), new BlockPos(9, 5, 9), true)
                ),
                (variant, level) -> buildBeacon(variant)
            )
        );
    }

    private static MultiblockStructure buildBeacon(SizeVariant variant) {
        int tiers = switch (variant.dimensions().getY()) {
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 3;
            default -> 4;
        };

        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        int maxWidth = tiers * 2 + 1;
        int offset = (variant.dimensions().getX() - maxWidth) / 2;

        // Build pyramid layers bottom-up
        for (int tier = tiers; tier >= 1; tier--) {
            int layerWidth = tier * 2 + 1;
            int y = tiers - tier;
            int layerOffset = offset + (maxWidth - layerWidth) / 2;

            for (int x = 0; x < layerWidth; x++) {
                for (int z = 0; z < layerWidth; z++) {
                    blocks.put(new BlockPos(layerOffset + x, y, layerOffset + z), BEACON_MINERAL);
                }
            }
        }

        // Beacon on top center
        int center = variant.dimensions().getX() / 2;
        blocks.put(new BlockPos(center, tiers, center), new SingleBlock(Blocks.BEACON.defaultBlockState()));

        return new MultiblockStructure(blocks);
    }

    // ---- Nether Portals ----

    private static void registerNetherPortals(RegisterEvent.RegisterHelper<MultiblockDefinition> helper) {
        helper.register(
            rl("nether_portal"),
            new MultiblockDefinition(
                Component.literal("Nether Portal"),
                MOD_ID,
                MultiblockCategory.GENERAL,
                List.of(
                    new SizeVariant(Component.literal("Minimum"), new BlockPos(4, 5, 1), true),
                    new SizeVariant(Component.literal("Medium"), new BlockPos(5, 7, 1), false),
                    new SizeVariant(Component.literal("Large"), new BlockPos(6, 8, 1), false)
                ),
                (variant, level) -> buildNetherPortal(
                    variant.dimensions().getX(),
                    variant.dimensions().getY()
                )
            )
        );
    }

    private static MultiblockStructure buildNetherPortal(int width, int height) {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        SingleBlock obsidian = new SingleBlock(Blocks.OBSIDIAN.defaultBlockState());

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                boolean isFrame = x == 0 || x == width - 1 || y == 0 || y == height - 1;
                if (isFrame) {
                    blocks.put(new BlockPos(x, y, 0), obsidian);
                }
            }
        }

        return new MultiblockStructure(blocks);
    }

    // ---- Conduit ----

    private static void registerConduit(RegisterEvent.RegisterHelper<MultiblockDefinition> helper) {
        helper.register(
            rl("conduit"),
            MultiblockDefinition.fixed(
                Component.literal("Conduit"),
                MOD_ID,
                MultiblockCategory.POWER,
                new BlockPos(5, 5, 5),
                (variant, level) -> buildConduit()
            )
        );
    }

    private static MultiblockStructure buildConduit() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        Set<BlockPos> positions = new HashSet<>();

        // Three orthogonal 5x5 rings centered at (2,2,2)
        // XY ring at z=2
        addRing(positions, (x, y) -> new BlockPos(x, y, 2));
        // XZ ring at y=2
        addRing(positions, (x, z) -> new BlockPos(x, 2, z));
        // YZ ring at x=2
        addRing(positions, (y, z) -> new BlockPos(2, y, z));

        // Remove center (conduit goes there)
        positions.remove(new BlockPos(2, 2, 2));

        for (BlockPos pos : positions) {
            blocks.put(pos, PRISMARINE);
        }

        // Conduit at center
        blocks.put(new BlockPos(2, 2, 2), new SingleBlock(Blocks.CONDUIT.defaultBlockState()));

        return new MultiblockStructure(blocks);
    }

    @FunctionalInterface
    private interface RingMapper {
        BlockPos map(int a, int b);
    }

    private static void addRing(Set<BlockPos> positions, RingMapper mapper) {
        for (int a = 0; a < 5; a++) {
            for (int b = 0; b < 5; b++) {
                if (a == 0 || a == 4 || b == 0 || b == 4) {
                    positions.add(mapper.map(a, b));
                }
            }
        }
    }

    // ---- Iron Golem ----

    private static void registerIronGolem(RegisterEvent.RegisterHelper<MultiblockDefinition> helper) {
        helper.register(
            rl("iron_golem"),
            MultiblockDefinition.fixed(
                Component.literal("Iron Golem"),
                MOD_ID,
                MultiblockCategory.GENERAL,
                new BlockPos(3, 3, 1),
                (variant, level) -> buildIronGolem()
            )
        );
    }

    private static MultiblockStructure buildIronGolem() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        SingleBlock iron = new SingleBlock(Blocks.IRON_BLOCK.defaultBlockState());
        SingleBlock pumpkin = new SingleBlock(Blocks.CARVED_PUMPKIN.defaultBlockState());

        // Body (center column)
        blocks.put(new BlockPos(1, 0, 0), iron);
        // Arms + chest
        blocks.put(new BlockPos(0, 1, 0), iron);
        blocks.put(new BlockPos(1, 1, 0), iron);
        blocks.put(new BlockPos(2, 1, 0), iron);
        // Head
        blocks.put(new BlockPos(1, 2, 0), pumpkin);

        return new MultiblockStructure(blocks);
    }

    // ---- Snow Golem ----

    private static void registerSnowGolem(RegisterEvent.RegisterHelper<MultiblockDefinition> helper) {
        helper.register(
            rl("snow_golem"),
            MultiblockDefinition.fixed(
                Component.literal("Snow Golem"),
                MOD_ID,
                MultiblockCategory.GENERAL,
                new BlockPos(1, 3, 1),
                (variant, level) -> buildSnowGolem()
            )
        );
    }

    private static MultiblockStructure buildSnowGolem() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        SingleBlock snow = new SingleBlock(Blocks.SNOW_BLOCK.defaultBlockState());
        SingleBlock pumpkin = new SingleBlock(Blocks.CARVED_PUMPKIN.defaultBlockState());

        blocks.put(new BlockPos(0, 0, 0), snow);
        blocks.put(new BlockPos(0, 1, 0), snow);
        blocks.put(new BlockPos(0, 2, 0), pumpkin);

        return new MultiblockStructure(blocks);
    }

    // ---- Wither ----

    private static void registerWither(RegisterEvent.RegisterHelper<MultiblockDefinition> helper) {
        helper.register(
            rl("wither"),
            MultiblockDefinition.fixed(
                Component.literal("Wither"),
                MOD_ID,
                MultiblockCategory.GENERAL,
                new BlockPos(3, 3, 1),
                (variant, level) -> buildWither()
            )
        );
    }

    private static MultiblockStructure buildWither() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        SingleBlock skull = new SingleBlock(Blocks.WITHER_SKELETON_SKULL.defaultBlockState());

        // Soul sand/soil T-shape
        blocks.put(new BlockPos(1, 0, 0), SOUL_BLOCK);
        blocks.put(new BlockPos(0, 1, 0), SOUL_BLOCK);
        blocks.put(new BlockPos(1, 1, 0), SOUL_BLOCK);
        blocks.put(new BlockPos(2, 1, 0), SOUL_BLOCK);

        // Wither skeleton skulls
        blocks.put(new BlockPos(0, 2, 0), skull);
        blocks.put(new BlockPos(1, 2, 0), skull);
        blocks.put(new BlockPos(2, 2, 0), skull);

        return new MultiblockStructure(blocks);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("multiblockprojector", "vanilla/" + path);
    }
}
