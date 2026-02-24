# Scroll-to-Zoom for Multiblock Preview

## Summary

Add scroll wheel zoom to the right-side multiblock preview in the projector selection GUI. Players can scroll to zoom in/out of the 3D preview, with zoom resetting to auto-fit when selecting a different multiblock.

## Behavior

- Scroll wheel over the right panel adjusts zoom
- Zoom is a multiplier on top of the existing auto-calculated scale
- Clamped to 0.5x - 3.0x of auto-fit scale
- Resets to 1.0x (auto-fit) when a new multiblock is selected
- Left panel scroll (multiblock list) unaffected

## Changes

### `SimpleMultiblockPreviewRenderer`

- Add `zoomMultiplier` field (default `1.0f`)
- Add `onMouseScrolled(double scrollY)` — adjusts `zoomMultiplier` by `0.1f` per tick, clamped to `[0.5f, 3.0f]`
- Add `resetZoom()` — sets `zoomMultiplier` to `1.0f`
- Render method: effective scale = `scale * zoomMultiplier`

### `ProjectorScreen`

- `mouseScrolled`: if cursor over right panel (`mouseX > leftPanelWidth`), forward to `previewRenderer.onMouseScrolled(scrollY)` and consume event; otherwise delegate to super
- When selected multiblock changes, call `previewRenderer.resetZoom()`
