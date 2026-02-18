# Schematic / NBT File Support Design

**Date:** 2026-02-18
**Status:** Approved

## Overview

Add support for loading `.nbt` structure files into the Multiblock Projector. Players can capture structures using Create's Schematic and Quill (or vanilla structure blocks) and project them for building assistance. Create remains an optional dependency — our mod reads vanilla `.nbt` format directly.

## File Discovery & Tab Organization

### Folder Structure

```
config/multiblockprojector/schematics/
├── example-house.nbt          → Tab: "Custom NBTs"
├── examples/                  → Tab: "Examples" (auto-copied on first launch)
│   ├── small-tower.nbt
│   └── l-shaped-wall.nbt
└── bobs-tools/                → Tab: "Bobs Tools"
    ├── crusher.nbt
    └── machines/
        └── smelter.nbt        → Still tab: "Bobs Tools" (flattened to first subfolder)
```

When Create is loaded, additionally scan:

```
schematics/                    → Tab: "Create Schematics"
├── my_base.nbt
└── subfolder/thing.nbt        → Still tab: "Create Schematics" (no subfolder logic)
```

### Display Name Rules

- **File:** `my_cool_build.nbt` → "My Cool Build" (strip `.nbt`, replace `_` and `-` with spaces, title case)
- **Subfolder:** `bobs-tools` → "Bobs Tools" (same rule)
- **Loose files** (no subfolder): tab name "Custom NBTs"
- **Create folder**: tab name "Create Schematics"

### Recursive Scan with First-Level Flattening

Files at any depth are discovered, but the tab is always determined by the first subfolder. `bobs-tools/machines/crusher.nbt` goes under "Bobs Tools".

## NBT Loading & Conversion

### Reading `.nbt` Files

- Use `NbtIo.readCompressed()` to load the file
- Feed into `StructureTemplate.load()` to get the full structure data
- Extract block palette and position list from the template

### Conversion to `Map<BlockPos, BlockEntry>`

| NBT Block | Conversion | Projected | Validated |
|---|---|---|---|
| Solid block | `SingleBlock(blockState)` | Yes | Yes |
| `minecraft:air` | `SingleBlock(Blocks.AIR.defaultBlockState())` | See rendering rules | Yes — flags if occupied |
| `minecraft:structure_void` | Omitted from map | No | No |

Structures are loaded lazily — only when a player selects an entry for preview/projection, not at scan time.

## Rendering & Validation Specification

**Pre-implementation requirement:** Before implementing any rendering changes, exhaustively verify that red tint for incorrect solid blocks does not already exist somewhere in the codebase.

| Position State | World State | Render | Validation |
|---|---|---|---|
| **Solid block** | Empty (air) | White translucent ghost | Missing — needs placement |
| **Solid block** | Correct block | Not rendered | Valid |
| **Solid block** | Wrong block | Red-tinted ghost of expected block overlaid | Invalid — wrong block |
| **Air** | Empty (air) | Not rendered | Valid |
| **Air** | Has a block | Red-tinted overlay on the occupying block | Invalid — should be empty |
| **Structure void** | Anything | Not rendered | Skipped entirely |

### Chat Messages (existing behavior)

- "Incorrect block placed!" in red when a new invalid block is detected
- "Multiblock structure completed!" in green when all positions are valid

This spec applies to both registry multiblocks and schematic-loaded structures identically.

## SchematicIndex — Dynamic Parallel System

New class `SchematicIndex` (client-side, parallel to `MultiblockIndex`):

```
SchematicIndex
├── scan()           — walks folder tree, discovers .nbt files
├── get()            — returns cached results
├── getForTab(id)    — returns entries for a specific tab
├── getTabs()        — returns all tab labels
└── getById(id)      — lookup a specific schematic entry
```

### Scan Sources (in order)

1. `config/multiblockprojector/schematics/` — always scanned
2. `schematics/` (Create's folder) — only when `ModList.get().isLoaded("create")`

### ID Scheme

Schematic entries use `ResourceLocation`-style IDs derived from path:

- `multiblockprojector:custom/bobs-tools/crusher` for `schematics/bobs-tools/crusher.nbt`
- `multiblockprojector:create/my_base` for Create's `schematics/my_base.nbt`
- `multiblockprojector:custom/example-house` for loose files in root

### Lifecycle

- First scan triggered on projector GUI open
- Results cached in memory
- Rescanned each time GUI opens (folder walk is cheap — just file listing, no NBT parsing)
- Actual `.nbt` parsing is lazy — only when player selects an entry for preview/projection

### Example Files on First Launch

- Mod ships `.nbt` resources in JAR under `assets/multiblockprojector/examples/`
- On first launch, copies them to `config/multiblockprojector/schematics/examples/`
- A marker file (`config/multiblockprojector/schematics/.initialized`) prevents re-copying if player deletes them

## GUI Integration

### Tab Ordering

Registry tabs first (Vanilla, IE, Mekanism, etc.), then schematic tabs (Custom NBTs, Examples, Bobs Tools, Create Schematics, etc.). "All" tab remains first and includes everything from both sources.

### Unified List Entries

New `IProjectorEntry` wrapper interface that both `MultiblockDefinition` and `SchematicEntry` satisfy:

- `displayName()` — `Component` for list display
- `structure()` — provides `MultiblockStructure` for projection/preview
- `id()` — unique `ResourceLocation` identifier

`MultiblockSelectionList` and `SimpleMultiblockPreviewRenderer` updated to accept `IProjectorEntry` instead of `MultiblockDefinition`.

### Selection Flow

When a player selects a schematic entry, the `Settings` object stores the schematic ID. Settings distinguishes source via a `source` field (enum: `REGISTRY` or `SCHEMATIC`).

## Settings Storage

### Changes to `Settings`

- Add `source` field: enum `REGISTRY` or `SCHEMATIC` (default `REGISTRY`)
- When `source == REGISTRY`: existing behavior, lookup from `MultiblockIndex`
- When `source == SCHEMATIC`: lookup from `SchematicIndex` by ID

Both stored in item's `CustomData` NBT. Old projector items without `source` default to `REGISTRY` — no migration needed.

### Projection Flow for Schematics

1. Player opens GUI → `SchematicIndex.scan()` → tabs merge into GUI
2. Player selects schematic → `Settings` stores `(source=SCHEMATIC, id=...)`
3. Player enters projection mode → lazy `.nbt` load → `MultiblockStructure`
4. `MultiblockProjection` receives structure — identical from here on
5. Rotation, mirroring, placement, building mode, validation — all unchanged

### Error Handling

- Corrupt/unreadable `.nbt` file → skip during scan, log warning
- File deleted after selection → "Schematic not found" message, return to `NOTHING_SELECTED`
- Empty structure (only air/voids) → skip during scan

### Size Variants

Schematics are always single-size. No variant selector shown for schematic entries.

## Test Multiblock Migration

### Current State

Test multiblocks in `src/testmod/` register via `TestMultiblockRegistrar` during `RegisterEvent`. Dev-only, not in production JAR.

### Migration

1. Create `.nbt` structure files for each test multiblock
2. Ship as JAR resources: `assets/multiblockprojector/examples/*.nbt`
3. On first launch, copy to `config/multiblockprojector/schematics/examples/`
4. Remove `src/testmod/` source set, `TestMultiblockRegistrar`, and related Gradle config

## New & Modified Files

### New Files

- `SchematicEntry` record — display name, tab ID, file path, lazy structure loader
- `SchematicIndex` — folder scanning, caching, tab/lookup API
- `IProjectorEntry` interface — common interface for registry and schematic entries
- `SchematicLoader` — reads `.nbt`, converts `StructureTemplate` → `MultiblockStructure`
- Example `.nbt` resources in `assets/multiblockprojector/examples/`

### Modified Files

- `ProjectorScreen` — merge schematic tabs, use `IProjectorEntry`
- `MultiblockSelectionList` — accept `IProjectorEntry`
- `SimpleMultiblockPreviewRenderer` — accept `IProjectorEntry`
- `ProjectionRenderer` — add red tint for incorrect blocks (verify doesn't exist first!), add red tint for air violations
- `BlockValidationManager` — wire up air block validation
- `Settings` — add `source` enum, schematic ID lookup
- `ProjectorClientHandler` — handle schematic source in projection flow
- `build.gradle` — remove testmod source set config

### Removed Files

- `src/testmod/` directory entirely
- `TestMultiblockRegistrar`

### Unchanged

- `MultiblockProjection` — receives `MultiblockStructure` regardless of source
- `MultiblockDefinition`, `BlockEntry`, `SingleBlock`, `BlockGroup` — API untouched
- `MultiblockIndex`, `LegacyAdapterRegistrar` — registry system untouched
- All existing adapters (IE, Mekanism, Blood Magic) — untouched
