package com.multiblockprojector.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Public API entry point for the Multiblock Projector mod.
 * Contains the registry key that third-party mods use to register multiblocks.
 *
 * <p>Usage in other mods:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onRegister(RegisterEvent event) {
 *     event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
 *         helper.register(rl("mymod", "my_structure"),
 *             MultiblockDefinition.fixed(...));
 *     });
 * }
 * }</pre>
 */
public class ProjectorAPI {

    public static final ResourceKey<Registry<MultiblockDefinition>> MULTIBLOCK_REGISTRY_KEY =
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath("multiblockprojector", "multiblocks"));

    private ProjectorAPI() {}
}
