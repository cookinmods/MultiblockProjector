package com.multiblockprojector.api;

import com.multiblockprojector.UniversalProjector;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for all discovered multiblocks from various mods
 */
public class UniversalMultiblockHandler {
    
    private static final List<IUniversalMultiblock> MULTIBLOCKS = new ArrayList<>();
    private static final Map<ResourceLocation, IUniversalMultiblock> BY_NAME = new HashMap<>();
    private static final Map<String, List<IUniversalMultiblock>> BY_MOD = new HashMap<>();
    
    /**
     * Register a multiblock with the universal handler
     */
    public static synchronized void registerMultiblock(IUniversalMultiblock multiblock) {
        // Prevent duplicate registration
        if (BY_NAME.containsKey(multiblock.getUniqueName())) {
            UniversalProjector.LOGGER.debug("Multiblock {} already registered, skipping duplicate", 
                multiblock.getUniqueName());
            return;
        }
        
        MULTIBLOCKS.add(multiblock);
        BY_NAME.put(multiblock.getUniqueName(), multiblock);
        BY_MOD.computeIfAbsent(multiblock.getModId(), k -> new ArrayList<>()).add(multiblock);
        
        UniversalProjector.LOGGER.debug("Registered multiblock: {} from {}", 
            multiblock.getUniqueName(), multiblock.getModId());
    }
    
    /**
     * @return All registered multiblocks in alphabetical order
     */
    public static List<IUniversalMultiblock> getMultiblocks() {
        List<IUniversalMultiblock> sorted = new ArrayList<>(MULTIBLOCKS);
        sorted.sort((a, b) -> a.getDisplayName().getString().compareToIgnoreCase(b.getDisplayName().getString()));
        return sorted;
    }
    
    /**
     * Get multiblock by unique name
     */
    @Nullable
    public static IUniversalMultiblock getByUniqueName(ResourceLocation name) {
        return BY_NAME.get(name);
    }
    
    /**
     * Get all multiblocks from a specific mod
     */
    public static List<IUniversalMultiblock> getMultiblocksFromMod(String modId) {
        return new ArrayList<>(BY_MOD.getOrDefault(modId, new ArrayList<>()));
    }
    
    /**
     * Discover and register multiblocks from all available mods
     */
    public static void discoverMultiblocks() {
        UniversalProjector.LOGGER.info("Discovering multiblocks from installed mods...");
        
        int realMultiblocksFound = 0;
        
        // IE multiblocks are now registered via LegacyAdapterRegistrar into the new registry.
        // Count any IE multiblocks that were already registered through the new path.
        if (isModLoaded("immersiveengineering")) {
            UniversalProjector.LOGGER.info("IE multiblocks handled by registry-based adapter");
        }
        
        // Mekanism multiblocks are now registered via LegacyAdapterRegistrar into the new registry.
        if (isModLoaded("mekanism")) {
            UniversalProjector.LOGGER.info("Mekanism multiblocks handled by registry-based adapter");
        }

        // Blood Magic multiblocks are now registered via LegacyAdapterRegistrar into the new registry.
        if (isModLoaded("bloodmagic")) {
            UniversalProjector.LOGGER.info("Blood Magic multiblocks handled by registry-based adapter");
        }

        // TODO: Add adapters for other mods (Create, etc.)
        
        // Only register test multiblocks if no real multiblocks were found
        // Check total registered multiblocks instead of just this call's count to handle duplicate calls
        int totalRealMultiblocks = 0;
        for (IUniversalMultiblock mb : MULTIBLOCKS) {
            if (!mb.getModId().equals("multiblockprojector")) { // Not test multiblocks
                totalRealMultiblocks++;
            }
        }
        
        if (totalRealMultiblocks == 0) {
            UniversalProjector.LOGGER.info("No real multiblocks found, registering test multiblocks for development");
            TestMultiblock.registerTestMultiblocks();
        } else {
            UniversalProjector.LOGGER.info("Found {} real multiblocks, skipping test multiblocks", totalRealMultiblocks);
        }
        
        UniversalProjector.LOGGER.info("Discovered {} total multiblocks from {} mods", 
            MULTIBLOCKS.size(), BY_MOD.size());
    }
    
    private static boolean isModLoaded(String modId) {
        try {
            return net.neoforged.fml.ModList.get().isLoaded(modId);
        } catch (Exception e) {
            return false;
        }
    }
}