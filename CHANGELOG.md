# Changelog

## 1.0.0 — Initial Release

### Projector Items

- **Multiblock Projector** — ghost block hologram that follows your aim, locks in place for manual building with real-time validation and auto-completion detection
- **Creative Multiblock Fabricator** — instant animated auto-build in creative mode
- **Multiblock Fabricator** — survival auto-builder that links to an external energy source and container, places blocks one at a time using FE and materials
- **Battery Multiblock Fabricator** — survival auto-builder with internal 32M FE battery, links to a container only
- First-person held item renders like a map for clear visibility

### Multiblock Selector GUI

- Full-screen 50/50 split layout with mod tab selector, scrollable multiblock list, and interactive 3D preview
- Mouse drag rotation and scroll wheel zoom on the 3D preview
- Material requirements panel with inventory comparison (green/red per block type)
- Size variant selector (+/- buttons) for variable-size multiblocks
- Remembers last selected mod tab across screen openings

### Ghost Block Rendering

- Translucent hologram overlays rendered at full brightness
- White/translucent for missing blocks, red tint for incorrect or misplaced blocks
- Air position validation — red overlay on blocks occupying positions that must be empty
- Automatic completion detection clears the projection when all blocks match

### Mod Support

- **Immersive Engineering** — all multiblocks auto-discovered via reflection at runtime
- **Mekanism & Mekanism Generators** — Dynamic Tank, Boiler, Evaporation Plant, Induction Matrix, SPS, Fission Reactor, Fusion Reactor, Industrial Turbine, and all Generators multiblocks with variable sizing
- **Blood Magic** — all 6 altar tiers (Weak through Transcendent) with cycling rune previews for both basic (11) and tier-2 (21) rune types
- **Vanilla Minecraft** — Beacon (Tier 1–4), Nether Portal (3 sizes), Conduit, Iron Golem, Snow Golem, Wither
- **Create** — Add to Clipboard button writes material checklist in Create's native MaterialChecklist format

### NBT Schematic Support

- Load any vanilla `.nbt` structure file as a projectable multiblock
- Custom schematics folder at `config/multiblockprojector/schematics/` with automatic tab creation from subfolders
- Create mod schematics folder scanned automatically when Create is installed
- Example schematics included on first launch
- Structure void blocks ignored, air blocks validated as enforced empty space

### Mod Developer API

- NeoForge custom registry (`ProjectorAPI.MULTIBLOCK_REGISTRY_KEY`) for third-party multiblock registration
- `MultiblockDefinition` with fixed-size and variable-size support via `SizeVariant`
- `BlockEntry` sealed interface: `SingleBlock`, `BlockGroup` (cycling preview + flexible matching), `AirEntry`
- `MultiblockCategory` with built-in types (Processing, Power, Storage, Crafting, General) and custom category support
- Lazy `StructureProvider` for on-demand structure generation
