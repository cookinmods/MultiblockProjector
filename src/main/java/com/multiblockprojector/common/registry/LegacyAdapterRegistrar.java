package com.multiblockprojector.common.registry;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.ProjectorAPI;
import com.multiblockprojector.api.adapters.BloodMagicMultiblockAdapter;
import com.multiblockprojector.api.adapters.IEMultiblockAdapter;
import com.multiblockprojector.api.adapters.MekanismMultiblockAdapter;
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

            if (ModList.get().isLoaded("mekanism")) {
                try {
                    var registry = MultiblockRegistrySetup.getRegistry();
                    for (var entry : MekanismMultiblockAdapter.discover()) {
                        if (!registry.containsKey(entry.id())) {
                            helper.register(entry.id(), entry.definition());
                        }
                    }
                    UniversalProjector.LOGGER.info("Registered Mekanism multiblocks from adapter");
                } catch (Exception e) {
                    UniversalProjector.LOGGER.error("Failed to load Mekanism multiblocks", e);
                }
            }

            if (ModList.get().isLoaded("bloodmagic")) {
                try {
                    var registry = MultiblockRegistrySetup.getRegistry();
                    for (var entry : BloodMagicMultiblockAdapter.discover()) {
                        if (!registry.containsKey(entry.id())) {
                            helper.register(entry.id(), entry.definition());
                        }
                    }
                    UniversalProjector.LOGGER.info("Registered Blood Magic multiblocks from adapter");
                } catch (Exception e) {
                    UniversalProjector.LOGGER.error("Failed to load Blood Magic multiblocks", e);
                }
            }
        });
    }
}
