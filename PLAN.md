# Plan: Mekanism Fission Reactor Variable-Size Support

## Summary
Add support for Mekanism's Fission Reactor multiblock with three predefined sizes (Small, Medium, Large) and +/- buttons in the GUI to cycle between them.

## Design Decisions (from user input)
- **Scope**: Start with Fission Reactor only (proof of concept)
- **Size Options**: Three predefined sizes (min/mid/max) - not continuous scaling
- **UI Location**: GUI selection screen only
- **Block Types**: Real Mekanism blocks via reflection, Iron Block fallback

## Fission Reactor Size Presets
| Size | Dimensions (WxHxD) | Description |
|------|-------------------|-------------|
| Small | 3x4x3 | Minimum valid size |
| Medium | 9x10x9 | Mid-range practical size |
| Large | 17x18x17 | Near-maximum size |

## Implementation Tasks

### Task 1: Create IVariableSizeMultiblock Interface
**File**: `src/main/java/com/multiblockprojector/api/IVariableSizeMultiblock.java`

New interface extending `IUniversalMultiblock`:
```java
public interface IVariableSizeMultiblock extends IUniversalMultiblock {
    // Get available size presets
    List<SizePreset> getSizePresets();

    // Get structure at a specific size
    List<StructureBlockInfo> getStructureAtSize(Level world, Vec3i size);

    // Check if this multiblock supports variable sizing
    default boolean isVariableSize() { return true; }
}

// Inner class or separate class for size presets
public record SizePreset(String name, Vec3i size, Component displayName) {}
```

### Task 2: Re-enable and Refactor Mekanism Adapter
**File**: `src/main/java/com/multiblockprojector/api/adapters/MekanismFissionReactorAdapter.java`

- Uncomment the file
- Refactor `FissionReactorMultiblock` to implement `IVariableSizeMultiblock`
- Define three size presets: Small (3x4x3), Medium (9x10x9), Large (17x18x17)
- Implement `getStructureAtSize()` to generate correct fission reactor shell
- Use reflection to get actual Mekanism blocks:
  - `mekanism.generators.common.registries.GeneratorsBlocks.FISSION_REACTOR_CASING`
  - `mekanism.generators.common.registries.GeneratorsBlocks.FISSION_REACTOR_PORT`
  - `mekanism.generators.common.registries.GeneratorsBlocks.FISSION_REACTOR_LOGIC_ADAPTER`
- Fall back to Iron Block placeholders if reflection fails
- Register only ONE multiblock (not 8 separate variants)

### Task 3: Update UniversalMultiblockHandler
**File**: `src/main/java/com/multiblockprojector/api/UniversalMultiblockHandler.java`

- Uncomment Mekanism loading code (lines 87-103)
- Update import statement (line 6)

### Task 4: Extend Settings Class for Size Selection
**File**: `src/main/java/com/multiblockprojector/common/projector/Settings.java`

Add:
- `private int sizePresetIndex = 0;` - Index into size preset list
- `KEY_SIZE_PRESET = "sizePreset"` constant
- Getter/setter for size preset index
- Serialize/deserialize in `toNbt()`/constructor

### Task 5: Update ProjectorScreen GUI with Size Controls
**File**: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java`

Add size controls when variable-size multiblock is selected:
- Show current size label (e.g., "Size: Medium (9x10x9)")
- Add "-" button to go to smaller preset
- Add "+" button to go to larger preset
- Disable - at minimum, + at maximum
- Position: Below the preview in right panel, or near select button

UI layout:
```
[Preview Area]
Size: Medium (9x10x9)
[ - ]  [ + ]
```

### Task 6: Update Preview Renderer
**File**: `src/main/java/com/multiblockprojector/client/gui/SimpleMultiblockPreviewRenderer.java`

- Accept size parameter when setting multiblock
- Call `getStructureAtSize()` for variable-size multiblocks
- Recalculate scale when size changes

### Task 7: Update Projection System
**File**: `src/main/java/com/multiblockprojector/common/projector/MultiblockProjection.java`

- Check if multiblock is `IVariableSizeMultiblock`
- Use `getStructureAtSize()` with selected preset size
- Pass size from Settings

### Task 8: Update Network Sync
**File**: `src/main/java/com/multiblockprojector/common/network/MessageProjectorSync.java`

- Size preset index is already in Settings NBT
- No additional changes needed if Settings serialization is correct

### Task 9: Add Language Keys
**File**: `src/main/resources/assets/multiblockprojector/lang/en_us.json`

Add:
```json
"gui.multiblockprojector.size": "Size: %s",
"gui.multiblockprojector.size.small": "Small",
"gui.multiblockprojector.size.medium": "Medium",
"gui.multiblockprojector.size.large": "Large",
"gui.multiblockprojector.button.size_increase": "+",
"gui.multiblockprojector.button.size_decrease": "-"
```

### Task 10: Testing
- Test Fission Reactor at all three sizes
- Verify projection renders correctly in-world
- Verify size persists when closing/reopening GUI
- Verify +/- buttons enable/disable correctly at bounds
- Test with Mekanism not installed (should fall back gracefully)

## Files Modified Summary
1. **Create**: `api/IVariableSizeMultiblock.java`
2. **Modify**: `api/adapters/MekanismFissionReactorAdapter.java` (uncomment + refactor)
3. **Modify**: `api/UniversalMultiblockHandler.java` (uncomment Mekanism)
4. **Modify**: `common/projector/Settings.java` (add size preset)
5. **Modify**: `client/gui/ProjectorScreen.java` (add +/- buttons)
6. **Modify**: `client/gui/SimpleMultiblockPreviewRenderer.java` (size parameter)
7. **Modify**: `common/projector/MultiblockProjection.java` (use size)
8. **Modify**: `resources/assets/.../lang/en_us.json` (translations)
9. **Delete or keep disabled**: `api/adapters/MekanismMultiblockAdapter.java` (old approach)

## Out of Scope (Future Work)
- Other Mekanism multiblocks (Dynamic Tank, Induction Matrix, etc.)
- In-world size controls
- Continuous size scaling
- Internal components (fuel assemblies, control rods)
- Create mod multiblocks

## Risk Mitigation
- Reflection may fail if Mekanism updates block registry names
- Fall back to Iron Blocks ensures functionality even if reflection fails
- Test with both Mekanism installed and not installed
