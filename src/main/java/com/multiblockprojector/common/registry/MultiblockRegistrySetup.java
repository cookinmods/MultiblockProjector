package com.multiblockprojector.common.registry;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.ProjectorAPI;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Creates and manages the multiblock definition registry.
 */
public class MultiblockRegistrySetup {

    private static Registry<MultiblockDefinition> registry;

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(MultiblockRegistrySetup::onNewRegistry);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        registry = event.create(
            new RegistryBuilder<>(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY)
                .sync(true)
        );
        UniversalProjector.LOGGER.info("Created multiblock definition registry");
    }

    /**
     * Returns the multiblock registry. Only valid after NewRegistryEvent has fired.
     */
    public static Registry<MultiblockDefinition> getRegistry() {
        if (registry == null) {
            throw new IllegalStateException("Multiblock registry not yet created");
        }
        return registry;
    }
}
