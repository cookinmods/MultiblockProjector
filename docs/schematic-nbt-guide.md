# Schematic & NBT File Guide

Load `.nbt` structure files into the Multiblock Projector for building assistance. No code required — just drop files into a folder.

For registering multiblocks programmatically from another mod, see [API Guide](api-guide.md).

## Quick Start

1. Create the schematics folder: `config/multiblockprojector/schematics/`
2. Drop any `.nbt` file into the folder
3. Open the projector GUI — your structure appears under the "Custom NBTs" tab

## Creating .nbt Files

### With Create (recommended)

If you have the [Create](https://www.curseforge.com/minecraft/mc-mods/create) mod installed:

1. Craft a **Schematic and Quill**
2. Right-click two corners to select the area around your structure
3. Right-click a third time to save — the file goes to the game's `schematics/` folder
4. Move the `.nbt` file from `schematics/` to `config/multiblockprojector/schematics/`

Create's `schematics/` folder is also scanned automatically when Create is installed (appears as a "Create Schematics" tab), but organizing files in the config folder gives you control over tab names.

### With Structure Blocks (vanilla)

1. Place a **Structure Block** (obtain with `/give @s structure_block`)
2. Set it to **Save** mode
3. Define the bounding box around your structure using the corner and size fields
4. Name it and click **Save**
5. The file saves to `generated/<world>/structures/<name>.nbt`
6. Copy it to `config/multiblockprojector/schematics/`

### From Other Mods

Many structure mods ship `.nbt` files inside their JARs or generate them. You can extract these and place them in the schematics folder. Common locations inside mod JARs:

- `data/<modid>/structures/` — worldgen structures
- `data/<modid>/structure/` — alternate path
- `assets/<modid>/structures/` — some mods use assets

Open the mod JAR with any zip tool to browse and extract `.nbt` files.

## Folder Structure & Tabs

### Basic Setup

```
config/multiblockprojector/schematics/
├── my-house.nbt                    → Tab: "Custom NBTs"
├── cool-bridge.nbt                 → Tab: "Custom NBTs"
├── bobs-builds/                    → Tab: "Bobs Builds"
│   ├── crusher.nbt
│   └── smelter.nbt
└── medieval-pack/                  → Tab: "Medieval Pack"
    ├── tower.nbt
    ├── walls/
    │   └── gatehouse.nbt           → Still tab: "Medieval Pack"
    └── houses/
        └── cottage.nbt             → Still tab: "Medieval Pack"
```

### Tab Rules

- **Loose files** (directly in the schematics folder) go under the **"Custom NBTs"** tab
- **First-level subfolders** become their own tabs — the folder name is prettified into the tab name
- **Deeper subfolders** are flattened into their first-level parent's tab. `medieval-pack/walls/gatehouse.nbt` appears under "Medieval Pack", not a separate "Walls" tab
- Tab names are derived from folder names: underscores and hyphens become spaces, words are title-cased. `bobs-builds` becomes "Bobs Builds"

### Create Integration

When the Create mod is loaded, the projector also scans the game's `schematics/` folder (where Create saves its Schematic and Quill captures). All files there appear under a single **"Create Schematics"** tab with no subfolder logic.

### Tab Ordering in the GUI

1. **All** tab (always first — shows everything from all sources)
2. Registry tabs (Vanilla, Immersive Engineering, Mekanism, etc.)
3. Schematic tabs (Custom NBTs, Create Schematics, your subfolders — alphabetical)

## Display Name Rules

File and folder names are converted to display names:

| Raw Name | Display Name |
|----------|-------------|
| `my_cool_build.nbt` | My Cool Build |
| `bobs-tools` | Bobs Tools |
| `medieval_castle.nbt` | Medieval Castle |
| `3x3-furnace.nbt` | 3x3 Furnace |

The `.nbt` extension is stripped. Underscores and hyphens become spaces. Each word is title-cased.

## How Blocks Are Interpreted

When a `.nbt` file is loaded, each block position is interpreted as follows:

| Block in .nbt | Projector Behavior |
|---------------|-------------------|
| **Solid block** (stone, iron, etc.) | Projected as a ghost block. Validated — must be placed correctly. |
| **Air** (`minecraft:air`) | Not rendered when correct. Validated — flags an error if something occupies the space. |
| **Structure void** (`minecraft:structure_void`) | Completely ignored. Not rendered, not validated. Use this for "don't care" positions. |

### Air vs Structure Void

This distinction matters when capturing structures:

- **Air** means "this space must be empty." If a player builds something in an air position, the projector shows a red tint and marks it as incorrect.
- **Structure void** means "ignore this position entirely." The projector does not care what is here.

When capturing a structure with Create's Schematic and Quill, all empty space within the bounding box is saved as air. This means the projector will validate that those spaces remain empty. If you don't want that, either:
- Tighten your capture bounding box to only include the blocks you care about
- Use Structure Blocks with structure void blocks placed in positions you want ignored

## Rendering & Validation Rules

These rules apply identically to both API-registered multiblocks and schematic-loaded structures.

### Ghost Block Colors

| Situation | What You See | Color |
|-----------|-------------|-------|
| Block needs to be placed here | Translucent ghost of the expected block | White `(1.0, 1.0, 1.0, 0.4)` |
| Correct block already placed | Nothing (ghost hidden) | — |
| Wrong block placed | Translucent ghost of the expected block overlaid | Red `(1.0, 0.3, 0.3, 0.4)` |
| Space should be empty but has a block | Translucent ghost of the offending block | Red `(1.0, 0.3, 0.3, 0.4)` |
| Structure void position | Nothing, regardless of world state | — |

### Chat Messages

- **"Incorrect block placed!"** (red) — appears when a newly placed block doesn't match the expected block at that position, or when a block is placed where air is expected
- **"Multiblock structure completed!"** (green) — appears when every position in the structure is valid

### Auto-Build

Auto-build (creative/operator) places all missing solid blocks at once. It does **not** remove blocks at air positions — it only adds, never destroys.

## Schematic Entries vs Registry Entries

| Feature | Registry (API) | Schematic (.nbt) |
|---------|---------------|------------------|
| Source | Mod code via `RegisterEvent` | `.nbt` files on disk |
| Size variants | Supported (Small/Medium/Large) | Always single-size |
| Block groups | Supported (cycling alternatives) | Not supported (one block per position) |
| Air validation | Not available (air is omitted) | Supported (air positions are validated) |
| Hot-reload | Requires game restart | Rescanned every time the GUI opens |
| Persistence | Always available | Can be added/removed by the player |

## Tips & Tricks

### Organizing for Modpacks

Modpack authors can ship pre-made schematics by placing `.nbt` files in the config folder within their modpack distribution:

```
config/multiblockprojector/schematics/
├── modpack-structures/
│   ├── starter-base.nbt
│   ├── mob-farm.nbt
│   └── nether-portal-room.nbt
└── community-builds/
    └── ...
```

Each subfolder becomes a separate tab in the GUI. Players can delete any schematic they don't want.

### Capturing Only What You Need

When using Create's Schematic and Quill, the entire bounding box is captured including air. To avoid unwanted air validation:

- **Minimize the bounding box.** Only select the blocks that matter.
- **Place structure void blocks** (`/give @s structure_void`) in positions you want the projector to ignore before capturing.
- **Accept the air validation** if you want the projector to enforce that certain spaces remain clear (useful for interior rooms, doorways, etc.).

### Working with Large Structures

- `.nbt` files up to ~100MB compressed are supported
- Structures are loaded lazily — the file is only read from disk when you select the entry for preview or projection, not during folder scanning
- Folder scanning only reads the `size` tag from each file (fast), not the full block data
- Very large structures (thousands of blocks) may cause brief lag on first load

### File Management

- Schematics are rescanned every time the projector GUI opens — add or remove files at any time without restarting
- If a schematic file is deleted while it's selected in the projector, the projector resets to the selection screen
- The projector remembers your last selected schematic between GUI opens (stored in the projector item's NBT data)

### First-Launch Examples

The mod ships example `.nbt` files that are auto-copied to `config/multiblockprojector/schematics/examples/` on first launch. These demonstrate the system and can be freely deleted. A `.initialized` marker file in the schematics folder prevents re-copying — delete it if you want the examples restored.

### Create Compatibility

Create is an **optional** dependency. The projector reads vanilla `.nbt` format directly and does not require Create to be installed. When Create is present, the projector additionally scans Create's `schematics/` folder for convenience.
