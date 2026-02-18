# Blood Magic Altar Support - Cycling Rune Blocks Implementation

## Overview
Implement support for Blood Magic altar tiers (1-6) with cycling rune previews and flexible validation that accepts any valid rune type in rune positions.

## Key Requirements
1. **Preview Cycling**: Rune positions cycle through acceptable rune types (1 second per rune)
2. **Tier-Specific Runes**:
   - Tier 2 altars: Show 11 basic runes only
   - Tier 3+ altars: Show 21 runes (11 basic + 10 tier-2 variants, no blank tier-2)
3. **Validation**: Any valid rune type is accepted during building (matches real Blood Magic behavior)
4. **Creative Mode**: Auto-build places blank runes in all rune positions
5. **Survival Mode**: Player places any acceptable rune type

## Implementation Tasks

### Task 1: Create ICyclingBlockMultiblock Interface
**File**: `src/main/java/com/multiblockprojector/api/ICyclingBlockMultiblock.java`

New interface extending `IUniversalMultiblock` that defines positions with multiple acceptable block states:
```java
public interface ICyclingBlockMultiblock extends IUniversalMultiblock {
    // Get acceptable blocks for a position that should cycle
    List<BlockState> getAcceptableBlocks(BlockPos structurePos);

    // Check if a position has cycling blocks
    boolean hasCyclingBlocks(BlockPos structurePos);

    // Get the default block for creative auto-build
    BlockState getDefaultBlock(BlockPos structurePos);
}
```

### Task 2: Update BloodMagicMultiblockAdapter
**File**: `src/main/java/com/multiblockprojector/api/adapters/BloodMagicMultiblockAdapter.java`

Changes:
- Implement `ICyclingBlockMultiblock` for altar tier classes
- Load all 21 rune block types via reflection (11 basic + 10 tier-2, no RUNE_2_BLANK)
- Track which positions are rune positions vs fixed blocks (altar, pillars, capstones)
- Store rune position set for each altar tier class
- Return appropriate rune lists based on altar tier:
  - Tier 2: 11 basic runes (RUNE_BLANK, RUNE_SPEED, etc.)
  - Tier 3+: 21 runes (basic + RUNE_2_* variants except blank)
- `getDefaultBlock()` returns blank rune for rune positions

Rune Block Registry Names to load:
- Basic (11): RUNE_BLANK, RUNE_SPEED, RUNE_SACRIFICE, RUNE_SELF_SACRIFICE, RUNE_CAPACITY, RUNE_CAPACITY_AUGMENTED, RUNE_CHARGING, RUNE_ACCELERATION, RUNE_DISLOCATION, RUNE_ORB, RUNE_EFFICIENCY
- Tier-2 (10): RUNE_2_SPEED, RUNE_2_SACRIFICE, RUNE_2_SELF_SACRIFICE, RUNE_2_CAPACITY, RUNE_2_CAPACITY_AUGMENTED, RUNE_2_CHARGING, RUNE_2_ACCELERATION, RUNE_2_DISLOCATION, RUNE_2_ORB, RUNE_2_EFFICIENCY

### Task 3: Update SimpleMultiblockPreviewRenderer for Cycling
**File**: `src/main/java/com/multiblockprojector/client/gui/SimpleMultiblockPreviewRenderer.java`

Changes:
- Add cycling timer tracking (1 second = 1000ms interval)
- Track current cycle index (0 to N-1 where N = number of acceptable blocks)
- In render loop, for each StructureBlockInfo:
  - Check if multiblock is ICyclingBlockMultiblock
  - If position has cycling blocks, get current cycled block state based on timer
  - Render that block instead of the stored block state
- All cycling positions cycle in sync (same index)

### Task 4: Update ProjectionRenderer for World Cycling
**File**: `src/main/java/com/multiblockprojector/client/render/ProjectionRenderer.java`

Changes:
- Add static cycling timer (use System.currentTimeMillis() or game tick)
- Track global cycle index that updates every 1000ms
- When rendering ghost blocks:
  - Check if projection's multiblock is ICyclingBlockMultiblock
  - For cycling positions, get current cycled block state
  - Render that block as the ghost

### Task 5: Update BlockValidationManager for Flexible Validation
**File**: `src/main/java/com/multiblockprojector/client/BlockValidationManager.java`

Changes:
- Modify `blocksMatch()` method to handle cycling blocks
- For `ICyclingBlockMultiblock`:
  - Get list of acceptable blocks for position
  - Check if placed block matches ANY of the acceptable blocks
  - Return valid if any match
- Non-cycling positions (altar, pillars, caps) use existing exact match logic

### Task 6: Update MessageAutoBuild for Creative Auto-Build
**File**: `src/main/java/com/multiblockprojector/common/network/MessageAutoBuild.java`

Changes:
- In `performAutoBuild()`, check if multiblock is `ICyclingBlockMultiblock`
- For cycling positions:
  - Get the StructureBlockInfo's position
  - Call `getDefaultBlock(pos)` to get blank rune
  - Use that instead of `info.getModifiedState()`
- Non-cycling positions use normal block state

### Task 7: Verify/Fix Altar Tier Structures
**File**: `src/main/java/com/multiblockprojector/api/adapters/BloodMagicMultiblockAdapter.java`

Review and verify altar structures match actual Blood Magic requirements:
- Tier 1: Just altar block (1x1x1) - Already correct
- Tier 2: Altar + 8 runes in 3x3 ring below (3x2x3)
- Tier 3: Adds outer rune ring + 4 pillars (3 tall) + glowstone caps (7x4x7)
- Tier 4: Adds another outer ring + taller pillars + bloodstone caps (11x6x11)
- Tier 5: Adds outer ring + beacon pillars + hellforged caps (17x7x17)
- Tier 6: Adds outer ring + tall pillars + crystal cluster caps (23x9x23)

## Testing Checklist
- [ ] Preview shows cycling runes in GUI (1 sec interval)
- [ ] Tier 2 altar cycles through 11 basic runes
- [ ] Tier 3+ altars cycle through 21 runes (basic + tier-2)
- [ ] Ghost projection in world shows cycling runes
- [ ] Placing any valid rune counts as correct during building
- [ ] Creative auto-build places blank runes in rune positions
- [ ] Non-rune positions (altar, pillars, caps) don't cycle and validate exactly
- [ ] All 6 altar tier structures render correctly
- [ ] Fallback blocks work when Blood Magic not installed

## Files Modified Summary
1. **NEW**: `api/ICyclingBlockMultiblock.java` - Interface for cycling block support
2. **MODIFY**: `api/adapters/BloodMagicMultiblockAdapter.java` - Implement cycling, load all runes
3. **MODIFY**: `client/gui/SimpleMultiblockPreviewRenderer.java` - Add cycling render logic
4. **MODIFY**: `client/render/ProjectionRenderer.java` - Add cycling logic for world ghosts
5. **MODIFY**: `client/BlockValidationManager.java` - Accept any valid rune in validation
6. **MODIFY**: `common/network/MessageAutoBuild.java` - Use default blocks in creative

## Sources
- [Blood Altar - Feed The Beast Wiki](https://ftbwiki.org/Blood_Altar)
- [Blood Altar Structure Information](https://mods-of-minecraft.fandom.com/wiki/Blood_Altar)
