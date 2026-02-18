package com.multiblockprojector.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Extensible category for organizing multiblocks.
 * Third-party mods can create their own categories.
 */
public record MultiblockCategory(ResourceLocation id, Component displayName) {

    public static final MultiblockCategory PROCESSING = create("processing", "Processing");
    public static final MultiblockCategory POWER = create("power", "Power");
    public static final MultiblockCategory STORAGE = create("storage", "Storage");
    public static final MultiblockCategory CRAFTING = create("crafting", "Crafting");
    public static final MultiblockCategory GENERAL = create("general", "General");

    private static MultiblockCategory create(String path, String name) {
        return new MultiblockCategory(
            ResourceLocation.fromNamespaceAndPath("multiblockprojector", path),
            Component.literal(name)
        );
    }
}
