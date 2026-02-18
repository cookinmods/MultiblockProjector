package com.multiblockprojector.common.registry;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.ProjectorAPI;
import com.multiblockprojector.api.adapters.IEMultiblockAdapter;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
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

            if (ModList.get().isLoaded("immersiveengineering")) {
                try {
                    var registry = MultiblockRegistrySetup.getRegistry();
                    for (var entry : IEMultiblockAdapter.discover()) {
                        if (!registry.containsKey(entry.id())) {
                            helper.register(entry.id(), entry.definition());
                        }
                    }
                    UniversalProjector.LOGGER.info("Registered IE multiblocks from adapter");
                } catch (Exception e) {
                    UniversalProjector.LOGGER.error("Failed to load IE multiblocks", e);
                }
            }

            // Adapters for other mods will be wired here in Tasks 9-10
        });
    }
}
