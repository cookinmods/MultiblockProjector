# Multiblock Projector Item Split — Design Document

## Overview

Split the single Multiblock Projector into four specialized items:

1. **Multiblock Projector** — base item, projection + manual building with validation
2. **Creative Multiblock Projector** — creative-tab-only, instant auto-build
3. **Multiblock Fabricator** — survival auto-builder, links to external energy source + chest
4. **Battery Powered MBF** — survival auto-builder, internal 32M FE storage + linked chest

The existing creative auto-build functionality (sneak+left-click) is removed from the base Projector entirely. Additionally, left-click and right-click behavior in PROJECTION mode is swapped across all items (left-click rotates, right-click performs the action) to prevent accidental builds.

## Item Hierarchy

```
AbstractProjectorItem (new base class)
├── ProjectorItem            — projection + manual BUILDING mode
├── CreativeProjectorItem    — projection + instant auto-build on right-click
├── FabricatorItem           — projection + animated build, links to energy source + chest
└── BatteryFabricatorItem    — projection + animated build, internal 32M FE + linked chest
```

### Registration

| Item | Registry Name | Creative Tab | Has Recipe |
|------|--------------|-------------|------------|
| Multiblock Projector | `projector` | Yes | Existing |
| Creative Multiblock Projector | `creative_projector` | Yes (creative-only) | No |
| Multiblock Fabricator | `fabricator` | Yes | Later |
| Battery Powered MBF | `battery_fabricator` | Yes | Later |

All registered in `UPContent` via `DeferredRegister`. The creative tab shows all four items.

## Interaction Flows

### Breaking Change: Click Swap in PROJECTION Mode

For **all four items**, left-click and right-click are swapped in PROJECTION mode compared to current behavior:
- **Left-click** → rotates 90° clockwise (safe, repeatable action)
- **Right-click** → performs the item-specific action (deliberate action)

This prevents accidental builds/placements from a quick mis-click.

### Multiblock Projector (base)

| Input | Mode | Action |
|-------|------|--------|
| Right-click | NOTHING_SELECTED | Opens ProjectorScreen GUI |
| Left-click | PROJECTION | Rotates 90° clockwise |
| Right-click | PROJECTION | Places fixed projection, enters BUILDING mode |
| ESC | PROJECTION / BUILDING | Cancels, returns to NOTHING_SELECTED |
| — | BUILDING | Validates placed blocks, detects completion |

No auto-build capability whatsoever. Works identically in creative and survival.

### Creative Multiblock Projector

| Input | Mode | Action |
|-------|------|--------|
| Right-click | NOTHING_SELECTED | Opens ProjectorScreen GUI |
| Left-click | PROJECTION | Rotates 90° clockwise |
| Right-click | PROJECTION | Instantly auto-builds all blocks, returns to NOTHING_SELECTED |
| ESC | PROJECTION | Cancels, returns to NOTHING_SELECTED |

No BUILDING mode. No sneak required. Sends `MessageAutoBuild` to server. No recipe — creative tab only.

### Multiblock Fabricator

| Input | Context | Action |
|-------|---------|--------|
| Sneak+right-click | On energy block | Links energy source (stores BlockPos + dimension in NBT) |
| Sneak+right-click | On container | Links chest (stores BlockPos + dimension in NBT) |
| Right-click | NOTHING_SELECTED | Opens ProjectorScreen GUI with requirements panel |
| Left-click | PROJECTION | Rotates 90° clockwise |
| Right-click | PROJECTION | Pre-validates, then animated build (see Fabrication System) |
| ESC | PROJECTION | Cancels, returns to NOTHING_SELECTED |

Linking shows chat feedback: "Linked to Energy Source at X, Y, Z" or "Linked to Container at X, Y, Z".

### Battery Powered MBF

Same as Multiblock Fabricator except:
- No energy source linking (sneak+right-click on non-containers does nothing)
- Internal 32M FE capacity, chargeable via any mod's charging mechanism
- Sneak+right-click on container → links chest (same behavior)

## Energy System

### FE Cost Formula

```
per_block = base_cost × hardness × (1 + scale_factor × total_blocks)
```

Constants:
- `base_cost`: 800
- `scale_factor`: 0.0008
- `hardness`: block's `destroyTime` property

Where `total_blocks` is the total non-air blocks in the multiblock definition.

**Example costs:**

| Block Type | Hardness | 100-block build | 200-block build | 500-block build |
|-----------|----------|----------------|----------------|----------------|
| Cobblestone | 2.0 | 1,728 FE/block (173K total) | 1,856 FE/block (371K total) | 2,240 FE/block (1.12M total) |
| Iron Block | 5.0 | 4,320 FE/block (432K total) | 4,640 FE/block (928K total) | 5,600 FE/block (2.8M total) |
| Obsidian | 50.0 | 43,200 FE/block (4.32M total) | 46,400 FE/block (9.28M total) | 56,000 FE/block (28M total) |

### Battery Powered MBF — Internal Energy Storage

- Capacity: 32,000,000 FE
- Implements `IEnergyStorage` via NeoForge item capability (`Capabilities.EnergyStorage.ITEM`)
- Compatible with: Mekanism chargepads, energy cubes, Thermal energetic infusers, etc.
- FE stored in item's `CUSTOM_DATA` component
- Visual FE bar on item (similar to durability bar but colored for energy)

### Multiblock Fabricator — Linked Energy Source

- Stores linked energy source as `BlockPos` + `ResourceKey<Level>` (dimension) in item NBT
- On fabrication: looks up block entity at stored position, queries `IEnergyStorage` capability
- Constraint: chunk must be loaded (within simulation distance or force-loaded)
- If chunk not loaded or block entity missing → fails with "Energy source not available"

## Fabrication System

### Pre-Validation

Before any block is placed, the server performs these checks:

1. Calculate total FE needed (sum of per-block costs for all non-air blocks)
2. Verify FE available >= total needed (internal storage or linked source)
3. Verify all required block items present in player inventory + linked chest
4. Verify linked chest chunk is loaded (if applicable)
5. Verify linked energy source chunk is loaded (Fabricator only)

If **any** check fails: descriptive error message sent to player, no blocks placed, no resources consumed.

### Resource Reservation

Once pre-validation passes:

1. All required FE is **extracted** from the energy source immediately
2. All required items are **removed** from inventory/chest immediately
3. Resources are held in the `FabricationTask` object

This atomic reservation prevents race conditions where another mod/player could drain energy or take items mid-build. The animated placement is cosmetic — resources are already committed.

### Animated Placement

- Server creates a `FabricationTask` registered with `FabricationManager`
- `FabricationManager` is a server-side singleton ticked via `ServerTickEvent`
- Placement order: bottom-to-top (ascending Y layers), left-to-right within each layer
- Delay: 4 ticks (0.2 seconds) between block placements
- Blocks placed via `level.setBlock()` with flag 3 (update neighbors + notify clients)
- Air entries are skipped (don't erase existing blocks)
- Ghost projections are cleared when fabrication begins (not needed during animated build)
- If player logs out mid-build: remaining blocks are placed instantly (resources already committed)

### Client-Side Feedback

- Ghost projections removed when fabrication starts
- Action bar progress: "Building... 47/186 blocks"
- Sent via `MessageFabricationProgress` packet from server

## GUI — Requirements Panel

When using a Fabricator (either variant) and a multiblock is selected in the list, a requirements panel appears on the left side between the scrollview and the select button. The multiblock scrollview height is reduced to accommodate it.

```
┌─────────────────────────┐
│ [Mod Selector Button]   │
├─────────────────────────┤
│ Multiblock List         │  ← reduced height
│  > IE Blast Furnace     │
│  > IE Coke Oven         │
│  > Mek Boiler           │
├─────────────────────────┤
│ Requirements:           │
│ ┌────┬──────────┬─────┐ │
│ │ ✓  │ Bricks    │24/24│ │  green = have all
│ │ ✗  │ Iron Block│ 3/8 │ │  red = missing some
│ │ ✓  │ Sand      │12/12│ │
│ └────┴──────────┴─────┘ │
│ FE: 1.2M / 1.8M needed │  red if insufficient
│ ⚡ Linked: (12, 64, -5) │  or "32M FE stored"
│ 📦 Chest: (10, 64, -3)  │  or "No chest linked"
├─────────────────────────┤
│       [ SELECT ]        │
└─────────────────────────┘
```

Details:
- Scrollable list of required block types with item icon, name, and "have/need" count
- Green text for sufficient quantity, red for insufficient
- FE line: available / required with color coding
- Energy source line: linked block coords (Fabricator) or "32M FE stored" (Battery MBF)
- Chest line: linked container coords or "No chest linked"
- Panel only visible for Fabricator items — base Projector and Creative Projector show full-height scrollview
- Block counts aggregated from MultiblockDefinition across all layers

## Networking

### New Packets

| Packet | Direction | Purpose |
|--------|-----------|---------|
| `MessageFabricate` | Client → Server | Fabrication request. Contains BlockPos (placement origin) + InteractionHand |
| `MessageLinkBlock` | Client → Server | Block linking. Contains BlockPos + link type enum (ENERGY / CONTAINER) |
| `MessageFabricationProgress` | Server → Client | Progress update. Contains current index / total blocks |

### Modified Packets

- `MessageAutoBuild`: Validate that player holds a `CreativeProjectorItem` (not just `isCreative()`)
- `MessageProjectorSync`: Unchanged — still handles Settings sync for all item types

### Data Storage

Link data stored in item's `CUSTOM_DATA` component alongside Settings:

```json
{
  "settings": { ... },
  "linked_energy": { "x": 12, "y": 64, "z": -5, "dim": "minecraft:overworld" },
  "linked_chest": { "x": 10, "y": 64, "z": -3, "dim": "minecraft:overworld" },
  "stored_energy": 32000000
}
```

`stored_energy` field only present on `BatteryFabricatorItem`.

## File Changes

### New Files

| File | Package | Purpose |
|------|---------|---------|
| `AbstractProjectorItem.java` | `common.items` | Base class with shared projection/GUI/rotation logic |
| `CreativeProjectorItem.java` | `common.items` | Creative-only instant auto-build |
| `FabricatorItem.java` | `common.items` | External energy + linked chest fabricator |
| `BatteryFabricatorItem.java` | `common.items` | Internal 32M FE + linked chest fabricator |
| `FabricationTask.java` | `common.fabrication` | Server-side animated placement state machine |
| `FabricationManager.java` | `common.fabrication` | Server singleton, ticks active tasks via ServerTickEvent |
| `MessageFabricate.java` | `common.network` | Client→Server fabrication request packet |
| `MessageLinkBlock.java` | `common.network` | Client→Server block linking packet |
| `MessageFabricationProgress.java` | `common.network` | Server→Client progress update packet |
| `RequirementsPanel.java` | `client.gui` | GUI widget for block/FE requirements display |

### Modified Files

| File | Changes |
|------|---------|
| `ProjectorItem.java` | Refactor to extend `AbstractProjectorItem`, remove creative auto-build code |
| `UPContent.java` | Register 3 new items, update creative tab with all 4 items |
| `ProjectorScreen.java` | Accept item type parameter, conditionally show `RequirementsPanel` when Fabricator |
| `ProjectorClientHandler.java` | Swap left/right click in PROJECTION mode, dispatch to correct handler per item type |
| `NetworkHandler.java` | Register 3 new packet types |
| `MessageAutoBuild.java` | Validate player holds `CreativeProjectorItem` (not just creative mode check) |
| `Settings.java` | Add link data fields (energy BlockPos, chest BlockPos, dimension keys) |

### Unchanged Files

- `MultiblockProjection.java` — block transformation logic unchanged
- `ProjectionRenderer.java` — ghost rendering unchanged
- `BlockValidationManager.java` — validation logic unchanged
- `ProjectionManager.java` — projection management unchanged
