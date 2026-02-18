# Multiblock Registry API Design

## Overview

Replace the current hardcoded multiblock discovery system (`UniversalMultiblockHandler` + reflection-based adapters + static GUI tabs) with a proper NeoForge custom registry and a public API jar that third-party mods can depend on to register their own multiblocks.

**Goals:**
- Single source of truth: one NeoForge registry backs everything
- Third-party mods can register multiblocks via standard `RegisterEvent`
- Existing adapters (IE, Mekanism, Blood Magic) continue working as compatibility bridges
- GUI tabs auto-generate from registry contents
- Clean API surface shipped as a separate artifact

## API Module Types

The `projector-api` module contains only the types other mods need. No dependency on the main mod.

### MultiblockDefinition (registry value type)

```java
public record MultiblockDefinition(
    Component displayName,
    String modId,                    // plain string, not ResourceLocation
    MultiblockCategory category,
    List<SizeVariant> variants,
    StructureProvider structureProvider
) {
    public record SizeVariant(
        Component label,             // "3x3x3", "Small", etc.
        BlockPos dimensions,
        boolean isDefault
    ) {}

    @FunctionalInterface
    public interface StructureProvider {
        /**
         * Creates the multiblock structure for a given size variant.
         *
         * Contract: Must be idempotent, side-effect free, and thread-safe.
         * May be called from any thread. Implementations should not access
         * or modify world state.
         *
         * @param variant  the size variant to build
         * @param level    the current level, or null if unavailable
         * @return the structure block data
         */
        MultiblockStructure create(SizeVariant variant, @Nullable Level level);
    }

    /** Convenience factory for single-size multiblocks. */
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
}
```

### MultiblockStructure

```java
public record MultiblockStructure(
    Map<BlockPos, BlockEntry> blocks,
    BlockPos size   // auto-computed from block map in constructor
) {
    public MultiblockStructure(Map<BlockPos, BlockEntry> blocks) {
        this(blocks, computeBounds(blocks));
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

### BlockEntry (sealed hierarchy)

```java
public sealed interface BlockEntry permits SingleBlock, BlockGroup {
    /** Returns the block state to display at the given tick. */
    BlockState displayState(long tick);

    /** Returns true if the placed block state satisfies this entry. */
    boolean matches(BlockState placed);
}

public record SingleBlock(BlockState state) implements BlockEntry {
    public BlockState displayState(long tick) { return state; }
    public boolean matches(BlockState placed) { return placed.is(state.getBlock()); }
}

public record BlockGroup(
    Component label,              // "Any Rune", "Any Casing"
    List<BlockState> options
) implements BlockEntry {
    public BlockState displayState(long tick) {
        return options.get((int) ((tick / 20) % options.size()));
    }
    public boolean matches(BlockState placed) {
        return options.stream().anyMatch(o -> placed.is(o.getBlock()));
    }
}
```

Note: `BlockGroup` equality is identity-based on block states. Do not rely on `BlockGroup.equals()` for deduplication.

### MultiblockCategory (extensible record)

```java
public record MultiblockCategory(ResourceLocation id, Component displayName) {
    // Common categories provided as constants
    public static final MultiblockCategory PROCESSING =
        new MultiblockCategory(rl("multiblockprojector", "processing"), Component.literal("Processing"));
    public static final MultiblockCategory POWER =
        new MultiblockCategory(rl("multiblockprojector", "power"), Component.literal("Power"));
    public static final MultiblockCategory STORAGE =
        new MultiblockCategory(rl("multiblockprojector", "storage"), Component.literal("Storage"));
    public static final MultiblockCategory CRAFTING =
        new MultiblockCategory(rl("multiblockprojector", "crafting"), Component.literal("Crafting"));
    public static final MultiblockCategory GENERAL =
        new MultiblockCategory(rl("multiblockprojector", "general"), Component.literal("General"));
}
```

Third-party mods can create their own: `new MultiblockCategory(rl("mymod", "ritual"), Component.literal("Ritual"))`.

### ProjectorAPI (registry key)

```java
@API(owner = "multiblockprojector", provides = "ProjectorAPI", apiVersion = "1.0.0")
public class ProjectorAPI {
    public static final ResourceKey<Registry<MultiblockDefinition>> MULTIBLOCK_REGISTRY_KEY =
        ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath("multiblockprojector", "multiblocks"));
}
```

## Registration Flow

### Two-phase registration within RegisterEvent

**Phase 1 (DEFAULT priority):** Third-party mods register their own multiblocks via `RegisterEvent` at default priority.

```java
// In some other mod that depends on projector-api
@SubscribeEvent
public static void onRegister(RegisterEvent event) {
    event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
        helper.register(
            ResourceLocation.fromNamespaceAndPath("mymod", "big_reactor"),
            MultiblockDefinition.fixed(
                Component.literal("Big Reactor"),
                "mymod",
                new MultiblockCategory(rl("mymod", "energy"), Component.literal("Energy")),
                new BlockPos(5, 5, 5),
                (variant, level) -> buildBigReactor()
            )
        );
    });
}
```

**Phase 2 (LOW priority):** Our adapters run at `EventPriority.LOW`, after all default-priority handlers. They check `containsKey()` before registering, so first-party registrations win.

```java
@SubscribeEvent(priority = EventPriority.LOW)
public static void onRegisterAdapters(RegisterEvent event) {
    event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
        if (ModList.get().isLoaded("immersiveengineering")) {
            for (var def : IEMultiblockAdapter.discover()) {
                if (!MULTIBLOCK_REGISTRY.containsKey(def.registryId())) {
                    helper.register(def.registryId(), def);
                }
            }
        }
        // Same for Mekanism, Blood Magic...
    });
}
```

**Safety net:** If `containsKey()` doesn't see batched writes within the same event fire, track registered IDs in a `Set<ResourceLocation>` and check that instead.

### Namespace convention (documented requirement)

Adapter entries **must** use the target mod's namespace (e.g., `immersiveengineering:arc_furnace`). This ensures that if IE later registers its own entry at the same key, the adapter's `containsKey()` check skips it. This is the linchpin of the override system.

### Registry creation

```java
// In mod constructor
public static final Registry<MultiblockDefinition> MULTIBLOCK_REGISTRY =
    new RegistryBuilder<>(MULTIBLOCK_REGISTRY_KEY).sync(true).create();
```

### Test multiblocks

Use a `testmod/` source set that only loads in dev. Test registrations never ship in the release jar.

```
src/testmod/java/
  com/multiblockprojector/test/
    TestMultiblockRegistrar.java   // registers test entries at LOWEST priority
```

## GUI Changes

### Dynamic tab generation

Replace hardcoded `MOD_TABS` with dynamic generation from the registry.

**`MultiblockIndex`** (lives in `common/registry/`, not `client/gui/`):

```java
public class MultiblockIndex {
    private static MultiblockIndex INSTANCE;

    private final List<TabEntry> tabs;           // sorted alphabetically by display name
    private final Map<String, List<MultiblockDefinition>> byMod;

    public record TabEntry(String modId, String displayName) {}

    public static MultiblockIndex get() {
        if (INSTANCE == null) INSTANCE = buildFromRegistry();
        return INSTANCE;
    }

    /** Invalidate on registry changes (shouldn't happen post-load, but defensive). */
    public static void invalidate() { INSTANCE = null; }
}
```

- Tabs sorted alphabetically by display name
- Display name from `ModList.get().getModContainerById(modId).getModInfo().getDisplayName()`
- "All" tab at the front, shows every multiblock regardless of mod
- Cached lazily, built once from the frozen registry

### Tab overflow

Horizontal scrolling with arrow buttons when tabs don't fit. Same pattern as creative inventory tabs. No row wrapping (avoids variable screen height).

### Size selector

- `[-]` / `[+]` buttons when `variants.size() > 1`
- Hidden for single-variant multiblocks
- `// TODO`: Switch to dropdown when `variants.size() > 4`

### Preview renderer

`SimpleMultiblockPreviewRenderer` calls `BlockEntry.displayState(tick)` uniformly. No `instanceof` checks. `BlockGroup` entries cycle automatically. `SingleBlock` entries return their state directly.

## Project Structure

```
multiblockprojector/
├── api/                              # projector-api module
│   ├── build.gradle
│   │   dependencies {
│   │       compileOnly "net.neoforged:neoforge:${neoforge_version}"
│   │   }
│   └── src/main/java/
│       └── com/multiblockprojector/api/
│           ├── package-info.java             # @API annotation
│           ├── ProjectorAPI.java             # Registry key
│           ├── MultiblockDefinition.java
│           ├── MultiblockStructure.java
│           ├── MultiblockCategory.java
│           ├── BlockEntry.java               # Sealed interface
│           ├── SingleBlock.java
│           └── BlockGroup.java
├── src/main/java/                    # main mod
│   └── com/multiblockprojector/
│       ├── common/
│       │   ├── registry/
│       │   │   ├── MultiblockRegistrySetup.java    # RegistryBuilder + event listeners
│       │   │   ├── LegacyAdapterRegistrar.java     # LOW-priority adapter bridge
│       │   │   └── MultiblockIndex.java             # Cached tab/list data
│       │   └── adapters/                            # Existing adapters, refactored
│       │       ├── IEMultiblockAdapter.java         # Returns List<MultiblockDefinition>
│       │       ├── MekanismMultiblockAdapter.java
│       │       └── BloodMagicMultiblockAdapter.java
│       ├── client/
│       │   ├── gui/
│       │   │   ├── ProjectorScreen.java             # Dynamic tabs via MultiblockIndex
│       │   │   └── SimpleMultiblockPreviewRenderer.java  # Uses BlockEntry.displayState()
│       │   └── ...
│       └── ...
├── src/testmod/java/                 # Dev-only test multiblocks
│   └── com/multiblockprojector/test/
│       └── TestMultiblockRegistrar.java
└── build.gradle                      # Multi-project setup
```

## Migration Phases

Each phase is independently testable. No coexistence of old/new systems required (pre-release).

### Phase 1: Create API module
- Define all API types (records, sealed interface, registry key)
- Set up `api/build.gradle` with minimal dependencies
- Add `package-info.java` with `@API` annotation

### Phase 2: Create registry + wire test entry
- `RegistryBuilder` in mod constructor
- Register one hardcoded test `MultiblockDefinition` to verify the registry works
- Verify registry freezing behavior

### Phase 3: Migrate GUI to read from registry
- Create `MultiblockIndex` in `common/registry/`
- Rewrite `ProjectorScreen` to use dynamic tabs from `MultiblockIndex`
- Implement horizontal tab scrolling with arrow buttons
- Update `SimpleMultiblockPreviewRenderer` to use `BlockEntry.displayState(tick)`
- Verify with test entry from Phase 2

### Phase 4: Refactor adapters one at a time
- Each adapter returns `List<MultiblockDefinition>` instead of calling old handler
- `LegacyAdapterRegistrar` at `EventPriority.LOW` calls adapters and registers with dedup
- Each adapter lights up in the GUI immediately as converted
- Order: IE first (simplest, reflection-only), then Mekanism (variable sizes), then Blood Magic (cycling blocks)

### Phase 5: Migrate projection and validation
- `ProjectionRenderer` uses `BlockEntry.displayState()` for ghost rendering
- `BlockValidationManager` uses `BlockEntry.matches()` for validation
- Remove all `instanceof ICyclingBlockMultiblock` checks

### Phase 6: Remove old system
- Delete `UniversalMultiblockHandler`
- Delete `IUniversalMultiblock`, `IVariableSizeMultiblock`, `ICyclingBlockMultiblock`
- Delete `NeoForgeEventHandler.onServerStarting()` discovery call
- Delete `TestMultiblock` (replaced by `testmod/` source set)

### Phase 7: Publish API jar + documentation
- Publish `projector-api` artifact
- Write `api/USAGE.md` with quick-start example:
  ```java
  // build.gradle
  compileOnly "com.multiblockprojector:projector-api:1.0.0"

  // In your mod's event handler
  @SubscribeEvent
  public static void onRegister(RegisterEvent event) {
      event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
          helper.register(rl("mymod", "my_structure"),
              MultiblockDefinition.fixed(...));
      });
  }
  ```

## Open Questions

- **Registry freeze timing:** Verify that `RegistryBuilder.create()` custom registries follow the same freeze lifecycle as vanilla registries in NeoForge 1.21.1. If so, confirm that `containsKey()` works within `RegisterEvent` across priority levels, or fall back to tracking IDs in a `Set`.
- **`@API` annotation availability:** Confirm NeoForge 1.21.1 still supports `@API` on packages, or use the modern equivalent.
- **Size selector UX for many variants:** Decide threshold for switching from `[-]/[+]` buttons to a dropdown (suggested: >4 variants). Not a v1 blocker.
