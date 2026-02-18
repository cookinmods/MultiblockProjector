# Projector GUI Improvements

## Problems

1. **Mod tabs are unreadable** — Horizontal tab row with `MAX_VISIBLE_TABS = 4` squishes long mod names (e.g. "Immersive Engineering"). Text scrolls within tiny buttons.
2. **Multiblock list wastes space** — Hard-coded `ENTRIES_PER_PAGE = 7` regardless of screen/window size. Fullscreen shows same 7 entries.
3. **Scrollbar disappears** — Clicking a multiblock name to preview it causes the scrollbar to vanish.

## What We Implemented

All changes are in `ProjectorScreen.java`. No other files affected.

### 1. Replace Tab Row with Mod Selector Sub-Screen

Removed horizontal tab buttons, scroll arrows, and `MAX_VISIBLE_TABS`/`tabScrollOffset` state.

Replaced with:
- **Button**: Single `Button` widget spanning left panel width showing `"[Mod Name] ▼"`.
- **Sub-screen**: Clicking the button opens `ModSelectorScreen` (inner class extending `Screen`). Renders as a centered modal panel over a dimmed background with a scrollable `AbstractSelectionList` of mod tabs.
- **Closing**: Click a mod to select and return, click outside the panel, or press Escape.

A dropdown overlay approach was attempted first but abandoned — the 3D preview renderer writes depth buffer values (z=100 + blockZ×scale, up to ~350) that block `RenderType.gui()` overlay rendering. Using a separate `Screen` completely avoids depth buffer conflicts since it's a separate render pass.

### 2. Migrate Multiblock List to AbstractSelectionList

Replaced the old `Button`-per-entry approach with a `MultiblockListWidget` inner class extending `AbstractSelectionList`. This provides:
- **Native scrollbar** that always works (no custom rendering needed)
- **Dynamic height** — list fills available space between the mod selector button and the Select button (pinned to bottom)
- **Proper scissoring** — entries clip to list bounds

Blur prevention overrides on the list widget:
- `renderListBackground()` → empty (parent screen draws panel background)
- `renderListSeparators()` → empty
- `enableScissor()` → clips to exact list bounds

### 3. Fix Scrollbar Bug

**Root cause**: The old implementation called `rebuildWidgets()` inside `selectMultiblockForPreview()`, which called `clearWidgets()` + `init()` — destroying and recreating the entire widget tree including the scrollbar state.

**Fix**: `selectMultiblockForPreview()` now directly updates size button visibility without calling `rebuildWidgets()`. The `AbstractSelectionList` maintains its own scroll state across selections.

### 4. Blur Prevention

Both `ProjectorScreen` and `ModSelectorScreen` override `renderBackground()` to empty, preventing the default 1.21+ blur shader. Both list widgets override `renderListBackground()` and `renderListSeparators()` similarly.

The `ModSelectorScreen` also overrides `renderSelection()` to empty on its list widget, since `AbstractSelectionList`'s built-in selection highlight uses different bounds (±2px padding) than the entry-level hover/selected fills.

## Fields Removed

- `MAX_VISIBLE_TABS`, `tabScrollOffset`, `tabLeftButton`, `tabRightButton`, `tabButtons`
- `ENTRIES_PER_PAGE` constant
- All custom scrollbar rendering code

## Fields Added

- `modSelectorButton` (Button widget)
- `multiblockList` (MultiblockListWidget — AbstractSelectionList)
- `leftPanelWidth`, `listStartY` (computed in `init()`)

## Inner Classes Added

- `MultiblockListWidget extends AbstractSelectionList` — scrollable multiblock list with native scrollbar
- `ModSelectorScreen extends Screen` — modal mod tab selector with its own `ModTabListWidget`
