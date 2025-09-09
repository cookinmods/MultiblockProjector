package com.multiblockprojector.common;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * Handles NeoForge events for the Universal Projector mod
 */
@EventBusSubscriber(modid = UniversalProjector.MODID)
public class NeoForgeEventHandler {
    
    @SubscribeEvent  
    public static void onServerStarting(ServerStartingEvent event) {
        // Discover multiblocks when server starts
        UniversalProjector.LOGGER.info("Server starting - discovering multiblocks...");
        UniversalMultiblockHandler.discoverMultiblocks();
    }
}