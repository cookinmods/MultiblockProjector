package com.multiblockprojector.common;

import com.multiblockprojector.UniversalProjector;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Handles mod lifecycle events for the Universal Projector mod
 */
@EventBusSubscriber(modid = UniversalProjector.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CommonEventHandler {
    
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            UniversalProjector.LOGGER.info("Universal Projector common setup complete");
        });
    }
}