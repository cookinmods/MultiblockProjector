# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build System

This is a Minecraft 1.21.1 NeoForge mod using Gradle as the build system.

### Common Commands

- **Build the mod**: `./gradlew build`
- **Run development client**: `./gradlew runClient`  
- **Run development server**: `./gradlew runServer`
- **Generate data**: `./gradlew runData`
- **Clean build**: `./gradlew clean`
- **Compile Java only**: `./gradlew compileJava`

The mod JAR will be generated in `build/libs/` after building.

### Dependencies

The mod includes several mod JARs in the `libs/` directory:
- Immersive Engineering (implementation dependency)
- Mekanism and related mods (implementation dependencies)
- Create support (compileOnly - optional)
- JEI (compileOnly for API, runtimeOnly for full mod)

## Architecture Overview

### Core Concept
MultiblockProjector is a universal multiblock projection system that can work with any mod's multiblocks through adapter patterns. It provides ghost block projections with real-time validation and building assistance.

### Key Components

**Universal API System** (`api/`):
- `IUniversalMultiblock`: Interface that wraps any mod's multiblock structures
- `UniversalMultiblockHandler`: Central registry for all discovered multiblocks
- Adapter classes for specific mods (IE, Mekanism) using reflection to avoid compile-time dependencies

**Projection System** (`common/projector/`):
- `MultiblockProjection`: Core projection logic ported from Immersive Petroleum
- `Settings`: Projector state management (modes, rotation, mirroring)
- Handles rotation, mirroring, and layer-based processing

**Client-Side Rendering** (`client/`):
- `ProjectionRenderer`: Renders ghost blocks with tinting (green=correct, red=incorrect, white=missing)
- `ProjectorClientHandler`: Handles player interaction, ESC key, mouse controls
- `ProjectionManager`: Manages active projections
- `BlockValidationManager`: Real-time block validation and completion detection

**GUI System** (`client/gui/`):
- `ProjectorScreen`: Multiblock selection interface with 50/50 split layout
- `SimpleMultiblockPreviewRenderer`: 3D preview rendering in GUI
- Scrollable list with mouse drag rotation controls

**Item & Networking** (`common/`):
- `ProjectorItem`: The projector tool item with right-click GUI
- `NetworkHandler` & `MessageProjectorSync`: Client-server synchronization
- Event handlers for integration with NeoForge lifecycle

### Mod Integration Pattern

The mod uses reflection-based adapters to integrate with other mods without compile-time dependencies:

1. **Discovery Phase**: `UniversalMultiblockHandler.discoverMultiblocks()` checks for installed mods
2. **Adapter Registration**: Each mod adapter (e.g., `IEMultiblockAdapter`) uses reflection to access mod-specific multiblock systems
3. **Wrapper Creation**: Adapters create `IUniversalMultiblock` wrappers around foreign multiblock objects
4. **Universal Access**: All multiblocks can then be accessed through the same interface

### Modes of Operation

1. **NOTHING_SELECTED**: Projector is idle, right-click opens GUI
2. **PROJECTION**: Ghost projection follows player aim, left-click places, right-click rotates, ESC cancels  
3. **BUILDING**: Fixed projection at placed location, validates blocks, auto-completes when done

### Client-Server Architecture

- Client handles all rendering, GUI, and player interaction
- Server maintains projector state through `Settings` class
- `MessageProjectorSync` keeps client and server in sync
- Block validation happens client-side for immediate feedback

### File Structure Notes

- Main mod class: `UniversalProjector.java` 
- Content registration: `UPContent.java`
- Proxy pattern for client/server separation: `ClientProxy` extends `CommonProxy`
- Resources follow standard Minecraft structure in `src/main/resources/`

## Development Notes

- Uses Java 21 and NeoForge 21.1.176 for Minecraft 1.21.1
- Mod ID: `multiblockprojector`
- The projection system is based on Immersive Petroleum's implementation but updated for universal compatibility
- All mod integrations use reflection to avoid hard dependencies - mods are detected at runtime
- Test multiblocks are automatically registered if no real multiblocks are found for development purposes