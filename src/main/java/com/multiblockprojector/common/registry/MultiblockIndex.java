package com.multiblockprojector.common.registry;

import com.multiblockprojector.api.MultiblockDefinition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Cached index of multiblock definitions grouped by mod.
 * Built lazily from the frozen registry, cached for the session.
 */
public class MultiblockIndex {

    /** Represents a mod tab in the GUI. */
    public record TabEntry(String modId, String displayName) {}

    /** Special mod ID for the "All" tab. */
    public static final String ALL_TAB = "__all__";

    private static MultiblockIndex INSTANCE;

    private final List<TabEntry> tabs;
    private final Map<String, List<MultiblockDefinition>> byMod;
    private final List<MultiblockDefinition> all;

    private MultiblockIndex(List<TabEntry> tabs, Map<String, List<MultiblockDefinition>> byMod, List<MultiblockDefinition> all) {
        this.tabs = tabs;
        this.byMod = byMod;
        this.all = all;
    }

    public static MultiblockIndex get() {
        if (INSTANCE == null) {
            INSTANCE = buildFromRegistry();
        }
        return INSTANCE;
    }

    public static void invalidate() {
        INSTANCE = null;
    }

    /**
     * All tabs, sorted alphabetically by display name, with "All" first.
     */
    public List<TabEntry> getTabs() {
        return tabs;
    }

    /**
     * All multiblock definitions, sorted alphabetically by display name.
     */
    public List<MultiblockDefinition> getAll() {
        return all;
    }

    /**
     * Multiblock definitions for a specific mod tab.
     * Use {@link #ALL_TAB} for all multiblocks.
     */
    public List<MultiblockDefinition> getForTab(String modIdOrAll) {
        if (ALL_TAB.equals(modIdOrAll)) {
            return all;
        }
        return byMod.getOrDefault(modIdOrAll, List.of());
    }

    /**
     * Look up a definition by registry ID.
     */
    public Optional<MultiblockDefinition> getById(ResourceLocation id) {
        var registry = MultiblockRegistrySetup.getRegistry();
        var def = registry.get(id);
        return Optional.ofNullable(def);
    }

    /**
     * Look up the registry ID for a definition.
     */
    public Optional<ResourceLocation> getId(MultiblockDefinition def) {
        var registry = MultiblockRegistrySetup.getRegistry();
        return Optional.ofNullable(registry.getKey(def));
    }

    private static MultiblockIndex buildFromRegistry() {
        var registry = MultiblockRegistrySetup.getRegistry();

        // Collect all definitions sorted by display name
        List<MultiblockDefinition> all = registry.stream()
            .sorted(Comparator.comparing(d -> d.displayName().getString(), String.CASE_INSENSITIVE_ORDER))
            .toList();

        // Group by mod ID
        Map<String, List<MultiblockDefinition>> byMod = all.stream()
            .collect(Collectors.groupingBy(MultiblockDefinition::modId));

        // Build sorted tab list
        List<TabEntry> modTabs = byMod.keySet().stream()
            .map(modId -> new TabEntry(modId, getModDisplayName(modId)))
            .sorted(Comparator.comparing(TabEntry::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        // Prepend "All" tab
        List<TabEntry> tabs = new ArrayList<>();
        tabs.add(new TabEntry(ALL_TAB, "All"));
        tabs.addAll(modTabs);

        return new MultiblockIndex(
            Collections.unmodifiableList(tabs),
            Collections.unmodifiableMap(byMod),
            all
        );
    }

    private static String getModDisplayName(String modId) {
        return ModList.get().getModContainerById(modId)
            .map(c -> c.getModInfo().getDisplayName())
            .orElse(modId);
    }
}
