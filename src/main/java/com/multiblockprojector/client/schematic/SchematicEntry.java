package com.multiblockprojector.client.schematic;

import com.multiblockprojector.api.MultiblockCategory;
import com.multiblockprojector.api.MultiblockDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

/**
 * Represents a discovered {@code .nbt} schematic file that can be projected.
 *
 * @param id             Unique resource location identifier for this schematic
 * @param displayName    Human-readable name shown in the GUI
 * @param tabId          Tab identifier for grouping in the GUI
 * @param tabDisplayName Human-readable tab name
 * @param filePath       Filesystem path to the .nbt file
 * @param size           Dimensions of the structure (from NBT size tag)
 */
public record SchematicEntry(
    ResourceLocation id,
    Component displayName,
    String tabId,
    Component tabDisplayName,
    Path filePath,
    BlockPos size
) {

    /** Category used for all schematic-based multiblock definitions. */
    public static final MultiblockCategory SCHEMATIC_CATEGORY = new MultiblockCategory(
        ResourceLocation.fromNamespaceAndPath("multiblockprojector", "schematic"),
        Component.literal("Schematic")
    );

    /**
     * Creates a synthetic {@link MultiblockDefinition} from this schematic entry.
     * The structure is loaded lazily when the definition's provider is first called.
     */
    public MultiblockDefinition toDefinition() {
        Path path = this.filePath;
        return MultiblockDefinition.fixed(
            displayName, tabId, SCHEMATIC_CATEGORY, size,
            (variant, level) -> {
                var structure = SchematicLoader.load(path);
                if (structure == null) {
                    throw new IllegalStateException("Failed to load schematic: " + path);
                }
                return structure;
            }
        );
    }

    /**
     * Converts a raw filename (without extension) into a human-readable display name.
     * Replaces underscores and hyphens with spaces and title-cases each word.
     * <p>
     * Example: {@code "my_cool_build"} becomes {@code "My Cool Build"}.
     */
    public static String prettifyName(String raw) {
        String[] words = raw.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            String word = words[i];
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }
}
