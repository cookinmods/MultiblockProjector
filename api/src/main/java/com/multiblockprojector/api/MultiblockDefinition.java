package com.multiblockprojector.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Registry value type for multiblock definitions.
 * Registered via {@code RegisterEvent} into the multiblock registry.
 *
 * @param displayName  Human-readable name shown in GUI
 * @param modId        Plain string mod ID (e.g., "mekanism")
 * @param category     Category for organization
 * @param variants     Size variants; single-element list for fixed-size multiblocks
 * @param structureProvider  Lazy structure generator
 */
public record MultiblockDefinition(
    Component displayName,
    String modId,
    MultiblockCategory category,
    List<SizeVariant> variants,
    StructureProvider structureProvider
) {
    /**
     * A size variant for variable-size multiblocks.
     */
    public record SizeVariant(Component label, BlockPos dimensions, boolean isDefault) {
        public String getSizeString() {
            return dimensions.getX() + "x" + dimensions.getY() + "x" + dimensions.getZ();
        }

        public Component getFullDisplayName() {
            return Component.literal(label.getString() + " (" + getSizeString() + ")");
        }
    }

    /**
     * Creates the multiblock structure for a given size variant.
     *
     * <p>Contract: Must be idempotent, side-effect free, and thread-safe.
     * May be called from any thread. Implementations should not access
     * or modify world state.</p>
     */
    @FunctionalInterface
    public interface StructureProvider {
        MultiblockStructure create(SizeVariant variant, @Nullable Level level);
    }

    /**
     * Convenience factory for single-size multiblocks.
     */
    public static MultiblockDefinition fixed(
        Component name, String modId, MultiblockCategory category,
        BlockPos size, StructureProvider provider
    ) {
        var single = new SizeVariant(
            Component.literal(size.getX() + "x" + size.getY() + "x" + size.getZ()),
            size, true
        );
        return new MultiblockDefinition(name, modId, category, List.of(single), provider);
    }

    /**
     * Returns the default size variant (first one marked as default, or the first variant).
     */
    public SizeVariant getDefaultVariant() {
        return variants.stream()
            .filter(SizeVariant::isDefault)
            .findFirst()
            .orElse(variants.get(0));
    }

    /**
     * Whether this definition has multiple size variants.
     */
    public boolean isVariableSize() {
        return variants.size() > 1;
    }
}
