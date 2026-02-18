# Multiblock Projector API Guide

Add multiblock projections to the Multiblock Projector mod from your own mod. Players will be able to browse, preview, and project your multiblocks using the projector item.

## Setup

### Gradle Dependency

Add the Multiblock Projector API jar to your project. The API is a compile-only dependency — your mod does not need it at runtime if the projector mod isn't installed.

```groovy
repositories {
    // Add the repository hosting the API jar
}

dependencies {
    compileOnly files('libs/MultiblockProjector-api.jar')
}
```

### Optional Dependency

Declare the projector mod as optional in your `neoforge.mods.toml` so your mod works without it:

```toml
[[dependencies.yourmod]]
modId = "multiblockprojector"
type = "optional"
versionRange = "[1.21.1-0.1,)"
ordering = "NONE"
side = "BOTH"
```

## Registration

Register multiblocks during the `RegisterEvent` using the `ProjectorAPI.MULTIBLOCK_REGISTRY_KEY`. Guard the registration behind a mod-loaded check so your code doesn't crash if the projector mod is absent.

```java
import com.multiblockprojector.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = "yourmod", bus = EventBusSubscriber.Bus.MOD)
public class ProjectorCompat {

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!ModList.get().isLoaded("multiblockprojector")) return;

        event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
            helper.register(
                ResourceLocation.fromNamespaceAndPath("yourmod", "my_machine"),
                MultiblockDefinition.fixed(
                    Component.literal("My Machine"),
                    "yourmod",
                    MultiblockCategory.PROCESSING,
                    new BlockPos(3, 3, 3),
                    (variant, level) -> buildMyMachine()
                )
            );
        });
    }
}
```

## API Reference

### MultiblockDefinition

The core record stored in the registry. Each definition represents one multiblock that appears in the projector's GUI.

| Field | Type | Description |
|-------|------|-------------|
| `displayName` | `Component` | Name shown in the GUI list |
| `modId` | `String` | Your mod ID — used for grouping in the mod selector |
| `category` | `MultiblockCategory` | Organization category |
| `variants` | `List<SizeVariant>` | Size options. Single-element list for fixed-size |
| `structureProvider` | `StructureProvider` | Creates the block layout on demand |

**Factory method** for fixed-size multiblocks (most common):

```java
MultiblockDefinition.fixed(name, modId, category, size, structureProvider)
```

**Constructor** for variable-size multiblocks:

```java
new MultiblockDefinition(name, modId, category, List.of(
    new SizeVariant(Component.literal("Small"),  new BlockPos(3, 3, 3), false),
    new SizeVariant(Component.literal("Medium"), new BlockPos(5, 5, 5), true),  // default
    new SizeVariant(Component.literal("Large"),  new BlockPos(7, 7, 7), false)
), structureProvider)
```

The variant marked `isDefault = true` is selected initially in the GUI.

### StructureProvider

A functional interface that creates the block layout:

```java
MultiblockStructure create(SizeVariant variant, @Nullable Level level)
```

**Contract:**
- Must be idempotent and side-effect free
- Must be thread-safe (may be called from any thread)
- Should not access or modify world state
- The `level` parameter is provided for block state lookups but may be null

For fixed-size multiblocks, you can ignore the `variant` parameter:

```java
(variant, level) -> buildMyStructure()
```

For variable-size multiblocks, read dimensions from the variant:

```java
(variant, level) -> {
    int w = variant.dimensions().getX();
    int h = variant.dimensions().getY();
    int d = variant.dimensions().getZ();
    return buildScaledStructure(w, h, d);
}
```

### MultiblockStructure

A record containing the block layout as a `Map<BlockPos, BlockEntry>`. Positions use absolute coordinates starting at `(0, 0, 0)`. Air blocks should be omitted.

```java
Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
blocks.put(new BlockPos(0, 0, 0), new SingleBlock(Blocks.IRON_BLOCK.defaultBlockState()));
blocks.put(new BlockPos(1, 0, 0), new SingleBlock(Blocks.IRON_BLOCK.defaultBlockState()));
// ... more blocks

return new MultiblockStructure(blocks);  // size auto-computed from bounding box
```

Use `LinkedHashMap` to preserve insertion order — blocks are rendered and animated in this order during the build-up preview.

### BlockEntry

A sealed interface with two implementations:

#### SingleBlock

Requires an exact block type. Properties (e.g. facing) are ignored during validation — only the block type is checked.

```java
new SingleBlock(Blocks.FURNACE.defaultBlockState())
```

#### BlockGroup

Accepts any of several block types. The preview cycles through the options every second (20 ticks), showing players what blocks are valid.

```java
new BlockGroup(
    Component.literal("Mineral Block"),
    List.of(
        Blocks.IRON_BLOCK.defaultBlockState(),
        Blocks.GOLD_BLOCK.defaultBlockState(),
        Blocks.DIAMOND_BLOCK.defaultBlockState(),
        Blocks.EMERALD_BLOCK.defaultBlockState()
    )
)
```

Use `BlockGroup` when multiple block types are acceptable at a position, such as:
- Beacon bases (iron/gold/diamond/emerald)
- Decorative blocks (any stone brick variant)
- Tier-based components (any valid rune type)

### MultiblockCategory

Predefined categories for organizing multiblocks in the GUI:

| Constant | Use for |
|----------|---------|
| `MultiblockCategory.PROCESSING` | Furnaces, crushers, machines |
| `MultiblockCategory.POWER` | Generators, energy storage |
| `MultiblockCategory.STORAGE` | Tanks, chests, silos |
| `MultiblockCategory.CRAFTING` | Crafting tables, assemblers |
| `MultiblockCategory.GENERAL` | Everything else |

Create custom categories if none fit:

```java
public static final MultiblockCategory MY_CATEGORY = new MultiblockCategory(
    ResourceLocation.fromNamespaceAndPath("yourmod", "ritual"),
    Component.literal("Rituals")
);
```

## Complete Examples

### Fixed-Size Multiblock

A 3x2x3 smeltery with a furnace core:

```java
helper.register(
    ResourceLocation.fromNamespaceAndPath("yourmod", "smeltery"),
    MultiblockDefinition.fixed(
        Component.literal("Smeltery"),
        "yourmod",
        MultiblockCategory.PROCESSING,
        new BlockPos(3, 2, 3),
        (variant, level) -> {
            Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();

            // Bottom layer: 3x3 stone bricks with center furnace
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    BlockEntry entry = (x == 1 && z == 1)
                        ? new SingleBlock(Blocks.FURNACE.defaultBlockState())
                        : new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState());
                    blocks.put(new BlockPos(x, 0, z), entry);
                }
            }

            // Top layer: corner pillars only
            blocks.put(new BlockPos(0, 1, 0), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));
            blocks.put(new BlockPos(2, 1, 0), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));
            blocks.put(new BlockPos(0, 1, 2), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));
            blocks.put(new BlockPos(2, 1, 2), new SingleBlock(Blocks.STONE_BRICKS.defaultBlockState()));

            return new MultiblockStructure(blocks);
        }
    )
);
```

### Variable-Size Multiblock

A tank that scales from 3x3x3 to 7x7x7:

```java
helper.register(
    ResourceLocation.fromNamespaceAndPath("yourmod", "tank"),
    new MultiblockDefinition(
        Component.literal("Fluid Tank"),
        "yourmod",
        MultiblockCategory.STORAGE,
        List.of(
            new SizeVariant(Component.literal("Small"),  new BlockPos(3, 3, 3), false),
            new SizeVariant(Component.literal("Medium"), new BlockPos(5, 5, 5), true),
            new SizeVariant(Component.literal("Large"),  new BlockPos(7, 7, 7), false)
        ),
        (variant, level) -> {
            int w = variant.dimensions().getX();
            int h = variant.dimensions().getY();
            int d = variant.dimensions().getZ();
            Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();

            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    for (int z = 0; z < d; z++) {
                        boolean isEdge = x == 0 || x == w - 1
                            || y == 0 || y == h - 1
                            || z == 0 || z == d - 1;
                        if (isEdge) {
                            blocks.put(new BlockPos(x, y, z),
                                new SingleBlock(Blocks.IRON_BLOCK.defaultBlockState()));
                        }
                        // Interior is air — omit from map
                    }
                }
            }

            return new MultiblockStructure(blocks);
        }
    )
);
```

### BlockGroup with Cycling Preview

A ritual altar where pedestals accept any gem block:

```java
BlockGroup gemPedestal = new BlockGroup(
    Component.literal("Gem Block"),
    List.of(
        Blocks.DIAMOND_BLOCK.defaultBlockState(),
        Blocks.EMERALD_BLOCK.defaultBlockState(),
        Blocks.LAPIS_BLOCK.defaultBlockState(),
        Blocks.AMETHYST_BLOCK.defaultBlockState()
    )
);

Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
blocks.put(new BlockPos(1, 0, 1), new SingleBlock(Blocks.OBSIDIAN.defaultBlockState()));
blocks.put(new BlockPos(0, 0, 0), gemPedestal);
blocks.put(new BlockPos(2, 0, 0), gemPedestal);
blocks.put(new BlockPos(0, 0, 2), gemPedestal);
blocks.put(new BlockPos(2, 0, 2), gemPedestal);
```

In the GUI preview, all four pedestal positions will cycle through diamond, emerald, lapis, and amethyst blocks. During validation, any of those blocks will be accepted.

## Tips

- **Insertion order matters.** Use `LinkedHashMap` for the block map. The build-up animation adds blocks in iteration order.
- **Omit air.** Don't add air blocks to the structure map — only include blocks the player needs to place.
- **Block type matching.** `SingleBlock.matches()` checks block type only, not block state properties. A furnace facing north will match a furnace facing east.
- **Reuse BlockGroup instances.** If multiple positions accept the same set of blocks, create one `BlockGroup` and reuse it across positions.
- **Guard your registration.** Always check `ModList.get().isLoaded("multiblockprojector")` before accessing API classes to avoid `NoClassDefFoundError` when the projector mod isn't installed.
