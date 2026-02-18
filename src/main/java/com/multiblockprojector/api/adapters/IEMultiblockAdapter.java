package com.multiblockprojector.api.adapters;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.MultiblockCategory;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockStructure;
import com.multiblockprojector.api.SingleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Immersive Engineering multiblocks.
 * Uses reflection to discover IE multiblocks and converts them to {@link MultiblockDefinition} objects.
 */
public class IEMultiblockAdapter {

    /**
     * A named definition pairing a registry ID with its definition.
     */
    public record NamedDefinition(ResourceLocation id, MultiblockDefinition definition) {}

    /**
     * Discover all IE multiblocks and return them as named definitions.
     *
     * @return list of discovered IE multiblock definitions
     */
    public static List<NamedDefinition> discover() {
        List<NamedDefinition> results = new ArrayList<>();
        try {
            Class<?> handlerClass = Class.forName("blusunrize.immersiveengineering.api.multiblocks.MultiblockHandler");
            Object getMultiblocksResult = handlerClass.getMethod("getMultiblocks").invoke(null);

            @SuppressWarnings("unchecked")
            List<Object> ieMultiblocks = (List<Object>) getMultiblocksResult;

            for (Object ieMultiblock : ieMultiblocks) {
                try {
                    NamedDefinition def = convertMultiblock(ieMultiblock);
                    results.add(def);
                } catch (Exception e) {
                    UniversalProjector.LOGGER.warn("Failed to convert IE multiblock", e);
                }
            }
        } catch (Exception e) {
            UniversalProjector.LOGGER.error("Failed to discover IE multiblocks via reflection", e);
            return results;
        }
        return results;
    }

    /**
     * Convert a single IE multiblock object (via reflection) to a NamedDefinition.
     */
    private static NamedDefinition convertMultiblock(Object ieMultiblock) throws Exception {
        ResourceLocation uniqueName = IEReflectionHelper.getUniqueName(ieMultiblock);
        Component displayName = IEReflectionHelper.getDisplayName(ieMultiblock);
        String modId = "immersiveengineering";
        MultiblockCategory category = categorizeByName(uniqueName.getPath());

        // Cache the reflection Method lookup so the lambda doesn't re-resolve it each call
        Method getStructureMethod = ieMultiblock.getClass().getMethod("getStructure", Level.class);

        MultiblockDefinition.StructureProvider structureProvider = (variant, level) -> {
            try {
                @SuppressWarnings("unchecked")
                List<StructureBlockInfo> structureList = (List<StructureBlockInfo>) getStructureMethod.invoke(ieMultiblock, level);

                Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
                for (StructureBlockInfo info : structureList) {
                    if (!info.state().isAir()) {
                        blocks.put(info.pos(), new SingleBlock(info.state()));
                    }
                }
                return new MultiblockStructure(blocks);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get structure for IE multiblock " + uniqueName, e);
            }
        };

        // Get size for the single variant
        BlockPos size;
        try {
            Vec3i vec = IEReflectionHelper.getSize(ieMultiblock, null);
            size = new BlockPos(vec.getX(), vec.getY(), vec.getZ());
        } catch (Exception e) {
            // Derive size from structure instead of using a hardcoded fallback
            try {
                MultiblockStructure probe = structureProvider.create(null, null);
                size = probe.size();
            } catch (Exception e2) {
                UniversalProjector.LOGGER.warn("Could not determine size for IE multiblock {}, using structure bounds", uniqueName);
                size = new BlockPos(1, 1, 1);
            }
        }

        MultiblockDefinition definition = MultiblockDefinition.fixed(
            displayName, modId, category, size, structureProvider
        );

        return new NamedDefinition(uniqueName, definition);
    }

    /**
     * Maps an IE multiblock name to a {@link MultiblockCategory}.
     */
    private static MultiblockCategory categorizeByName(String name) {
        if (name.contains("furnace") || name.contains("coke") || name.contains("alloy")) {
            return MultiblockCategory.PROCESSING;
        } else if (name.contains("generator") || name.contains("lightning")) {
            return MultiblockCategory.POWER;
        } else if (name.contains("assembler") || name.contains("press") || name.contains("workbench")) {
            return MultiblockCategory.CRAFTING;
        } else if (name.contains("tank") || name.contains("silo")) {
            return MultiblockCategory.STORAGE;
        }
        return MultiblockCategory.GENERAL;
    }

    /**
     * Private helper for reflection calls into IE multiblock objects.
     */
    private static class IEReflectionHelper {

        static ResourceLocation getUniqueName(Object ieMultiblock) throws Exception {
            return (ResourceLocation) ieMultiblock.getClass()
                .getMethod("getUniqueName")
                .invoke(ieMultiblock);
        }

        static Component getDisplayName(Object ieMultiblock) {
            try {
                return (Component) ieMultiblock.getClass()
                    .getMethod("getDisplayName")
                    .invoke(ieMultiblock);
            } catch (Exception e) {
                try {
                    ResourceLocation name = getUniqueName(ieMultiblock);
                    return Component.literal(name.getPath());
                } catch (Exception ex) {
                    return Component.literal("Unknown");
                }
            }
        }

        static Vec3i getSize(Object ieMultiblock, Level level) throws Exception {
            return (Vec3i) ieMultiblock.getClass()
                .getMethod("getSize", Level.class)
                .invoke(ieMultiblock, level);
        }
    }
}
