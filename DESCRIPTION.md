🍳 A Cookin' Mods project — he cookin' 💬 [Discord](https://discord.com/invite/NujBZ5S7Hu) | ☕ [Support](https://ko-fi.com/cookinmods)

Hop in the Discord for support, sneak peeks, and to vote on what gets cooked next.

---

Preview, validate, and auto-build multiblock structures from any mod — no guessing, no wiki tabs, no misplaced blocks. **Multiblock Projector** renders ghost block holograms directly in your world, validates every block in real time, and can auto-build entire structures using FE and materials from linked containers. A universal multiblock building guide and projection tool for NeoForge 1.21.1 with built-in support for Immersive Engineering, Mekanism, Blood Magic, Create, and vanilla structures — plus user-loaded NBT schematics.

---

## At a Glance

- **Ghost block projections** — translucent hologram overlays show exactly where every block goes
- **Real-time validation** — white for missing, red for incorrect, auto-detects completion
- **4 projector tiers** — from manual building guide to fully automated survival fabrication
- **Built-in mod support** — Immersive Engineering, Mekanism, Blood Magic, and vanilla multiblocks discovered automatically
- **NBT schematic loading** — drop any `.nbt` file into a folder and project it in-world
- **3D preview GUI** — browse, rotate, zoom, and inspect multiblocks before projecting
- **Material checklist** — see exactly what blocks you need, with inventory comparison and Create Clipboard export
- **Variable-size multiblocks** — resize Mekanism tanks, beacon pyramids, nether portals, and more
- **Mod developer API** — other mods can register their multiblocks via a NeoForge custom registry

---

## How to Use

1. **Right-click** with any projector to open the multiblock selector — browse by mod, preview in 3D, check material requirements
2. **Select a multiblock** to enter projection mode — a ghost hologram follows your aim, left-click to rotate, right-click to place or build
3. **Build to match** — the projection validates every block you place in real time and auto-completes when finished

## Projector Tiers

Four projector items cover every use case from early-game guidance to endgame automation:

| Item | Function | Mode |
|------|----------|------|
| **Multiblock Projector** | Ghost projection + manual building guide | Survival & Creative |
| **Creative Projector** | Instant auto-build on right-click | Creative only |
| **Multiblock Fabricator** | Animated auto-build using linked energy block + chest | Survival |
| **Battery Multiblock Fabricator** | Animated auto-build with internal 32M FE battery + linked chest | Survival |

### Multiblock Projector (Base)

The standard projector renders a ghost hologram that follows your crosshair. Right-click to lock the projection in place and enter **building mode** — blocks you place are validated in real time against the projection. When every block matches, the projection auto-clears and confirms completion.

### Multiblock Fabricator

Link an energy source and a container by sneak + right-clicking each block. Select a multiblock, aim, and right-click to begin **animated fabrication** — blocks are pulled from the linked chest and placed one by one, powered by FE from the linked energy source. FE cost scales with block hardness and structure size.

### Battery Multiblock Fabricator

Same as the Multiblock Fabricator but with an internal 32M FE battery — no external energy link needed. Charge it in any mod's charging station, link a chest, and fabricate on the go.

## Controls

| Action | Effect |
|--------|--------|
| **Right-click** (nothing selected) | Open multiblock selector GUI |
| **Left-click** (projection mode) | Rotate projection 90° |
| **Right-click** (projection mode) | Place projection / auto-build / fabricate (depends on item) |
| **Sneak + Right-click** (projection mode) | Cancel projection |
| **Right-click** (building mode) | Cancel building mode |
| **ESC** | Cancel projection or building mode |
| **Scroll wheel** (in GUI preview) | Zoom in/out on 3D preview |
| **Click + drag** (in GUI preview) | Rotate 3D preview |

Hold **Shift** on any projector's tooltip to see all controls in-game.

## Multiblock Selector GUI

The full-screen selection GUI features a 50/50 split layout:

- **Left panel** — mod tab selector, scrollable multiblock list, material requirements panel
- **Right panel** — interactive 3D block preview with mouse drag rotation and scroll wheel zoom

Select a multiblock to see its full 3D preview, material checklist (with inventory counts), and size variants if available. Variable-size structures like Mekanism tanks show +/- buttons to browse all supported sizes.

### Create Clipboard Integration

If Create is installed and you have a Clipboard in your inventory, an **"Add to Clipboard"** button appears in the requirements panel. Click it to write the full material checklist to your Clipboard in Create's native format — carry your shopping list with you.

## NBT Schematic Support

Project **any structure** — not just registered multiblocks. Drop vanilla `.nbt` structure files into the schematics folder and they appear in the projector GUI automatically:

- **Custom schematics**: `config/multiblockprojector/schematics/` — files here appear under the "Custom NBTs" tab
- **Subfolder tabs**: Create subfolders (e.g., `schematics/My Builds/`) to organize schematics into named tabs
- **Create schematics**: If Create is installed, the projector also scans `schematics/` (Create's folder) and lists them under "Create Schematics"

Structure files use the standard vanilla **StructureTemplate NBT format** — the same format used by structure blocks, Create schematics, and most structure mods. Structure void blocks are ignored; air blocks are validated as "must be empty."

Example schematics are included on first launch to demonstrate the feature.

## Built-In Mod Support

### Immersive Engineering

All IE multiblocks are discovered automatically at runtime — every registered multiblock in `MultiblockHandler` appears in the projector with correct block placement, sizing, and display names.

### Mekanism & Mekanism Generators

Full support for all Mekanism multiblock types with **variable sizing**:

- Dynamic Tank, Boiler, Evaporation Plant
- Induction Matrix, SPS, Fission Reactor, Fusion Reactor
- Industrial Turbine
- All Mekanism Generators multiblocks

Each structure supports multiple size presets, so you can preview a 3x3x3 Dynamic Tank or scale up to the maximum.

### Blood Magic

All 6 Blood Magic altar tiers (Weak through Transcendent) with **cycling rune previews** — rune positions animate through all valid rune types so you can see exactly which blocks are acceptable at each position. Supports both basic runes (11 types) and tier-2 runes (21 types).

### Vanilla Minecraft

Built-in projections for vanilla "multiblocks":

- **Beacon** (Tier 1–4 with cycling mineral blocks)
- **Nether Portal** (Minimum, Medium, Large)
- **Conduit** (full prismarine frame with cycling variants)
- **Iron Golem**, **Snow Golem**, **Wither**

## Mod Developer API

Other mods can register their multiblocks so they appear in the projector automatically. The API uses a **NeoForge custom registry** — no reflection needed.

### Quick Start

Add the API as a dependency and register during `RegisterEvent`:

```java
@SubscribeEvent
public static void onRegister(RegisterEvent event) {
    event.register(ProjectorAPI.MULTIBLOCK_REGISTRY_KEY, helper -> {
        helper.register(
            ResourceLocation.fromNamespaceAndPath("mymod", "my_machine"),
            MultiblockDefinition.fixed(
                Component.literal("My Machine"),
                "mymod",
                MultiblockCategory.PROCESSING,
                new BlockPos(3, 3, 3),
                (variant, level) -> {
                    Map<BlockPos, BlockEntry> blocks = new LinkedHashMap<>();
                    // Define your block layout...
                    return new MultiblockStructure(blocks);
                }
            )
        );
    });
}
```

### API Features

- **`MultiblockDefinition`** — fixed-size or variable-size with multiple `SizeVariant` entries
- **`BlockEntry`** — sealed interface: `SingleBlock` (exact match), `BlockGroup` (any of several blocks with cycling preview), or `AirEntry` (must be empty)
- **`MultiblockCategory`** — built-in categories (Processing, Power, Storage, Crafting, General) or create your own
- **`StructureProvider`** — lazy, thread-safe lambda that generates the block layout on demand

Variable-size multiblocks provide a list of `SizeVariant` entries, each with a label and dimensions. The projector GUI renders +/- buttons to browse sizes and shows the correct preview for each.

## Ghost Block Rendering

Projections render as translucent ghost blocks at full brightness with color-coded validation:

- **White/translucent** — block position is empty, needs to be placed
- **Red tint** — wrong block is placed at this position (or a block exists where air is required)
- **Auto-clear** — projection disappears automatically when all blocks are correctly placed

## Compatibility

- **Minecraft** 1.21.1
- **NeoForge** 21.1+
- **Java** 21
- **Immersive Engineering** — all multiblocks auto-discovered
- **Mekanism** + **Mekanism Generators** — all multiblocks with variable sizing
- **Blood Magic** — all 6 altar tiers with cycling rune previews
- **Create** — clipboard integration for material checklists + schematic folder scanning

All mod integrations are optional. Multiblock Projector works standalone with vanilla structures and NBT schematics — no dependencies beyond NeoForge.

## A Universal Alternative to Mod-Specific Guides

If you're tired of alt-tabbing to wikis to figure out where every block goes in a multiblock, or placing blocks by trial and error, Multiblock Projector gives you an in-game holographic building guide that works with every mod's multiblocks through a single tool. Think of it as a universal multiblock hologram and building assistant — like having a structure block preview for every multiblock in your modpack, with real-time validation and optional automated construction.

---

Feel free to include Multiblock Projector in any modpack — no permission needed.
