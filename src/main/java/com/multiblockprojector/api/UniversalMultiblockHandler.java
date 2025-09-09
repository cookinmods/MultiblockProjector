package com.multiblockprojector.api;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.adapters.IEMultiblockAdapter;
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
        MULTIBLOCKS.add(multiblock);
        BY_NAME.put(multiblock.getUniqueName(), multiblock);
        BY_MOD.computeIfAbsent(multiblock.getModId(), k -> new ArrayList<>()).add(multiblock);
        
        UniversalProjector.LOGGER.debug("Registered multiblock: {} from {}", 
            multiblock.getUniqueName(), multiblock.getModId());
    }
    
    /**
     * @return All registered multiblocks
     */
    public static List<IUniversalMultiblock> getMultiblocks() {
        return new ArrayList<>(MULTIBLOCKS);
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
        
        // Try to load IE multiblocks if available
        if (isModLoaded("immersiveengineering")) {
            try {
                int beforeCount = MULTIBLOCKS.size();
                IEMultiblockAdapter.registerIEMultiblocks();
                realMultiblocksFound += MULTIBLOCKS.size() - beforeCount;
            } catch (Exception e) {
                UniversalProjector.LOGGER.error("Failed to load Immersive Engineering multiblocks", e);
            }
        }
        
        // TODO: Add adapters for other mods (Create, Mekanism, etc.)
        
        // Only register test multiblocks if no real multiblocks were found
        if (realMultiblocksFound == 0) {
            UniversalProjector.LOGGER.info("No real multiblocks found, registering test multiblocks for development");
            TestMultiblock.registerTestMultiblocks();
        } else {
            UniversalProjector.LOGGER.info("Found {} real multiblocks, skipping test multiblocks", realMultiblocksFound);
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