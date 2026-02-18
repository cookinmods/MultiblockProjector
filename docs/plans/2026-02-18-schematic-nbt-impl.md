# Schematic / NBT File Support Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let players load `.nbt` structure files into the Multiblock Projector for building assistance, with optional Create mod integration and shipped example files.

**Architecture:** A `SchematicIndex` scans folders on GUI open, producing `SchematicEntry` records that lazily create synthetic `MultiblockDefinition` objects. `Settings` gains a `Source` enum to distinguish registry vs schematic lookups. The GUI merges tabs/entries from both `MultiblockIndex` and `SchematicIndex`. Rendering adds red tint for incorrect blocks and air violations.

**Tech Stack:** NeoForge 1.21.1, vanilla `NbtIo`/`StructureTemplate` for NBT reading, existing `MultiblockDefinition` API for projection integration.

**Design document:** `docs/plans/2026-02-18-schematic-nbt-support-design.md`

**Architectural simplification:** The design proposed `IProjectorEntry` as a common interface. Instead, `SchematicEntry` produces synthetic `MultiblockDefinition` objects via `toDefinition()`. This avoids refactoring the entire GUI/preview/projection pipeline (which is deeply coupled to `MultiblockDefinition`) while achieving the same result. The only code that needs to know about schematics vs registry entries is `Settings` (dual lookup) and `ProjectorScreen` (tab merging).

---

### Task 1: Create SchematicLoader utility

**Why:** Core utility that reads `.nbt` structure files and converts them to `MultiblockStructure`. Pure utility with no dependencies on other new code. Everything else builds on this.

**Files:**
- Create: `src/main/java/com/multiblockprojector/client/schematic/SchematicLoader.java`

**Step 1: Write SchematicLoader**

```java
package com.multiblockprojector.client.schematic;

import com.multiblockprojector.api.BlockEntry;
import com.multiblockprojector.api.MultiblockStructure;
import com.multiblockprojector.api.SingleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NbtUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads .nbt structure files (vanilla StructureTemplate format) and converts
 * them to MultiblockStructure for use in the projector.
 */
public class SchematicLoader {

    /**
     * Read just the dimensions from an .nbt file without parsing all blocks.
     * Returns null if the file is unreadable or has no size tag.
     */
    @Nullable
    public static BlockPos readSize(Path nbtFile) {
        try {
            CompoundTag root = NbtIo.readCompressed(nbtFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            if (root.contains("size", Tag.TAG_LIST)) {
                ListTag sizeTag = root.getList("size", Tag.TAG_INT);
                if (sizeTag.size() == 3) {
                    return new BlockPos(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
                }
            }
        } catch (IOException e) {
            // File unreadable — will be skipped
        }
        return null;
    }

    /**
     * Fully load an .nbt file and convert to MultiblockStructure.
     * Air blocks are included as SingleBlock(AIR) for validation.
     * Structure void blocks are omitted.
     * Returns null if the file is unreadable or produces an empty structure.
     */
    @Nullable
    public static MultiblockStructure load(Path nbtFile) {
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(nbtFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            return null;
        }

        // Parse palette
        if (!root.contains("palette", Tag.TAG_LIST) || !root.contains("blocks", Tag.TAG_LIST)) {
            return null;
        }

        ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = paletteTag.getCompound(i);
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), entry);
            palette.add(state);
        }

        // Parse block positions
        ListTag blocksTag = root.getList("blocks", Tag.TAG_COMPOUND);
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        boolean hasNonAirBlock = false;

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) continue;

            BlockState state = palette.get(stateIndex);

            // Skip structure voids entirely
            if (state.is(Blocks.STRUCTURE_VOID)) continue;

            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() != 3) continue;
            BlockPos pos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));

            // Include air blocks (for validation) and solid blocks
            blocks.put(pos, new SingleBlock(state));

            if (!state.isAir()) {
                hasNonAirBlock = true;
            }
        }

        // Skip structures that are only air/empty
        if (!hasNonAirBlock || blocks.isEmpty()) {
            return null;
        }

        return new MultiblockStructure(blocks);
    }
}
```

**Step 2: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/java/com/multiblockprojector/client/schematic/SchematicLoader.java
git commit -m "feat: add SchematicLoader utility for reading .nbt structure files"
```

---

### Task 2: Create SchematicEntry record and SchematicIndex

**Why:** SchematicEntry holds metadata for a discovered schematic file and can produce a synthetic `MultiblockDefinition`. SchematicIndex scans folders, caches results, and provides tab/lookup APIs — the parallel dynamic index alongside the registry-based `MultiblockIndex`.

**Files:**
- Create: `src/main/java/com/multiblockprojector/client/schematic/SchematicEntry.java`
- Create: `src/main/java/com/multiblockprojector/client/schematic/SchematicIndex.java`

**Reference files:**
- `src/main/java/com/multiblockprojector/common/registry/MultiblockIndex.java` — parallel registry index with `TabEntry`, `getForTab()`, `getTabs()`, `getById()` patterns
- `api/src/main/java/com/multiblockprojector/api/MultiblockDefinition.java` — `fixed()` factory, `SizeVariant`, `StructureProvider`

**Step 1: Write SchematicEntry**

```java
package com.multiblockprojector.client.schematic;

import com.multiblockprojector.api.MultiblockCategory;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Represents a discovered .nbt schematic file.
 * Can lazily produce a MultiblockDefinition for use in the projection system.
 */
public record SchematicEntry(
    ResourceLocation id,
    Component displayName,
    String tabId,
    Component tabDisplayName,
    Path filePath,
    BlockPos size
) {
    private static final MultiblockCategory SCHEMATIC_CATEGORY = new MultiblockCategory(
        ResourceLocation.fromNamespaceAndPath("multiblockprojector", "schematic"),
        Component.literal("Schematic")
    );

    /**
     * Create a synthetic MultiblockDefinition from this schematic entry.
     * The structure is loaded lazily on first projection/preview.
     */
    public MultiblockDefinition toDefinition() {
        // Capture filePath for the lambda — avoid capturing 'this' record
        Path path = this.filePath;
        return MultiblockDefinition.fixed(
            displayName,
            tabId,
            SCHEMATIC_CATEGORY,
            size,
            (variant, level) -> {
                MultiblockStructure structure = SchematicLoader.load(path);
                if (structure == null) {
                    throw new IllegalStateException("Failed to load schematic: " + path);
                }
                return structure;
            }
        );
    }

    /**
     * Convert a raw file/folder name to a display name.
     * "my_cool_build" -> "My Cool Build"
     * "bobs-tools" -> "Bobs Tools"
     */
    public static String prettifyName(String raw) {
        String spaced = raw.replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : spaced.toCharArray()) {
            if (c == ' ') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
```

**Step 2: Write SchematicIndex**

```java
package com.multiblockprojector.client.schematic;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.registry.MultiblockIndex.TabEntry;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dynamic index of .nbt schematic files from disk.
 * Rescanned on each GUI open (file listing only, no NBT parsing).
 * Parallel to MultiblockIndex which indexes the frozen NeoForge registry.
 */
public class SchematicIndex {

    private static final String CUSTOM_TAB_ID = "multiblockprojector_custom_nbts";
    private static final String CUSTOM_TAB_NAME = "Custom NBTs";
    private static final String CREATE_TAB_ID = "multiblockprojector_create_schematics";
    private static final String CREATE_TAB_NAME = "Create Schematics";

    private static SchematicIndex INSTANCE;

    private final List<TabEntry> tabs;
    private final Map<String, List<MultiblockDefinition>> byTab;
    private final List<MultiblockDefinition> all;
    private final Map<ResourceLocation, SchematicEntry> entriesById;

    private SchematicIndex(List<TabEntry> tabs,
                          Map<String, List<MultiblockDefinition>> byTab,
                          List<MultiblockDefinition> all,
                          Map<ResourceLocation, SchematicEntry> entriesById) {
        this.tabs = tabs;
        this.byTab = byTab;
        this.all = all;
        this.entriesById = entriesById;
    }

    /** Get cached instance, or scan if not yet initialized. */
    public static SchematicIndex get() {
        if (INSTANCE == null) {
            INSTANCE = scan();
        }
        return INSTANCE;
    }

    /** Force a rescan (called on GUI open). */
    public static void rescan() {
        INSTANCE = scan();
    }

    /** Clear cached data. */
    public static void invalidate() {
        INSTANCE = null;
    }

    public List<TabEntry> getTabs() { return tabs; }
    public List<MultiblockDefinition> getAll() { return all; }

    public List<MultiblockDefinition> getForTab(String tabId) {
        return byTab.getOrDefault(tabId, List.of());
    }

    @Nullable
    public SchematicEntry getEntryById(ResourceLocation id) {
        return entriesById.get(id);
    }

    /** Check if a given tab ID belongs to the schematic index. */
    public boolean hasTab(String tabId) {
        return byTab.containsKey(tabId);
    }

    private static SchematicIndex scan() {
        List<SchematicEntry> entries = new ArrayList<>();

        // 1. Always scan config/multiblockprojector/schematics/
        Path schematicsDir = FMLPaths.CONFIGDIR.get().resolve("multiblockprojector").resolve("schematics");
        if (Files.isDirectory(schematicsDir)) {
            scanCustomFolder(schematicsDir, entries);
        }

        // 2. Scan Create's schematics/ folder if Create is loaded
        if (ModList.get().isLoaded("create")) {
            Path createDir = FMLPaths.GAMEDIR.get().resolve("schematics");
            if (Files.isDirectory(createDir)) {
                scanCreateFolder(createDir, entries);
            }
        }

        // Build index
        Map<ResourceLocation, SchematicEntry> entriesById = new LinkedHashMap<>();
        Map<String, List<MultiblockDefinition>> byTab = new LinkedHashMap<>();
        List<MultiblockDefinition> all = new ArrayList<>();

        for (SchematicEntry entry : entries) {
            entriesById.put(entry.id(), entry);
            MultiblockDefinition def = entry.toDefinition();
            byTab.computeIfAbsent(entry.tabId(), k -> new ArrayList<>()).add(def);
            all.add(def);
        }

        // Sort each tab's entries alphabetically
        for (List<MultiblockDefinition> list : byTab.values()) {
            list.sort(Comparator.comparing(d -> d.displayName().getString(), String.CASE_INSENSITIVE_ORDER));
        }
        all.sort(Comparator.comparing(d -> d.displayName().getString(), String.CASE_INSENSITIVE_ORDER));

        // Build tab list sorted alphabetically
        List<TabEntry> tabs = byTab.entrySet().stream()
            .map(e -> {
                // Get display name from first entry's tabDisplayName
                MultiblockDefinition first = e.getValue().get(0);
                // Find the SchematicEntry for this tab to get the display name
                String displayName = entries.stream()
                    .filter(se -> se.tabId().equals(e.getKey()))
                    .findFirst()
                    .map(se -> se.tabDisplayName().getString())
                    .orElse(e.getKey());
                return new TabEntry(e.getKey(), displayName);
            })
            .sorted(Comparator.comparing(TabEntry::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        return new SchematicIndex(
            Collections.unmodifiableList(tabs),
            Collections.unmodifiableMap(byTab),
            Collections.unmodifiableList(all),
            Collections.unmodifiableMap(entriesById)
        );
    }

    /**
     * Scan config/multiblockprojector/schematics/ with subfolder-as-tab logic.
     * Loose files -> "Custom NBTs" tab.
     * First-level subfolders -> prettified tab names.
     * Deeper files flatten to their first-level subfolder's tab.
     */
    private static void scanCustomFolder(Path root, List<SchematicEntry> entries) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".nbt") && Files.isRegularFile(p))
                .forEach(nbtFile -> {
                    Path relative = root.relativize(nbtFile);
                    String fileName = nbtFile.getFileName().toString();
                    String baseName = fileName.substring(0, fileName.length() - 4); // strip .nbt

                    // Determine tab from first-level subfolder
                    String tabId;
                    Component tabDisplayName;
                    if (relative.getNameCount() == 1) {
                        // Loose file in root
                        tabId = CUSTOM_TAB_ID;
                        tabDisplayName = Component.literal(CUSTOM_TAB_NAME);
                    } else {
                        // In a subfolder — tab is the first directory component
                        String subfolderName = relative.getName(0).toString();
                        tabId = "multiblockprojector_custom_" + subfolderName.toLowerCase().replace(' ', '_');
                        tabDisplayName = Component.literal(SchematicEntry.prettifyName(subfolderName));
                    }

                    // Build path-based ID
                    String idPath = "custom/" + relative.toString().replace('\\', '/');
                    idPath = idPath.substring(0, idPath.length() - 4); // strip .nbt
                    // ResourceLocation paths must be lowercase with valid chars
                    idPath = idPath.toLowerCase().replaceAll("[^a-z0-9/_.-]", "_");
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("multiblockprojector", idPath);

                    // Read size (quick, doesn't parse all blocks)
                    BlockPos size = SchematicLoader.readSize(nbtFile);
                    if (size == null) return; // unreadable file, skip

                    Component displayName = Component.literal(SchematicEntry.prettifyName(baseName));

                    entries.add(new SchematicEntry(id, displayName, tabId, tabDisplayName, nbtFile, size));
                });
        } catch (IOException e) {
            // Folder walk failed — no schematics
        }
    }

    /**
     * Scan Create's schematics/ folder. All files go under "Create Schematics" tab.
     * No subfolder-as-tab logic for Create's folder.
     */
    private static void scanCreateFolder(Path root, List<SchematicEntry> entries) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".nbt") && Files.isRegularFile(p))
                .forEach(nbtFile -> {
                    Path relative = root.relativize(nbtFile);
                    String fileName = nbtFile.getFileName().toString();
                    String baseName = fileName.substring(0, fileName.length() - 4);

                    String idPath = "create/" + relative.toString().replace('\\', '/');
                    idPath = idPath.substring(0, idPath.length() - 4);
                    idPath = idPath.toLowerCase().replaceAll("[^a-z0-9/_.-]", "_");
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("multiblockprojector", idPath);

                    BlockPos size = SchematicLoader.readSize(nbtFile);
                    if (size == null) return;

                    Component displayName = Component.literal(SchematicEntry.prettifyName(baseName));

                    entries.add(new SchematicEntry(id, displayName, CREATE_TAB_ID,
                        Component.literal(CREATE_TAB_NAME), nbtFile, size));
                });
        } catch (IOException e) {
            // Folder walk failed
        }
    }
}
```

**Step 3: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/multiblockprojector/client/schematic/SchematicEntry.java \
        src/main/java/com/multiblockprojector/client/schematic/SchematicIndex.java
git commit -m "feat: add SchematicEntry record and SchematicIndex for folder scanning"
```

---

### Task 3: Add Source enum to Settings

**Why:** `Settings` needs to distinguish between registry multiblocks and schematic files. When `source == SCHEMATIC`, `getMultiblock()` creates a synthetic `MultiblockDefinition` from the `SchematicIndex`. Old projector items without a source field default to `REGISTRY` for backwards compatibility.

**Files:**
- Modify: `src/main/java/com/multiblockprojector/common/projector/Settings.java`

**Reference:** Read `Settings.java` fully before editing. Key integration points:
- Lines 27-35: NBT key constants — add `KEY_SOURCE`
- Lines 37-43: Fields — add `source` field
- Lines 63-90: NBT read constructor — read source
- Lines 127-148: `getMultiblock()` / `setMultiblock()` — add schematic path
- Lines 163-184: `toNbt()` — write source

**Step 1: Add Source enum, field, and NBT key**

Add the `Source` enum inside Settings (before or after `Mode`), the `KEY_SOURCE` constant, and the `source` field.

In `Settings.java`:
- Add constant: `public static final String KEY_SOURCE = "source";`
- Add field: `private Source source = Source.REGISTRY;`
- Add enum:
```java
public enum Source {
    REGISTRY, SCHEMATIC
}
```

**Step 2: Update NBT read constructor (lines 63-90)**

In the `else` block of `Settings(CompoundTag settingsNbt)`, add after line 75 (`this.sizePresetIndex = ...`):
```java
this.source = settingsNbt.contains(KEY_SOURCE)
    ? Source.values()[Mth.clamp(settingsNbt.getInt(KEY_SOURCE), 0, Source.values().length - 1)]
    : Source.REGISTRY; // backwards compatibility
```

**Step 3: Update toNbt() (lines 163-184)**

Add after line 169 (`nbt.putInt(KEY_SIZE_PRESET, ...)`):
```java
nbt.putInt(KEY_SOURCE, this.source.ordinal());
```

**Step 4: Update getMultiblock() (lines 127-131)**

Replace the entire `getMultiblock()` method:
```java
@Nullable
public MultiblockDefinition getMultiblock() {
    if (multiblockId == null) return null;
    if (source == Source.SCHEMATIC) {
        var entry = com.multiblockprojector.client.schematic.SchematicIndex.get().getEntryById(multiblockId);
        return entry != null ? entry.toDefinition() : null;
    }
    return MultiblockIndex.get().getById(multiblockId).orElse(null);
}
```

**Step 5: Add setSchematic() method and getters**

Add after `setMultiblockId()`:
```java
public void setSchematic(com.multiblockprojector.client.schematic.SchematicEntry entry) {
    this.source = Source.SCHEMATIC;
    this.multiblockId = entry.id();
}

public Source getSource() { return this.source; }
public void setSource(Source source) { this.source = source; }
```

Update existing `setMultiblock()` (lines 133-139) to also set source:
```java
public void setMultiblock(@Nullable MultiblockDefinition multiblock) {
    this.source = Source.REGISTRY;
    if (multiblock == null) {
        this.multiblockId = null;
    } else {
        this.multiblockId = MultiblockIndex.get().getId(multiblock).orElse(null);
    }
}
```

**Step 6: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/main/java/com/multiblockprojector/common/projector/Settings.java
git commit -m "feat: add Source enum to Settings for registry vs schematic lookup"
```

---

### Task 4: Integrate SchematicIndex with ProjectorScreen GUI

**Why:** The GUI needs to display schematic tabs alongside registry tabs, let players select schematics, and handle the selection flow correctly (storing schematic source in Settings).

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java`

**Reference:** Read `ProjectorScreen.java` fully before editing. Key integration points:
- Lines 36-37: `filteredMultiblocks` field and `selectedTab` — type changes
- Lines 60-75: Constructor — merge tabs from both indexes
- Lines 77-79: `updateFilteredMultiblocks()` — merge entries from both indexes
- Lines 158-166: `getSelectedTabDisplayName()` — check both indexes
- Lines 199-216: `selectMultiblockForPreview()` — needs to track source
- Lines 218-241: `selectMultiblock()` — use appropriate Settings method
- Lines 499-527: `ModSelectorScreen.init()` — merge tabs from both indexes

**Step 1: Add SchematicIndex import and tracking field**

Add import at top:
```java
import com.multiblockprojector.client.schematic.SchematicEntry;
import com.multiblockprojector.client.schematic.SchematicIndex;
```

Add field near `selectedMultiblock` (around line 56):
```java
private boolean selectedIsSchematic = false;
private ResourceLocation selectedSchematicId = null;
```

**Step 2: Update constructor to trigger schematic scan and merge tabs**

In the constructor (lines 60-75), after `var tabs = index.getTabs();` (line 68), add:
```java
SchematicIndex.rescan(); // rescan folders on GUI open
```

The tab selection logic can remain as-is — it checks if `lastSelectedTab` exists in registry tabs. We'll also need to check schematic tabs. Update the tab validation (lines 69-73):
```java
var schematicIndex = SchematicIndex.get();
var allTabs = new java.util.ArrayList<>(tabs);
allTabs.addAll(schematicIndex.getTabs());
if (lastSelectedTab != null && allTabs.stream().anyMatch(t -> t.modId().equals(lastSelectedTab))) {
    this.selectedTab = lastSelectedTab;
} else {
    this.selectedTab = tabs.size() > 1 ? tabs.get(1).modId() : MultiblockIndex.ALL_TAB;
}
```

**Step 3: Update updateFilteredMultiblocks() to merge both sources**

Replace `updateFilteredMultiblocks()` (lines 77-79):
```java
private void updateFilteredMultiblocks() {
    var schematicIndex = SchematicIndex.get();
    if (MultiblockIndex.ALL_TAB.equals(selectedTab)) {
        // "All" tab: merge registry + all schematics
        var merged = new java.util.ArrayList<>(MultiblockIndex.get().getForTab(selectedTab));
        merged.addAll(schematicIndex.getAll());
        merged.sort(java.util.Comparator.comparing(d -> d.displayName().getString(), String.CASE_INSENSITIVE_ORDER));
        filteredMultiblocks = merged;
    } else if (schematicIndex.hasTab(selectedTab)) {
        // Schematic tab
        filteredMultiblocks = schematicIndex.getForTab(selectedTab);
    } else {
        // Registry tab
        filteredMultiblocks = MultiblockIndex.get().getForTab(selectedTab);
    }
}
```

**Step 4: Update getSelectedTabDisplayName() to check both indexes**

Replace `getSelectedTabDisplayName()` (lines 158-166):
```java
private String getSelectedTabDisplayName() {
    var registryTabs = MultiblockIndex.get().getTabs();
    for (var tab : registryTabs) {
        if (tab.modId().equals(selectedTab)) return tab.displayName();
    }
    var schematicTabs = SchematicIndex.get().getTabs();
    for (var tab : schematicTabs) {
        if (tab.modId().equals(selectedTab)) return tab.displayName();
    }
    return "All";
}
```

**Step 5: Update selectMultiblock() to handle schematic source**

In `selectMultiblock()` (lines 218-241), replace lines 221-224:
```java
if (selectedIsSchematic && selectedSchematicId != null) {
    var entry = SchematicIndex.get().getEntryById(selectedSchematicId);
    if (entry != null) {
        settings.setSchematic(entry);
    } else {
        return; // schematic gone
    }
} else {
    settings.setMultiblock(multiblock);
}
settings.setMode(Settings.Mode.PROJECTION);
settings.setSizePresetIndex(currentSizePresetIndex);
settings.applyTo(projectorStack);
```

**Step 6: Track schematic source in selectMultiblockForPreview()**

We need to know whether the selected MultiblockDefinition came from a schematic. Since SchematicIndex creates synthetic definitions, we can check by looking up the definition's modId (which is the schematic tabId) in the SchematicIndex.

In `selectMultiblockForPreview()` (line 199), add after `this.selectedMultiblock = multiblock;`:
```java
// Determine if this is a schematic entry by checking if its modId is a schematic tab
var schematicIndex = SchematicIndex.get();
this.selectedIsSchematic = schematicIndex.hasTab(multiblock.modId());
if (selectedIsSchematic) {
    // Find the SchematicEntry by matching display name and tab
    this.selectedSchematicId = schematicIndex.getAll().stream()
        .flatMap(d -> schematicIndex.getTabs().stream())
        .findFirst()
        .map(t -> (ResourceLocation) null)
        .orElse(null);
    // Better approach: look through entries by tab + display name
    for (var tab : schematicIndex.getTabs()) {
        if (tab.modId().equals(multiblock.modId())) {
            var tabDefs = schematicIndex.getForTab(tab.modId());
            int idx = tabDefs.indexOf(multiblock);
            if (idx >= 0) {
                // Get the SchematicEntry at same position
                // SchematicIndex keeps entries in same order as definitions
            }
        }
    }
}
```

Actually, this approach is fragile. Better solution: **add a method to SchematicIndex that looks up a SchematicEntry by its generated MultiblockDefinition's display name + tab**. OR, even simpler: store a `Map<MultiblockDefinition, ResourceLocation>` in SchematicIndex that maps synthetic definitions back to their schematic IDs.

**Revised Step 6:** Add a reverse-lookup map to `SchematicIndex`:

In `SchematicIndex.java`, add field:
```java
private final Map<MultiblockDefinition, ResourceLocation> definitionToId;
```

In the `scan()` method, populate it alongside existing maps:
```java
Map<MultiblockDefinition, ResourceLocation> definitionToId = new IdentityHashMap<>();
// ... in the loop:
definitionToId.put(def, entry.id());
```

Add constructor parameter and getter:
```java
@Nullable
public ResourceLocation getSchematicId(MultiblockDefinition definition) {
    return definitionToId.get(definition);
}
```

Then in `ProjectorScreen.selectMultiblockForPreview()`:
```java
this.selectedIsSchematic = schematicIndex.hasTab(multiblock.modId());
this.selectedSchematicId = selectedIsSchematic ? schematicIndex.getSchematicId(multiblock) : null;
```

**Step 7: Update ModSelectorScreen.init() to show schematic tabs**

In `ModSelectorScreen.init()` (line 503), after `var tabs = MultiblockIndex.get().getTabs();`, merge schematic tabs:
```java
var allTabs = new java.util.ArrayList<>(tabs);
allTabs.addAll(SchematicIndex.get().getTabs());
```

Then update line 506 and line 523 to use `allTabs` instead of `tabs`:
```java
int contentHeight = allTabs.size() * listItemHeight;
// ...
for (TabEntry tab : allTabs) {
    tabList.addTabEntry(tab, tab.modId().equals(currentTabId));
}
```

**Step 8: Hide size buttons for schematic entries**

Schematic entries are always single-size. The existing code at line 135 (`selectedMultiblock.isVariableSize()`) handles this — `MultiblockDefinition.fixed()` creates a single variant, so `isVariableSize()` returns false. No changes needed.

**Step 9: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 10: Commit**

```bash
git add src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java \
        src/main/java/com/multiblockprojector/client/schematic/SchematicIndex.java
git commit -m "feat: integrate SchematicIndex with projector GUI tabs and selection"
```

---

### Task 5: Verify and add red tint rendering for incorrect blocks

**Why:** The design specifies red tinting for incorrect solid blocks and air violations. Before implementing, we must exhaustively verify this doesn't already exist. The existing `BlockValidationManager.isIncorrectBlock()` method exists but is never called from the renderer.

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/render/ProjectionRenderer.java`

**Reference:** Read `ProjectionRenderer.java` fully before editing. Key integration points:
- Lines 84-118: `renderProjection()` loop — where blocks are processed per layer
- Lines 89-97: Current skip logic — skips air blocks AND blocks where world is non-air
- Lines 145-180: `renderGhostQuad()` — where color is applied (line 176: white with alpha)

**Step 1: VERIFY red tint doesn't exist**

Search the ENTIRE codebase for any red tinting, incorrect block rendering, or color variation in ghost blocks:
- Search for: `setColor`, `0.0f, 0.0f` (red channel patterns), `red`, `incorrect`, `isIncorrect`, `tint`
- Search ALL files in `src/main/java/`, not just the render package
- Check for any mixins, shaders, or other render hooks
- Confirm: `isIncorrectBlock()` in `BlockValidationManager` has zero callers besides its own declaration

Document the verification result before proceeding.

**Step 2: Modify renderProjection() to show red-tinted ghosts for incorrect blocks**

Currently (lines 89-97):
```java
// Don't render air blocks
if (ghostState.isAir()) {
    return false;
}
// Don't render if there's already a block here
if (!level.getBlockState(worldPos).isAir()) {
    return false;
}
```

Replace with:
```java
BlockState worldState = level.getBlockState(worldPos);

if (ghostState.isAir()) {
    // Air entry: render red tint if something occupies the space
    if (!worldState.isAir()) {
        // Render the WORLD block with red tint to show it shouldn't be here
        poseStack.pushPose();
        double x = worldPos.getX() - cameraPos.x;
        double y = worldPos.getY() - cameraPos.y;
        double z = worldPos.getZ() - cameraPos.z;
        poseStack.translate(x, y, z);
        try {
            renderGhostBlock(worldState, poseStack, buffer, level, worldPos, true);
        } catch (Exception e) {
            // Ignore rendering errors
        }
        poseStack.popPose();
    }
    return false;
}

if (!worldState.isAir()) {
    // Solid entry with block already placed — check if it's correct
    if (!info.blockEntry.matches(worldState)) {
        // Wrong block: render expected block with red tint
        poseStack.pushPose();
        double x = worldPos.getX() - cameraPos.x;
        double y = worldPos.getY() - cameraPos.y;
        double z = worldPos.getZ() - cameraPos.z;
        poseStack.translate(x, y, z);
        try {
            renderGhostBlock(ghostState, poseStack, buffer, level, worldPos, true);
        } catch (Exception e) {
            // Ignore rendering errors
        }
        poseStack.popPose();
    }
    // Correct block or rendered red — either way, skip normal ghost render
    return false;
}
```

**Step 3: Add isIncorrect parameter to renderGhostBlock chain**

Update `renderGhostBlock()` (line 121-124):
```java
private static void renderGhostBlock(BlockState state, PoseStack poseStack, VertexConsumer buffer, Level level, BlockPos pos, boolean isIncorrect) {
    renderGhostBlockManual(state, poseStack, buffer, level, pos, isIncorrect);
}
```

Update `renderGhostBlockManual()` (line 126-143) — pass `isIncorrect` to `renderGhostQuad`:
```java
private static void renderGhostBlockManual(BlockState state, PoseStack poseStack, VertexConsumer buffer, Level level, BlockPos pos, boolean isIncorrect) {
    BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
    var model = blockRenderer.getBlockModel(state);

    for (Direction direction : Direction.values()) {
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(), ModelData.EMPTY, null);
        for (BakedQuad quad : quads) {
            renderGhostQuad(poseStack, buffer, quad, GHOST_LIGHT, isIncorrect);
        }
    }

    List<BakedQuad> generalQuads = model.getQuads(state, null, RandomSource.create(), ModelData.EMPTY, null);
    for (BakedQuad quad : generalQuads) {
        renderGhostQuad(poseStack, buffer, quad, GHOST_LIGHT, isIncorrect);
    }
}
```

**Step 4: Update renderGhostQuad() to apply red tint**

Update signature and color line (line 176):
```java
private static void renderGhostQuad(PoseStack poseStack, VertexConsumer buffer, BakedQuad quad, int light, boolean isIncorrect) {
```

Change the color application (line 176):
```java
// Red tint for incorrect, white for normal ghost
float r = isIncorrect ? 1.0f : 1.0f;
float g = isIncorrect ? 0.3f : 1.0f;
float b = isIncorrect ? 0.3f : 1.0f;
buffer.addVertex(pos.x(), pos.y(), pos.z())
      .setColor(r, g, b, GHOST_ALPHA)
      .setUv(u, v)
      .setLight(light)
      .setNormal(normal.x(), normal.y(), normal.z());
```

**Step 5: Fix the original (non-incorrect) call site**

The normal ghost rendering path (around line 109 in `renderProjection`) currently calls `renderGhostBlock` without the `isIncorrect` parameter. Update it to pass `false`:
```java
renderGhostBlock(ghostState, poseStack, buffer, level, worldPos, false);
```

**Step 6: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/main/java/com/multiblockprojector/client/render/ProjectionRenderer.java
git commit -m "feat: add red tint rendering for incorrect and air-violation blocks"
```

---

### Task 6: Update BlockValidationManager for air block validation

**Why:** Currently `BlockValidationManager` skips air entries entirely (lines 34-37). With schematic support, air entries (`SingleBlock(AIR)`) need validation — if something occupies a position that should be air, it's incorrect.

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/BlockValidationManager.java`

**Reference:** Read `BlockValidationManager.java` fully before editing. Key sections:
- Lines 33-37: `validateProjection()` skips air display states — CHANGE THIS
- Lines 119-123: `isProjectionComplete()` skips air display states — CHANGE THIS

**Step 1: Update validateProjection() air handling**

In `validateProjection()`, replace lines 33-47:
```java
// Don't validate air block entries
BlockState displayState = info.getDisplayState(level, worldPos, 0);
if (displayState.isAir()) {
    return false;
}

// Check if the actual block matches the requirement
boolean matches = info.matches(actualState);

if (!matches) {
    // Block is incorrect if it's not air and doesn't match
    if (!actualState.isAir()) {
        incorrectBlocks.add(worldPos.immutable());
    }
}
```

With:
```java
BlockState displayState = info.getDisplayState(level, worldPos, 0);

if (displayState.isAir()) {
    // Air entry: incorrect if something occupies this position
    if (!actualState.isAir()) {
        incorrectBlocks.add(worldPos.immutable());
    }
    return false;
}

// Solid block entry: check if the actual block matches
boolean matches = info.matches(actualState);
if (!matches && !actualState.isAir()) {
    incorrectBlocks.add(worldPos.immutable());
}
```

**Step 2: Update isProjectionComplete() air handling**

In `isProjectionComplete()`, replace lines 119-133:
```java
// Skip air block entries
BlockState displayState = info.getDisplayState(level, worldPos, 0);
if (displayState.isAir()) {
    return false;
}

// Check if block is missing or incorrect
boolean matches = info.matches(actualState);

if (actualState.isAir() || !matches) {
    hasIncompleteBlocks[0] = true;
    return true; // Stop processing
}
```

With:
```java
BlockState displayState = info.getDisplayState(level, worldPos, 0);

if (displayState.isAir()) {
    // Air entry: incomplete if something occupies this position
    if (!actualState.isAir()) {
        hasIncompleteBlocks[0] = true;
        return true;
    }
    return false;
}

// Solid block entry: incomplete if missing or wrong block
boolean matches = info.matches(actualState);
if (actualState.isAir() || !matches) {
    hasIncompleteBlocks[0] = true;
    return true;
}
```

**Step 3: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/java/com/multiblockprojector/client/BlockValidationManager.java
git commit -m "feat: add air block validation to BlockValidationManager"
```

---

### Task 7: Example NBT files and first-launch auto-copy

**Why:** Ship example `.nbt` structure files so players see something immediately. On first launch, copy from JAR resources to `config/multiblockprojector/schematics/examples/`. A `.initialized` marker prevents re-copying if deleted.

**Files:**
- Create: `src/main/java/com/multiblockprojector/client/schematic/SchematicExampleCopier.java`
- Create: Example `.nbt` files in `src/main/resources/assets/multiblockprojector/examples/`

**Reference:**
- `src/testmod/java/com/multiblockprojector/test/TestMultiblockRegistrar.java` — the test structures to convert
- The `.nbt` format: We need to create structure files. Since we can't easily generate them programmatically without a running game, we'll create a helper script that runs during `runData` or we'll capture them manually in dev.

**Step 1: Write SchematicExampleCopier**

```java
package com.multiblockprojector.client.schematic;

import com.multiblockprojector.UniversalProjector;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Copies example .nbt files from JAR resources to the config schematics folder
 * on first launch. A .initialized marker file prevents re-copying.
 */
public class SchematicExampleCopier {

    private static final String RESOURCE_PREFIX = "/assets/multiblockprojector/examples/";
    private static final List<String> EXAMPLE_FILES = List.of(
        "test-furnace.nbt",
        "test-beacon.nbt",
        "test-tower.nbt",
        "test-wall.nbt"
    );

    /**
     * Copy example files if not already initialized.
     * Call this during mod setup or before first GUI open.
     */
    public static void copyIfNeeded() {
        Path schematicsDir = FMLPaths.CONFIGDIR.get()
            .resolve("multiblockprojector").resolve("schematics");
        Path markerFile = schematicsDir.resolve(".initialized");

        if (Files.exists(markerFile)) {
            return; // Already initialized
        }

        Path examplesDir = schematicsDir.resolve("examples");
        try {
            Files.createDirectories(examplesDir);

            for (String fileName : EXAMPLE_FILES) {
                try (InputStream is = SchematicExampleCopier.class.getResourceAsStream(RESOURCE_PREFIX + fileName)) {
                    if (is != null) {
                        Files.copy(is, examplesDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Create marker file
            Files.createFile(markerFile);
            UniversalProjector.LOGGER.info("Copied example schematics to {}", examplesDir);
        } catch (IOException e) {
            UniversalProjector.LOGGER.warn("Failed to copy example schematics", e);
        }
    }
}
```

**Step 2: Call the copier on mod setup**

Find the mod's main class or client setup event handler and add:
```java
SchematicExampleCopier.copyIfNeeded();
```

This should be called during `FMLClientSetupEvent` or early in the mod lifecycle. Check where `ClientProxy` or client-side init runs and add the call there.

**Step 3: Create example .nbt files**

The example `.nbt` files need to be created by capturing the test multiblock structures in-game using a Structure Block or Create's Schematic and Quill, then placing them in `src/main/resources/assets/multiblockprojector/examples/`.

Alternative approach: Write a data generator or `runData` task that programmatically creates the `.nbt` files from the test multiblock definitions. This is more robust than manual capture.

Create a simple `ExampleNbtGenerator` that runs during data generation:

```java
// In a data generator class, for each test structure:
StructureTemplate template = new StructureTemplate();
// Fill template with blocks from buildTestFurnace() etc.
// Save using NbtIo.writeCompressed()
```

**NOTE:** The actual `.nbt` file generation requires a running Minecraft data generation context. The implementing engineer should:
1. First implement all other tasks
2. Run `./gradlew runClient` with the testmod still present
3. Use Structure Blocks in-game to capture the 4 test structures
4. Save the resulting `.nbt` files to `src/main/resources/assets/multiblockprojector/examples/`
5. OR write a data generator (preferred, more maintainable)

**Step 4: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/com/multiblockprojector/client/schematic/SchematicExampleCopier.java
# Also add example .nbt files once created
git commit -m "feat: add first-launch example schematic copier"
```

---

### Task 8: Remove testmod source set

**Why:** Test multiblocks are now shipped as example `.nbt` files. The `src/testmod/` source set and `TestMultiblockRegistrar` are no longer needed.

**Prerequisites:** Task 7 complete — example `.nbt` files are in place and loadable.

**Files:**
- Delete: `src/testmod/java/com/multiblockprojector/test/TestMultiblockRegistrar.java`
- Delete: `src/testmod/` directory
- Modify: `build.gradle`

**Reference:** Read `build.gradle` fully before editing. Key sections:
- Lines 44-51: `sourceSets { testmod { ... } }` — REMOVE
- Lines 61-65: `mods { multiblockprojector { sourceSet sourceSets.main; sourceSet sourceSets.testmod } }` — remove testmod reference

**Step 1: Remove testmod from build.gradle sourceSets**

Delete the `testmod` source set block (lines 44-51):
```groovy
// DELETE this entire block:
testmod {
    java {
        compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
    }
}
```

**Step 2: Remove testmod from neoForge mods block**

In the `neoForge { mods { multiblockprojector { ... } } }` section, remove the `sourceSet sourceSets.testmod` line. Change:
```groovy
sourceSet sourceSets.main
sourceSet sourceSets.testmod
```
To:
```groovy
sourceSet sourceSets.main
```

**Step 3: Delete testmod directory**

```bash
rm -rf src/testmod/
```

**Step 4: Compile to verify**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add build.gradle
git rm -r src/testmod/
git commit -m "chore: remove testmod source set, examples now ship as .nbt files"
```

---

### Task 9: Integration testing and edge cases

**Why:** Verify the full flow works end-to-end with manual testing in the dev client.

**Step 1: Create test schematics folder**

```bash
mkdir -p run/config/multiblockprojector/schematics/test-pack
```

**Step 2: Run dev client**

Run: `./gradlew runClient`

**Step 3: Manual test checklist**

1. **First-launch copy:** Verify `config/multiblockprojector/schematics/examples/` is created with `.nbt` files
2. **Capture a test structure:** Use a Structure Block to save a small structure as `.nbt`, place it in the `test-pack` subfolder
3. **Open projector GUI:** Verify tabs show:
   - Registry tabs first (Vanilla, etc.)
   - Then "Examples" tab (from auto-copied files)
   - Then "Test Pack" tab (from manual subfolder)
4. **Select a schematic:** Click an entry, verify 3D preview renders correctly
5. **Project schematic:** Select and enter projection mode, verify ghost blocks appear
6. **Place projection:** Left-click to anchor, verify building mode works
7. **Incorrect block test:** Place wrong block in a required position, verify red tint appears
8. **Air violation test:** In a schematic with air entries, place a block where air should be, verify red tint on that block
9. **Completion test:** Build the full structure correctly, verify "Multiblock structure completed!" message
10. **Schematic persistence:** Close and reopen GUI, verify the selected schematic is remembered
11. **File deletion:** Delete a schematic file while it's selected, reopen GUI, verify graceful handling
12. **Create integration (if Create available):** Place `.nbt` in `schematics/` folder, verify "Create Schematics" tab appears

**Step 4: Fix any issues found during testing**

Address bugs and edge cases discovered during manual testing.

**Step 5: Final commit**

```bash
git add -A
git commit -m "test: verify schematic/NBT support integration"
```

---

## Task Dependency Graph

```
Task 1 (SchematicLoader) ──┐
                           ├── Task 2 (SchematicEntry + SchematicIndex)
                           │         │
Task 3 (Settings.Source) ──┤         │
                           │         │
                           └── Task 4 (GUI Integration) ── depends on 2, 3

Task 5 (Red tint rendering) ── independent, can run in parallel with 1-4
Task 6 (Air validation) ── independent, can run in parallel with 1-4

Task 7 (Example NBTs) ── depends on 2 (SchematicIndex working)
Task 8 (Remove testmod) ── depends on 7
Task 9 (Integration test) ── depends on ALL previous tasks
```

**Parallel opportunities:** Tasks 1-3 can be done first in sequence. Tasks 5 and 6 are independent of the schematic system and can be done in parallel with or before Tasks 1-4.
