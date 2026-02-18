package com.multiblockprojector.common.registry;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.ProjectorAPI;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers multiblock definitions from legacy adapters at LOW priority,
 * so first-party registrations from other mods take precedence.
 */
public class LegacyAdapterRegistrar {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(EventPriority.LOW, LegacyAdapterRegistrar::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
            UniversalProjector.LOGGER.info("Registering multiblocks from legacy adapters...");
            // Adapters will be wired here in Tasks 8-10
        });
    }
}
