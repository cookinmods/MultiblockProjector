package com.multiblockprojector.client.schematic;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.registry.MultiblockIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Cached index that scans filesystem folders for {@code .nbt} schematic files
 * and makes them available as {@link MultiblockDefinition} entries.
 * <p>
 * Parallel to {@link MultiblockIndex} but for user-provided schematics.
 */
public class SchematicIndex {

    /** Tab ID for schematics placed directly in the custom schematics folder. */
    public static final String CUSTOM_TAB_ID = "multiblockprojector_custom_nbts";
    /** Display name for the custom schematics tab. */
    public static final String CUSTOM_TAB_NAME = "Custom NBTs";

    /** Tab ID for schematics found in the Create mod's schematics folder. */
    public static final String CREATE_TAB_ID = "multiblockprojector_create_schematics";
    /** Display name for the Create schematics tab. */
    public static final String CREATE_TAB_NAME = "Create Schematics";

    private static SchematicIndex INSTANCE;

    private final List<MultiblockIndex.TabEntry> tabs;
    private final Map<String, List<MultiblockDefinition>> byTab;
    private final List<MultiblockDefinition> all;
    private final Map<ResourceLocation, SchematicEntry> entriesById;
    private final Map<MultiblockDefinition, ResourceLocation> definitionToId;

    private SchematicIndex(
        List<MultiblockIndex.TabEntry> tabs,
        Map<String, List<MultiblockDefinition>> byTab,
        List<MultiblockDefinition> all,
        Map<ResourceLocation, SchematicEntry> entriesById,
        Map<MultiblockDefinition, ResourceLocation> definitionToId
    ) {
        this.tabs = tabs;
        this.byTab = byTab;
        this.all = all;
        this.entriesById = entriesById;
        this.definitionToId = definitionToId;
    }

    /** Returns the cached index, building it lazily on first access. */
    public static SchematicIndex get() {
        if (INSTANCE == null) {
            SchematicExampleCopier.copyIfNeeded();
            INSTANCE = scan();
        }
        return INSTANCE;
    }

    /** Forces a full rescan of all schematic folders. */
    public static SchematicIndex rescan() {
        SchematicExampleCopier.copyIfNeeded();
        INSTANCE = scan();
        return INSTANCE;
    }

    /** Invalidates the cached index so the next {@link #get()} call rebuilds it. */
    public static void invalidate() {
        INSTANCE = null;
    }

    /** All tabs containing schematics, sorted alphabetically. */
    public List<MultiblockIndex.TabEntry> getTabs() {
        return tabs;
    }

    /** All schematic definitions, sorted alphabetically by display name. */
    public List<MultiblockDefinition> getAll() {
        return all;
    }

    /** Schematic definitions for a specific tab. Returns empty list if the tab has no schematics. */
    public List<MultiblockDefinition> getForTab(String tabId) {
        return byTab.getOrDefault(tabId, List.of());
    }

    /** Looks up a schematic entry by its resource location ID. */
    @Nullable
    public SchematicEntry getEntryById(ResourceLocation id) {
        return entriesById.get(id);
    }

    /** Looks up the resource location ID for a given schematic definition. */
    @Nullable
    public ResourceLocation getSchematicId(MultiblockDefinition definition) {
        return definitionToId.get(definition);
    }

    /** Whether any schematics exist under the given tab ID. */
    public boolean hasTab(String tabId) {
        return byTab.containsKey(tabId);
    }

    // ---- Scanning ----

    private static SchematicIndex scan() {
        List<SchematicEntry> entries = new ArrayList<>();

        // Ensure custom schematics directory exists
        Path customRoot = FMLPaths.CONFIGDIR.get().resolve("multiblockprojector").resolve("schematics");
        try {
            Files.createDirectories(customRoot);
        } catch (IOException e) {
            UniversalProjector.LOGGER.warn("Failed to create schematics directory: {}", customRoot, e);
        }

        if (Files.isDirectory(customRoot)) {
            scanCustomFolder(customRoot, entries);
        }

        // If Create is loaded, also scan its schematics folder
        if (ModList.get().isLoaded("create")) {
            Path createRoot = FMLPaths.GAMEDIR.get().resolve("schematics");
            if (Files.isDirectory(createRoot)) {
                scanCreateFolder(createRoot, entries);
            }
        }

        return buildIndex(entries);
    }

    /**
     * Scans the custom schematics folder. Files directly in the root go into
     * {@link #CUSTOM_TAB_ID}; files inside subfolders get their own tab
     * based on the first-level subfolder name.
     */
    private static void scanCustomFolder(Path root, List<SchematicEntry> entries) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".nbt"))
                .forEach(nbtFile -> {
                    Path relative = root.relativize(nbtFile);

                    // Determine tab: directly in root vs in a subfolder
                    String tabId;
                    Component tabDisplayName;
                    if (relative.getNameCount() == 1) {
                        // File directly in root
                        tabId = CUSTOM_TAB_ID;
                        tabDisplayName = Component.literal(CUSTOM_TAB_NAME);
                    } else {
                        // File in a subfolder — use first subfolder name
                        String firstSubfolder = relative.getName(0).toString();
                        tabId = "multiblockprojector_custom_" + firstSubfolder.toLowerCase();
                        tabDisplayName = Component.literal(SchematicEntry.prettifyName(firstSubfolder));
                    }

                    // Build resource location ID
                    String relativeStr = relative.toString().replace('\\', '/');
                    // Remove .nbt extension
                    String withoutExt = relativeStr.substring(0, relativeStr.length() - 4);
                    String sanitized = sanitizeForId(withoutExt.toLowerCase());
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                        "multiblockprojector", "custom/" + sanitized
                    );

                    // Read size — skip files we can't parse
                    BlockPos size = SchematicLoader.readSize(nbtFile);
                    if (size == null) return;

                    // Display name from filename
                    String filename = nbtFile.getFileName().toString();
                    String rawName = filename.substring(0, filename.length() - 4); // strip .nbt
                    Component displayName = Component.literal(SchematicEntry.prettifyName(rawName));

                    entries.add(new SchematicEntry(id, displayName, tabId, tabDisplayName, nbtFile, size));
                });
        } catch (IOException e) {
            UniversalProjector.LOGGER.warn("Failed to scan custom schematics folder: {}", root, e);
        }
    }

    /**
     * Scans the Create mod's schematics folder. All files go into
     * {@link #CREATE_TAB_ID} regardless of subfolder structure.
     */
    private static void scanCreateFolder(Path root, List<SchematicEntry> entries) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".nbt"))
                .forEach(nbtFile -> {
                    Path relative = root.relativize(nbtFile);

                    // Build resource location ID
                    String relativeStr = relative.toString().replace('\\', '/');
                    String withoutExt = relativeStr.substring(0, relativeStr.length() - 4);
                    String sanitized = sanitizeForId(withoutExt.toLowerCase());
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                        "multiblockprojector", "create/" + sanitized
                    );

                    // Read size — skip files we can't parse
                    BlockPos size = SchematicLoader.readSize(nbtFile);
                    if (size == null) return;

                    // Display name from filename
                    String filename = nbtFile.getFileName().toString();
                    String rawName = filename.substring(0, filename.length() - 4);
                    Component displayName = Component.literal(SchematicEntry.prettifyName(rawName));

                    entries.add(new SchematicEntry(
                        id, displayName, CREATE_TAB_ID,
                        Component.literal(CREATE_TAB_NAME), nbtFile, size
                    ));
                });
        } catch (IOException e) {
            UniversalProjector.LOGGER.warn("Failed to scan Create schematics folder: {}", root, e);
        }
    }

    /**
     * Replaces characters invalid in resource location paths with underscores.
     * Valid characters: {@code a-z}, {@code 0-9}, {@code /}, {@code _}, {@code .}, {@code -}.
     */
    private static String sanitizeForId(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '_' || c == '.' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    // ---- Index building ----

    private static SchematicIndex buildIndex(List<SchematicEntry> entries) {
        Map<String, List<MultiblockDefinition>> byTab = new LinkedHashMap<>();
        List<MultiblockDefinition> all = new ArrayList<>();
        Map<ResourceLocation, SchematicEntry> entriesById = new LinkedHashMap<>();
        Map<MultiblockDefinition, ResourceLocation> definitionToId = new IdentityHashMap<>();

        // Track tab display names for building tab list
        Map<String, String> tabDisplayNames = new LinkedHashMap<>();

        for (SchematicEntry entry : entries) {
            MultiblockDefinition def = entry.toDefinition();

            entriesById.put(entry.id(), entry);
            definitionToId.put(def, entry.id());
            byTab.computeIfAbsent(entry.tabId(), k -> new ArrayList<>()).add(def);
            all.add(def);
            tabDisplayNames.putIfAbsent(entry.tabId(), entry.tabDisplayName().getString());
        }

        // Sort definitions alphabetically within each tab and in the all list
        Comparator<MultiblockDefinition> byName = Comparator.comparing(
            d -> d.displayName().getString(), String.CASE_INSENSITIVE_ORDER
        );
        all.sort(byName);
        byTab.values().forEach(list -> list.sort(byName));

        // Build sorted tab list
        List<MultiblockIndex.TabEntry> tabs = tabDisplayNames.entrySet().stream()
            .map(e -> new MultiblockIndex.TabEntry(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(MultiblockIndex.TabEntry::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        // Make byTab values unmodifiable
        Map<String, List<MultiblockDefinition>> unmodifiableByTab = new LinkedHashMap<>();
        byTab.forEach((k, v) -> unmodifiableByTab.put(k, Collections.unmodifiableList(v)));

        return new SchematicIndex(
            Collections.unmodifiableList(new ArrayList<>(tabs)),
            Collections.unmodifiableMap(unmodifiableByTab),
            Collections.unmodifiableList(all),
            Collections.unmodifiableMap(entriesById),
            definitionToId
        );
    }
}
