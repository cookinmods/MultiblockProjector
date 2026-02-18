# Multiblock Registry API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the hardcoded multiblock discovery system with a NeoForge custom registry and public API, enabling dynamic tabs and third-party mod registration.

**Architecture:** A separate `api/` Gradle subproject defines the public types (`MultiblockDefinition`, `BlockEntry`, etc.) and registry key. The main mod creates the registry via `RegistryBuilder`, migrates the GUI to read from it, and refactors existing adapters to feed into it at `EventPriority.LOW`. The old `UniversalMultiblockHandler` and related interfaces are deleted once all consumers migrate.

**Tech Stack:** NeoForge 21.1.176, Java 21, Gradle multi-project build with `net.neoforged.moddev` plugin.

**Testing strategy:** This is a Minecraft mod — no unit test framework. Verification is: (1) `./gradlew compileJava` for compilation, (2) `./gradlew build` for full build, (3) `./gradlew runClient` for in-game testing. Each task specifies which verification level is needed.

**Design doc:** `docs/plans/2026-02-17-registry-api-design.md`

---

## Task 1: Create API Gradle subproject structure

**Files:**
- Create: `api/build.gradle`
- Create: `api/src/main/java/com/multiblockprojector/api/package-info.java`
- Modify: `settings.gradle`
- Modify: `build.gradle` (root — add dependency on api subproject)

**Step 1: Create `api/build.gradle`**

```groovy
plugins {
    id('java')
}

group = 'com.multiblockprojector'
version = rootProject.version
base.archivesName = 'projector-api'

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of("${java_version}"))
    }
    withSourcesJar()
}

dependencies {
    compileOnly "net.neoforged:neoforge:${neo_version}"
}
```

**Step 2: Create `api/src/main/java/com/multiblockprojector/api/package-info.java`**

```java
/**
 * Public API for the Multiblock Projector mod.
 * Third-party mods depend on this module to register their multiblocks.
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.multiblockprojector.api;
```

Note: Skip the `@API` annotation — it was removed in modern NeoForge. The package-info is sufficient.

**Step 3: Add api subproject to `settings.gradle`**

Add `include 'api'` at the end of the file.

**Step 4: Add api dependency to root `build.gradle`**

In the `dependencies` block, add: `implementation project(':api')`

**Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (api subproject compiles with NeoForge on classpath)

**Step 6: Commit**

```
feat: scaffold API Gradle subproject

Adds api/ submodule that will contain the public multiblock registry
types for third-party mod integration.
```

---

## Task 2: Create API types — BlockEntry sealed hierarchy

**Files:**
- Create: `api/src/main/java/com/multiblockprojector/api/BlockEntry.java`
- Create: `api/src/main/java/com/multiblockprojector/api/SingleBlock.java`
- Create: `api/src/main/java/com/multiblockprojector/api/BlockGroup.java`

**Step 1: Create `BlockEntry.java`**

```java
package com.multiblockprojector.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Represents a block requirement at a position in a multiblock structure.
 * Sealed to {@link SingleBlock} (exact block) and {@link BlockGroup} (any of several blocks).
 */
public sealed interface BlockEntry permits SingleBlock, BlockGroup {
    /**
     * Returns the block state to display at the given game tick.
     * For {@link SingleBlock}, always returns the same state.
     * For {@link BlockGroup}, cycles through options.
     */
    BlockState displayState(long tick);

    /**
     * Returns true if the placed block state satisfies this entry.
     */
    boolean matches(BlockState placed);
}
```

**Step 2: Create `SingleBlock.java`**

```java
package com.multiblockprojector.api;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entry requiring an exact block type.
 */
public record SingleBlock(BlockState state) implements BlockEntry {
    @Override
    public BlockState displayState(long tick) {
        return state;
    }

    @Override
    public boolean matches(BlockState placed) {
        return placed.is(state.getBlock());
    }
}
```

**Step 3: Create `BlockGroup.java`**

```java
package com.multiblockprojector.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A block entry that accepts any of several block types.
 * Cycles through options in the preview renderer.
 *
 * <p>Note: equality is identity-based on block states.
 * Do not rely on {@code equals()} for deduplication.</p>
 */
public record BlockGroup(Component label, List<BlockState> options) implements BlockEntry {
    @Override
    public BlockState displayState(long tick) {
        return options.get((int) ((tick / 20) % options.size()));
    }

    @Override
    public boolean matches(BlockState placed) {
        return options.stream().anyMatch(o -> placed.is(o.getBlock()));
    }
}
```

**Step 4: Verify compilation**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: add BlockEntry sealed hierarchy to API

SingleBlock for exact matches, BlockGroup for positions accepting
multiple block types (e.g., Blood Magic rune slots).
```

---

## Task 3: Create API types — MultiblockStructure and MultiblockCategory

**Files:**
- Create: `api/src/main/java/com/multiblockprojector/api/MultiblockStructure.java`
- Create: `api/src/main/java/com/multiblockprojector/api/MultiblockCategory.java`

**Step 1: Create `MultiblockStructure.java`**

```java
package com.multiblockprojector.api;

import net.minecraft.core.BlockPos;

import java.util.Map;

/**
 * The block layout of a multiblock structure.
 * Size is auto-computed from the block map bounding box.
 */
public record MultiblockStructure(Map<BlockPos, BlockEntry> blocks, BlockPos size) {

    /**
     * Convenience constructor that auto-computes size from block positions.
     */
    public MultiblockStructure(Map<BlockPos, BlockEntry> blocks) {
        this(Map.copyOf(blocks), computeBounds(blocks));
    }

    private static BlockPos computeBounds(Map<BlockPos, BlockEntry> blocks) {
        int maxX = 0, maxY = 0, maxZ = 0;
        for (BlockPos pos : blocks.keySet()) {
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        return new BlockPos(maxX, maxY, maxZ);
    }
}
```

**Step 2: Create `MultiblockCategory.java`**

```java
package com.multiblockprojector.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Extensible category for organizing multiblocks.
 * Third-party mods can create their own categories.
 */
public record MultiblockCategory(ResourceLocation id, Component displayName) {

    public static final MultiblockCategory PROCESSING = create("processing", "Processing");
    public static final MultiblockCategory POWER = create("power", "Power");
    public static final MultiblockCategory STORAGE = create("storage", "Storage");
    public static final MultiblockCategory CRAFTING = create("crafting", "Crafting");
    public static final MultiblockCategory GENERAL = create("general", "General");

    private static MultiblockCategory create(String path, String name) {
        return new MultiblockCategory(
            ResourceLocation.fromNamespaceAndPath("multiblockprojector", path),
            Component.literal(name)
        );
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: add MultiblockStructure and MultiblockCategory to API

MultiblockStructure auto-computes bounding box from block map.
MultiblockCategory is an extensible record with common constants.
```

---

## Task 4: Create API types — MultiblockDefinition and ProjectorAPI

**Files:**
- Create: `api/src/main/java/com/multiblockprojector/api/MultiblockDefinition.java`
- Create: `api/src/main/java/com/multiblockprojector/api/ProjectorAPI.java`

**Step 1: Create `MultiblockDefinition.java`**

```java
package com.multiblockprojector.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Registry value type for multiblock definitions.
 * Registered via {@code RegisterEvent} into the multiblock registry.
 *
 * @param displayName  Human-readable name shown in GUI
 * @param modId        Plain string mod ID (e.g., "mekanism")
 * @param category     Category for organization
 * @param variants     Size variants; single-element list for fixed-size multiblocks
 * @param structureProvider  Lazy structure generator
 */
public record MultiblockDefinition(
    Component displayName,
    String modId,
    MultiblockCategory category,
    List<SizeVariant> variants,
    StructureProvider structureProvider
) {
    /**
     * A size variant for variable-size multiblocks.
     */
    public record SizeVariant(Component label, BlockPos dimensions, boolean isDefault) {
        public String getSizeString() {
            return dimensions.getX() + "x" + dimensions.getY() + "x" + dimensions.getZ();
        }

        public Component getFullDisplayName() {
            return Component.literal(label.getString() + " (" + getSizeString() + ")");
        }
    }

    /**
     * Creates the multiblock structure for a given size variant.
     *
     * <p>Contract: Must be idempotent, side-effect free, and thread-safe.
     * May be called from any thread. Implementations should not access
     * or modify world state.</p>
     */
    @FunctionalInterface
    public interface StructureProvider {
        MultiblockStructure create(SizeVariant variant, @Nullable Level level);
    }

    /**
     * Convenience factory for single-size multiblocks.
     */
    public static MultiblockDefinition fixed(
        Component name, String modId, MultiblockCategory category,
        BlockPos size, StructureProvider provider
    ) {
        var single = new SizeVariant(
            Component.literal(size.getX() + "x" + size.getY() + "x" + size.getZ()),
            size, true
        );
        return new MultiblockDefinition(name, modId, category, List.of(single), provider);
    }

    /**
     * Returns the default size variant (first one marked as default, or the first variant).
     */
    public SizeVariant getDefaultVariant() {
        return variants.stream()
            .filter(SizeVariant::isDefault)
            .findFirst()
            .orElse(variants.get(0));
    }

    /**
     * Whether this definition has multiple size variants.
     */
    public boolean isVariableSize() {
        return variants.size() > 1;
    }
}
```

**Step 2: Create `ProjectorAPI.java`**

```java
package com.multiblockprojector.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Public API entry point for the Multiblock Projector mod.
 * Contains the registry key that third-party mods use to register multiblocks.
 *
 * <p>Usage in other mods:</p>
 * <pre>{@code
 * @SubscribeEvent
 * public static void onRegister(RegisterEvent event) {
 *     event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
 *         helper.register(rl("mymod", "my_structure"),
 *             MultiblockDefinition.fixed(...));
 *     });
 * }
 * }</pre>
 */
public class ProjectorAPI {

    public static final ResourceKey<Registry<MultiblockDefinition>> MULTIBLOCK_REGISTRY_KEY =
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath("multiblockprojector", "multiblocks"));

    private ProjectorAPI() {}
}
```

**Step 3: Verify compilation**

Run: `./gradlew :api:compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Verify full project compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (both api and main module compile)

**Step 5: Commit**

```
feat: add MultiblockDefinition and ProjectorAPI to API module

MultiblockDefinition is the registry value type with SizeVariant
support and lazy StructureProvider. ProjectorAPI holds the registry
key for third-party mod registration.
```

---

## Task 5: Create registry and registration infrastructure

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/registry/MultiblockRegistrySetup.java`
- Create: `src/main/java/com/multiblockprojector/common/registry/LegacyAdapterRegistrar.java`
- Modify: `src/main/java/com/multiblockprojector/UniversalProjector.java` (wire registry setup)

**Step 1: Create `MultiblockRegistrySetup.java`**

```java
package com.multiblockprojector.common.registry;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.ProjectorAPI;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Creates and manages the multiblock definition registry.
 */
public class MultiblockRegistrySetup {

    private static Registry<MultiblockDefinition> registry;

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(MultiblockRegistrySetup::onNewRegistry);
    }

    @SubscribeEvent
    private static void onNewRegistry(NewRegistryEvent event) {
        registry = event.create(
            new RegistryBuilder<>(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY)
                .sync(true)
        );
        UniversalProjector.LOGGER.info("Created multiblock definition registry");
    }

    /**
     * Returns the multiblock registry. Only valid after NewRegistryEvent has fired.
     */
    public static Registry<MultiblockDefinition> getRegistry() {
        if (registry == null) {
            throw new IllegalStateException("Multiblock registry not yet created");
        }
        return registry;
    }
}
```

Note: NeoForge 1.21.1 uses `NewRegistryEvent` + `RegistryBuilder` rather than constructing the registry directly in the mod constructor. This is the idiomatic approach. The registry reference is stored in a static field accessible via `getRegistry()`.

**Step 2: Create `LegacyAdapterRegistrar.java` (skeleton — adapters wired in Task 8+)**

```java
package com.multiblockprojector.common.registry;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.api.ProjectorAPI;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
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
            // Adapters will be wired here in subsequent tasks
        });
    }
}
```

**Step 3: Wire registry setup in `UniversalProjector.java`**

In the constructor, after `UPContent.init(modEventBus)`, add:

```java
MultiblockRegistrySetup.init(modEventBus);
LegacyAdapterRegistrar.init(modEventBus);
```

Add the imports:
```java
import com.multiblockprojector.common.registry.MultiblockRegistrySetup;
import com.multiblockprojector.common.registry.LegacyAdapterRegistrar;
```

**Step 4: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: create multiblock registry with NewRegistryEvent

Adds MultiblockRegistrySetup to create the custom NeoForge registry
and LegacyAdapterRegistrar skeleton for adapter bridge at LOW
priority.
```

---

## Task 6: Create MultiblockIndex (cached tab/list data)

**Files:**
- Create: `src/main/java/com/multiblockprojector/common/registry/MultiblockIndex.java`

**Step 1: Create `MultiblockIndex.java`**

```java
package com.multiblockprojector.common.registry;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.ProjectorAPI;
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
```

**Step 2: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: add MultiblockIndex for cached tab/list data

Lazily built from the registry, provides sorted tabs by mod and
alphabetical multiblock lists. Includes "All" tab for cross-mod
browsing.
```

---

## Task 7: Migrate GUI — ProjectorScreen reads from registry

This is the largest single task. The screen is rewritten to use `MultiblockIndex` for dynamic tabs and `MultiblockDefinition` instead of `IUniversalMultiblock`.

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java` (major rewrite)
- Modify: `src/main/java/com/multiblockprojector/client/gui/SimpleMultiblockPreviewRenderer.java` (use BlockEntry)

**Step 1: Rewrite `ProjectorScreen.java`**

Key changes:
- Replace `List<IUniversalMultiblock>` with `List<MultiblockDefinition>`
- Replace hardcoded `MOD_TABS` with `MultiblockIndex.get().getTabs()`
- Replace `IVariableSizeMultiblock` instanceof checks with `def.isVariableSize()`
- Replace `IVariableSizeMultiblock.SizePreset` with `MultiblockDefinition.SizeVariant`
- Add horizontal tab scrolling with `<` and `>` arrow buttons
- All references to `UniversalMultiblockHandler` replaced with `MultiblockIndex`

The full rewritten file replaces the current 546-line `ProjectorScreen.java`. Important structural changes:

**Imports to change:**
- Remove: `IUniversalMultiblock`, `IVariableSizeMultiblock`, `UniversalMultiblockHandler`
- Add: `MultiblockDefinition`, `MultiblockDefinition.SizeVariant`, `MultiblockIndex`, `MultiblockIndex.TabEntry`, `MultiblockRegistrySetup`

**Field changes:**
- `List<IUniversalMultiblock> allMultiblocks` → removed (use MultiblockIndex)
- `List<IUniversalMultiblock> filteredMultiblocks` → `List<MultiblockDefinition> filteredMultiblocks`
- `Map<String, String> MOD_TABS` (static) → removed
- `Map<String, Boolean> modHasMultiblocks` → removed
- `String selectedModTab` → `String selectedTab` (initialized from MultiblockIndex)
- `IUniversalMultiblock selectedMultiblock` → `MultiblockDefinition selectedMultiblock`
- Add: `int tabScrollOffset = 0` and `Button tabLeftButton, tabRightButton`
- Add: `private static final int MAX_VISIBLE_TABS = 4`

**Constructor changes:**
- Remove all `MOD_TABS` iteration and `modHasMultiblocks` logic
- Initialize `selectedTab` from first tab in `MultiblockIndex.get().getTabs()` that has entries (skip "All" — default to first mod tab, fall back to "All")
- Call `updateFilteredMultiblocks()`

**`updateFilteredMultiblocks()` changes:**
- Use `MultiblockIndex.get().getForTab(selectedTab)`

**`init()` changes:**
- Tab buttons generated from `MultiblockIndex.get().getTabs()` with `tabScrollOffset` windowing
- Add `<` and `>` buttons flanking the visible tabs when `tabs.size() > MAX_VISIBLE_TABS`
- Multiblock list buttons use `MultiblockDefinition.displayName()` instead of `getDisplayName()`

**`selectMultiblockForPreview()` changes:**
- Replace `instanceof IVariableSizeMultiblock` with `multiblock.isVariableSize()`
- Replace `getSizePresets()` with `multiblock.variants()`
- Replace `preset.size()` with `variant.dimensions()`

**`selectMultiblock()` changes:**
- This is where Settings serialization happens. Currently `settings.setMultiblock(IUniversalMultiblock)`.
- We need Settings to store a `ResourceLocation` registry ID instead. This change is deferred to Task 10 (Settings migration). For now, we can use a temporary bridge: look up the registry key for the selected `MultiblockDefinition`.

**`render()` changes:**
- Tab highlight drawing uses dynamic tab positions instead of `MOD_TABS` iteration
- Size text uses `SizeVariant.getFullDisplayName()` instead of `IVariableSizeMultiblock.SizePreset`

**IMPORTANT:** This task creates a _compilation bridge_ — `ProjectorScreen` is rewritten to use the new types, but `Settings.java` still stores `IUniversalMultiblock`. We handle this with a temporary adapter until Task 10. The screen can look up the `IUniversalMultiblock` from the `MultiblockDefinition`'s registry ID via the old handler for now. This keeps the changeset focused.

Actually, a cleaner approach: do Settings migration (Task 10) first, or do both together. Since Settings is small and ProjectorScreen directly depends on it, **merge Task 10's Settings changes into this task**.

**Settings.java changes needed here:**
- `IUniversalMultiblock multiblock` → `ResourceLocation multiblockId` (the registry key)
- `getMultiblock()` → returns `MultiblockDefinition` looked up from registry
- Serialization already writes `multiblock.getUniqueName().toString()` → now writes `multiblockId.toString()`
- Deserialization already reads `ResourceLocation.parse(str)` → now stores it and looks up from registry

**Step 2: Rewrite `SimpleMultiblockPreviewRenderer.java`**

Key changes:
- Replace `IUniversalMultiblock multiblock` with `MultiblockDefinition multiblock`
- Replace `List<StructureBlockInfo> structure` with `MultiblockStructure structure` (and iterate `structure.blocks()` map)
- Replace `Vec3i size` with `BlockPos size` (from `MultiblockStructure.size()`)
- Remove all `ICyclingBlockMultiblock` references — `BlockEntry.displayState(tick)` handles cycling
- Remove `IVariableSizeMultiblock` references — caller passes `SizeVariant`
- `setMultiblock(MultiblockDefinition def)` and `setMultiblock(MultiblockDefinition def, SizeVariant variant)`

**`renderMultiblock()` changes (lines 161-213):**
- Instead of iterating `List<StructureBlockInfo>` and checking `ICyclingBlockMultiblock`:
```java
long tick = System.currentTimeMillis() / 50; // ~20 ticks/sec
for (var entry : structure.blocks().entrySet()) {
    BlockPos pos = entry.getKey();
    BlockEntry blockEntry = entry.getValue();
    BlockState state = blockEntry.displayState(tick);
    // ... render as before
}
```

**`renderInfo()` changes:**
- `multiblock.getDisplayName().getString()` → `multiblock.displayName().getString()`
- `multiblock.getModId()` → `multiblock.modId()`

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (may have warnings about unused old imports in other files — that's fine)

**Step 4: Commit**

```
feat: migrate GUI to read from multiblock registry

ProjectorScreen uses dynamic tabs from MultiblockIndex instead of
hardcoded MOD_TABS. SimpleMultiblockPreviewRenderer uses
BlockEntry.displayState() for unified cycling/static rendering.
Settings stores registry ResourceLocation instead of
IUniversalMultiblock reference.
```

---

## Task 8: Refactor IE adapter to produce MultiblockDefinition list

**Files:**
- Modify: `src/main/java/com/multiblockprojector/api/adapters/IEMultiblockAdapter.java`
- Modify: `src/main/java/com/multiblockprojector/common/registry/LegacyAdapterRegistrar.java`

Note: The adapters move from `api/adapters/` to `common/adapters/` since they are internal implementation details, not public API. However, to keep the diff manageable, we can move them in the cleanup task. For now, modify in place.

**Step 1: Refactor `IEMultiblockAdapter.java`**

Change `registerIEMultiblocks()` to `discover()` returning `List<NamedDefinition>`:

```java
public record NamedDefinition(ResourceLocation id, MultiblockDefinition definition) {}
```

The existing reflection logic stays intact. Instead of calling `UniversalMultiblockHandler.registerMultiblock(wrapper)`, build a `MultiblockDefinition` from the wrapper's data:

- `wrapper.getDisplayName()` → `definition.displayName()`
- `wrapper.getModId()` → `"immersiveengineering"`
- Category from the existing `categorizeByName()` logic → `MultiblockCategory` constant
- `wrapper.getStructure(level)` → wrapped in `StructureProvider` that converts `List<StructureBlockInfo>` to `MultiblockStructure` with `SingleBlock` entries
- `wrapper.getSize(level)` → used for the single `SizeVariant`

The key conversion is `List<StructureBlockInfo>` → `Map<BlockPos, BlockEntry>`:
```java
Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
for (StructureBlockInfo info : structureList) {
    if (!info.state().isAir()) {
        blocks.put(info.pos(), new SingleBlock(info.state()));
    }
}
return new MultiblockStructure(blocks);
```

**Step 2: Wire IE adapter in `LegacyAdapterRegistrar.java`**

```java
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
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: refactor IE adapter to produce MultiblockDefinition list

IEMultiblockAdapter.discover() returns named definitions registered
by LegacyAdapterRegistrar at LOW priority with dedup.
```

---

## Task 9: Refactor Mekanism adapter to produce MultiblockDefinition list

**Files:**
- Modify: `src/main/java/com/multiblockprojector/api/adapters/MekanismMultiblockAdapter.java`
- Modify: `src/main/java/com/multiblockprojector/common/registry/LegacyAdapterRegistrar.java`

**Step 1: Refactor `MekanismMultiblockAdapter.java`**

Same pattern as IE adapter. The Mekanism adapter is more complex because it has:
- Variable-size multiblocks (`IVariableSizeMultiblock`) → `List<SizeVariant>` in `MultiblockDefinition`
- Hardcoded structure generation → wrapped in `StructureProvider`

Each inner class (DynamicTankMultiblock, InductionMatrixMultiblock, etc.) currently implements `IVariableSizeMultiblock`. Refactor to:
- `discover()` returns `List<NamedDefinition>` (reuse the record from IE adapter, or extract to shared location)
- Each inner class becomes a static method returning a `MultiblockDefinition`
- `getSizePresets()` → `List<SizeVariant>`
- `getStructureAtSize(world, size)` → `StructureProvider` that converts `List<StructureBlockInfo>` to `MultiblockStructure` with `SingleBlock` entries

**Step 2: Wire Mekanism adapter in `LegacyAdapterRegistrar.java`**

Same pattern as IE — check `isModLoaded("mekanism")`, call `discover()`, register with dedup.

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: refactor Mekanism adapter to produce MultiblockDefinition list

Converts all 8 Mekanism multiblocks including variable-size support
via SizeVariant lists. Structure generation logic preserved.
```

---

## Task 10: Refactor Blood Magic adapter to produce MultiblockDefinition list

**Files:**
- Modify: `src/main/java/com/multiblockprojector/api/adapters/BloodMagicMultiblockAdapter.java`
- Modify: `src/main/java/com/multiblockprojector/common/registry/LegacyAdapterRegistrar.java`

**Step 1: Refactor `BloodMagicMultiblockAdapter.java`**

This adapter is unique because of `ICyclingBlockMultiblock` (rune positions). The conversion:
- Rune positions that had `hasCyclingBlocks() = true` → use `BlockGroup` entries in the structure map
- Non-rune positions → use `SingleBlock` entries
- The `BaseAltarMultiblock.runePositions` set drives which positions get `BlockGroup` vs `SingleBlock`

```java
// In structure building:
if (isRunePosition(pos)) {
    blocks.put(pos, new BlockGroup(
        Component.literal("Any Rune"),
        getRunesForTier(tier)
    ));
} else {
    blocks.put(pos, new SingleBlock(blockState));
}
```

**Step 2: Wire Blood Magic adapter in `LegacyAdapterRegistrar.java`**

Same pattern — check `isModLoaded("bloodmagic")`, call `discover()`, register with dedup.

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: refactor Blood Magic adapter to produce MultiblockDefinition list

Rune positions use BlockGroup entries for cycling display and
flexible validation. All 6 altar tiers converted.
```

---

## Task 11: Migrate projection and validation to new API types

**Files:**
- Modify: `src/main/java/com/multiblockprojector/common/projector/MultiblockProjection.java`
- Modify: `src/main/java/com/multiblockprojector/client/render/ProjectionRenderer.java`
- Modify: `src/main/java/com/multiblockprojector/client/BlockValidationManager.java`
- Modify: `src/main/java/com/multiblockprojector/common/network/MessageAutoBuild.java`
- Modify: `src/main/java/com/multiblockprojector/client/ProjectorClientHandler.java`
- Modify: `src/main/java/com/multiblockprojector/common/events/ProjectorEvent.java`

**Step 1: Migrate `MultiblockProjection.java`**

Key changes:
- `IUniversalMultiblock multiblock` → `MultiblockDefinition multiblock`
- Constructor takes `MultiblockDefinition` + `SizeVariant`
- Structure data comes from `multiblock.structureProvider().create(variant, level)` returning `MultiblockStructure`
- Internal layer map built from `MultiblockStructure.blocks()` instead of `List<StructureBlockInfo>`
- The `Info` inner class carries `BlockEntry` instead of `StructureBlockInfo`
- `Info.getModifiedState()` applies rotation/mirror to `blockEntry.displayState(tick)` — but wait, rotation/mirror needs to apply to the _actual_ state, not the display state. The info should carry the `BlockEntry` and the transformed `BlockPos`. The consumer decides whether to use `displayState()` or `matches()`.

Actually, let me think about this more carefully. The current `MultiblockProjection.Info` carries a `StructureBlockInfo` (pos + state + nbt). The projection system applies rotation and mirror to positions and states. With the new API:

- `BlockEntry` is the source of truth for what block should be at a position
- Position transformation still works the same way (rotation/mirror on BlockPos)
- State transformation: `SingleBlock.state().mirror().rotate()` for rendering
- For validation: `BlockEntry.matches(actualState)` — no transformation needed on the placed state

So `Info` should carry `BlockEntry` and the transformed `BlockPos`. The display state (with rotation/mirror) is computed on demand.

**Step 2: Migrate `ProjectionRenderer.java`**

- Remove all `ICyclingBlockMultiblock` references
- Ghost block state comes from `info.blockEntry.displayState(tick)` with rotation/mirror applied
- Remove the separate `cycleIndex` / `lastCycleTime` — `BlockEntry.displayState()` handles timing internally using `tick` parameter

**Step 3: Migrate `BlockValidationManager.java`**

- Remove all `ICyclingBlockMultiblock` references
- Replace `blocksMatch()` and `blocksMatchCycling()` with `info.blockEntry.matches(actualState)`
- Much simpler — the sealed hierarchy handles both cases

**Step 4: Migrate `MessageAutoBuild.java`**

- Remove `ICyclingBlockMultiblock` references
- For auto-build, use `blockEntry.displayState(0)` to get a concrete state to place (for `BlockGroup`, this returns the first option which serves as the "default")
- Or add a `BlockEntry.defaultState()` method — but `displayState(0)` works fine

**Step 5: Migrate `ProjectorClientHandler.java`**

- Remove `UniversalMultiblockHandler.discoverMultiblocks()` call (line 46-47)
- Remove `multiblocksDiscovered` flag
- `settings.getMultiblock()` now returns `MultiblockDefinition` (from Task 7's Settings changes)
- `MultiblockProjection` constructor calls updated per Step 1

**Step 6: Migrate `ProjectorEvent.java`**

- `IUniversalMultiblock multiblock` → `MultiblockDefinition multiblock`
- All getters/constructors updated

**Step 7: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL with zero references to old API types outside the old files themselves

**Step 8: Commit**

```
feat: migrate projection, rendering, and validation to new API

ProjectionRenderer and BlockValidationManager use BlockEntry for
unified block handling. All ICyclingBlockMultiblock instanceof
checks removed. MultiblockProjection uses MultiblockDefinition.
```

---

## Task 12: Delete old API types and handler

**Files:**
- Delete: `src/main/java/com/multiblockprojector/api/IUniversalMultiblock.java`
- Delete: `src/main/java/com/multiblockprojector/api/IVariableSizeMultiblock.java`
- Delete: `src/main/java/com/multiblockprojector/api/ICyclingBlockMultiblock.java`
- Delete: `src/main/java/com/multiblockprojector/api/UniversalMultiblockHandler.java`
- Delete: `src/main/java/com/multiblockprojector/api/TestMultiblock.java`
- Modify: `src/main/java/com/multiblockprojector/common/NeoForgeEventHandler.java` (remove discovery call)

**Step 1: Verify no remaining references**

Search for any remaining imports of old types:
```
grep -r "IUniversalMultiblock\|UniversalMultiblockHandler\|IVariableSizeMultiblock\|ICyclingBlockMultiblock\|TestMultiblock" src/main/java/
```
Expected: Only the files being deleted, plus the old `api/` package-info if it exists.

**Step 2: Delete the files**

Remove all 5 files listed above.

**Step 3: Simplify `NeoForgeEventHandler.java`**

Remove the `onServerStarting` method and the `UniversalMultiblockHandler` import. If the class has no other event handlers, delete it entirely.

**Step 4: Move adapters from `api/adapters/` to `common/adapters/`**

The adapters are internal implementation details, not part of the public API. Move:
- `src/main/java/com/multiblockprojector/api/adapters/` → `src/main/java/com/multiblockprojector/common/adapters/`

Update package declarations and all imports.

**Step 5: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Verify full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```
feat: remove old multiblock API types and handler

Deletes IUniversalMultiblock, IVariableSizeMultiblock,
ICyclingBlockMultiblock, UniversalMultiblockHandler, and
TestMultiblock. Moves adapters to common/adapters/ package.
The NeoForge registry is now the sole source of truth.
```

---

## Task 13: Add test multiblocks via testmod source set

**Files:**
- Modify: `build.gradle` (add testmod source set)
- Create: `src/testmod/java/com/multiblockprojector/test/TestMultiblockRegistrar.java`

**Step 1: Add testmod source set to `build.gradle`**

In the `neoForge` block, add a `testmod` source set that loads in dev runs:

```groovy
neoForge {
    // ... existing config ...
    mods {
        multiblockprojector {
            sourceSet sourceSets.main
        }
        multiblockprojector_testmod {
            sourceSet sourceSets.create('testmod')
        }
    }

    runs {
        client {
            // ... existing config ...
            modSources.add(sourceSets.testmod)
        }
        server {
            // ... existing config ...
            modSources.add(sourceSets.testmod)
        }
    }
}

sourceSets {
    testmod {
        java {
            compileClasspath += sourceSets.main.output
            compileClasspath += sourceSets.main.compileClasspath
        }
    }
}
```

Note: The exact Gradle DSL for testmod source sets with `net.neoforged.moddev` may vary. Consult the NeoForge MDK for the correct approach. If `moddev` doesn't support `modSources`, add testmod as a separate mod entry.

**Step 2: Create `TestMultiblockRegistrar.java`**

```java
package com.multiblockprojector.test;

import com.multiblockprojector.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = "multiblockprojector", bus = EventBusSubscriber.Bus.MOD)
public class TestMultiblockRegistrar {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterEvent event) {
        event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
            // Test furnace
            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_furnace"),
                MultiblockDefinition.fixed(
                    Component.literal("Test Furnace"),
                    "multiblockprojector",
                    MultiblockCategory.GENERAL,
                    new BlockPos(3, 2, 3),
                    (variant, level) -> buildTestFurnace()
                )
            );

            // Test tower
            helper.register(
                ResourceLocation.fromNamespaceAndPath("multiblockprojector", "test_tower"),
                MultiblockDefinition.fixed(
                    Component.literal("Test Tower"),
                    "multiblockprojector",
                    MultiblockCategory.GENERAL,
                    new BlockPos(1, 8, 1),
                    (variant, level) -> buildTestTower()
                )
            );
        });
    }

    private static MultiblockStructure buildTestFurnace() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (x == 1 && z == 1) {
                    blocks.put(new BlockPos(x, 0, z), new SingleBlock(Blocks.FURNACE.defaultBlockState()));
                } else {
                    blocks.put(new BlockPos(x, 0, z), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));
                }
            }
        }
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if ((x == 0 || x == 2) && (z == 0 || z == 2)) {
                    blocks.put(new BlockPos(x, 1, z), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));
                }
            }
        }
        return new MultiblockStructure(blocks);
    }

    private static MultiblockStructure buildTestTower() {
        Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
        for (int y = 0; y < 8; y++) {
            blocks.put(new BlockPos(0, y, 0), new SingleBlock(Blocks.COBBLESTONE.defaultBlockState()));
        }
        return new MultiblockStructure(blocks);
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: add testmod source set with test multiblocks

Test multiblocks only load in dev environment, never ship in
release jar. Registered at LOWEST priority.
```

---

## Task 14: Integration test — run the client

**Step 1: Run the game client**

Run: `./gradlew runClient`
Expected: Game launches without crash

**Step 2: Manual verification checklist**

- [ ] Game loads to main menu without errors in log
- [ ] Start a creative world
- [ ] Give yourself the projector item (`/give @p multiblockprojector:projector`)
- [ ] Right-click to open GUI
- [ ] Verify tabs appear dynamically (test multiblocks should show under "Multiblock Projector" or "All" tab)
- [ ] If IE/Mekanism/Blood Magic JARs are in libs/, verify those tabs appear
- [ ] Click a multiblock to see 3D preview
- [ ] For variable-size multiblocks, verify [-]/[+] buttons work
- [ ] Select a multiblock, verify projection appears
- [ ] Place projection (left-click), verify building mode works
- [ ] Complete the structure, verify completion detection
- [ ] Test auto-build (Shift+left-click in creative)

**Step 3: Fix any issues found**

**Step 4: Commit any fixes**

---

## Task 15: Final cleanup and documentation

**Files:**
- Remove any dead imports across all modified files
- Verify no `TODO` markers were left unaddressed

**Step 1: Search for dead imports**

```
grep -rn "import com.multiblockprojector.api.IUniversal\|import com.multiblockprojector.api.IVariable\|import com.multiblockprojector.api.ICycling\|import com.multiblockprojector.api.Universal" src/main/java/
```
Expected: No results

**Step 2: Final build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, JAR in `build/libs/`

**Step 3: Commit**

```
chore: final cleanup of registry API migration

Remove dead imports and verify clean build.
```

---

## Dependency Graph

```
Task 1 (Gradle subproject)
  └── Task 2 (BlockEntry)
       └── Task 3 (Structure + Category)
            └── Task 4 (Definition + API)
                 └── Task 5 (Registry infra)
                      ├── Task 6 (MultiblockIndex)
                      │    └── Task 7 (GUI migration) ← largest task
                      └── Task 8 (IE adapter)
                           └── Task 9 (Mekanism adapter)
                                └── Task 10 (Blood Magic adapter)
                                     └── Task 11 (Projection/validation)
                                          └── Task 12 (Delete old types)
                                               └── Task 13 (Testmod)
                                                    └── Task 14 (Integration test)
                                                         └── Task 15 (Cleanup)
```

Tasks 6-10 can potentially be parallelized (MultiblockIndex, GUI, and adapters are semi-independent), but the safest path is sequential since the GUI depends on adapters actually registering content.
