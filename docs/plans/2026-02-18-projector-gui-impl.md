# Projector GUI Improvements — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the squished horizontal mod tabs with a dropdown, make the multiblock list dynamically fill available space, and fix the scrollbar disappearing bug.

**Architecture:** Single-file refactor of `ProjectorScreen.java`. Tabs become a dropdown overlay with its own scroll. The multiblock list height is computed from screen dimensions. Select button pinned to bottom.

**Tech Stack:** NeoForge 1.21.1 Screen/Button API, GuiGraphics for custom rendering.

---

### Task 1: Replace field declarations — remove tab state, add dropdown state

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java:32-42`

**Step 1: Replace the tab and page fields with dropdown and dynamic fields**

Remove these fields (lines 33-42):
```java
private final List<Button> tabButtons = new ArrayList<>();
private int tabScrollOffset = 0;
private Button tabLeftButton;
private Button tabRightButton;
private static final int MAX_VISIBLE_TABS = 4;

private int scrollOffset = 0;
private static final int ENTRIES_PER_PAGE = 7; // Reduced to make room for tabs
private static final int ENTRY_HEIGHT = 20;
private static final int TAB_HEIGHT = 25;
```

Replace with:
```java
// Dropdown state
private boolean dropdownOpen = false;
private int dropdownScrollOffset = 0;
private Button dropdownButton;
private static final int DROPDOWN_BUTTON_HEIGHT = 20;
private static final int DROPDOWN_ENTRY_HEIGHT = 18;
private static final int MAX_DROPDOWN_VISIBLE = 8;

// Multiblock list state
private int scrollOffset = 0;
private int visibleEntries = 7; // recalculated in init()
private static final int ENTRY_HEIGHT = 20;
```

**Step 2: Verify it compiles**

Run: `./gradlew compileJava`
Expected: Compilation errors (references to removed fields in other methods). That's expected — we'll fix them in subsequent tasks.

---

### Task 2: Rewrite `init()` — dropdown button, dynamic list, pinned Select

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java` — the `init()` method (lines 82-187)

**Step 1: Rewrite `init()` with new layout**

Replace the entire `init()` method with:
```java
@Override
protected void init() {
    super.init();

    int leftPanelWidth = this.width / 2;
    int margin = 10;

    // --- Dropdown button at top ---
    int dropdownY = 10;
    String selectedName = getSelectedTabDisplayName();
    dropdownButton = Button.builder(
        Component.literal(selectedName + " \u25BC"), // ▼
        btn -> toggleDropdown()
    ).bounds(margin, dropdownY, leftPanelWidth - margin * 2, DROPDOWN_BUTTON_HEIGHT).build();
    this.addRenderableWidget(dropdownButton);

    // --- Calculate dynamic list area ---
    int listStartY = dropdownY + DROPDOWN_BUTTON_HEIGHT + 8;
    int selectButtonY = this.height - 30;
    visibleEntries = Math.max(1, (selectButtonY - listStartY - 10) / ENTRY_HEIGHT);

    // --- Multiblock list buttons ---
    boolean needsScrollbar = filteredMultiblocks.size() > visibleEntries;
    int buttonWidth = needsScrollbar
        ? leftPanelWidth - margin * 2 - 12  // leave space for scrollbar
        : leftPanelWidth - margin * 2;

    for (int i = 0; i < Math.min(visibleEntries, filteredMultiblocks.size() - scrollOffset); i++) {
        int index = scrollOffset + i;
        if (index >= filteredMultiblocks.size()) break;

        MultiblockDefinition multiblock = filteredMultiblocks.get(index);

        Button button = Button.builder(
            multiblock.displayName(),
            btn -> selectMultiblockForPreview(multiblock)
        ).bounds(margin, listStartY + i * ENTRY_HEIGHT, buttonWidth, 18).build();

        this.addRenderableWidget(button);
    }

    // --- Select button pinned to bottom ---
    int selectButtonWidth = needsScrollbar
        ? leftPanelWidth - margin * 2 - 12
        : leftPanelWidth - margin * 2;

    this.addRenderableWidget(Button.builder(
        Component.translatable("gui.multiblockprojector.select"),
        btn -> selectMultiblock(selectedMultiblock)
    ).bounds(margin, selectButtonY, selectButtonWidth, 20).build());

    // --- Size control buttons in right panel ---
    int rightPanelCenterX = leftPanelWidth + (this.width - leftPanelWidth) / 2;
    int sizeButtonY = this.height - 45;
    int sizeButtonWidth = 30;
    int textWidth = 130;
    int totalWidth = sizeButtonWidth * 2 + textWidth + 10;

    sizeDecreaseButton = Button.builder(
        Component.literal("-"),
        btn -> decreaseSizePreset()
    ).bounds(rightPanelCenterX - totalWidth / 2, sizeButtonY, sizeButtonWidth, 20).build();
    sizeDecreaseButton.visible = selectedMultiblock != null && selectedMultiblock.isVariableSize();
    this.addRenderableWidget(sizeDecreaseButton);

    sizeIncreaseButton = Button.builder(
        Component.literal("+"),
        btn -> increaseSizePreset()
    ).bounds(rightPanelCenterX + totalWidth / 2 - sizeButtonWidth, sizeButtonY, sizeButtonWidth, 20).build();
    sizeIncreaseButton.visible = selectedMultiblock != null && selectedMultiblock.isVariableSize();
    this.addRenderableWidget(sizeIncreaseButton);

    if (selectedMultiblock != null && selectedMultiblock.isVariableSize()) {
        updateSizeButtons(selectedMultiblock);
    }
}
```

**Step 2: Add helper methods**

Add these new methods:
```java
private String getSelectedTabDisplayName() {
    var tabs = MultiblockIndex.get().getTabs();
    for (TabEntry tab : tabs) {
        if (tab.modId().equals(selectedTab)) {
            return tab.displayName();
        }
    }
    return "All";
}

private void toggleDropdown() {
    dropdownOpen = !dropdownOpen;
}
```

**Step 3: Update `selectTab` to close dropdown and update button label**

```java
private void selectTab(String tabId) {
    selectedTab = tabId;
    dropdownOpen = false;
    dropdownScrollOffset = 0;
    updateFilteredMultiblocks();
    selectedMultiblock = null;
    if (sizeDecreaseButton != null) sizeDecreaseButton.visible = false;
    if (sizeIncreaseButton != null) sizeIncreaseButton.visible = false;
    previewRenderer.setMultiblock(null);
    rebuildWidgets();
}
```

**Step 4: Verify it compiles**

Run: `./gradlew compileJava`
Expected: Still errors in `render()` and input methods referencing old constants. Fixed in next tasks.

---

### Task 3: Rewrite `render()` — dropdown overlay, remove tab indicator

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java` — the `render()` method (lines 268-367)

**Step 1: Rewrite `render()` with dropdown overlay and dynamic scrollbar**

Replace the entire `render()` method with:
```java
@Override
public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

    int leftPanelWidth = this.width / 2;
    int margin = 10;
    int dropdownY = 10;
    int listStartY = dropdownY + DROPDOWN_BUTTON_HEIGHT + 8;

    // Draw left panel background
    guiGraphics.fill(0, 0, leftPanelWidth, this.height, 0x80000000);

    // Draw right panel background (grey)
    guiGraphics.fill(leftPanelWidth, 0, this.width, this.height, 0x80404040);

    // Render all widgets (buttons)
    super.render(guiGraphics, mouseX, mouseY, partialTick);

    // Draw vertical separator
    guiGraphics.fill(leftPanelWidth, 0, leftPanelWidth + 2, this.height, 0xFF555555);

    // Draw "no multiblocks" message if tab is empty
    if (filteredMultiblocks.isEmpty() && selectedTab != null) {
        guiGraphics.drawCenteredString(this.font,
            Component.literal("No multiblocks from " + getSelectedTabDisplayName()),
            leftPanelWidth / 2, listStartY + 40, 0x888888);
    }

    // Render preview in right panel
    int previewMargin = 20;
    int previewWidth = (this.width - leftPanelWidth) - (previewMargin * 2);
    int bottomReserved = 80;
    int previewHeight = this.height - previewMargin - bottomReserved;
    int previewX = leftPanelWidth + previewMargin;
    int previewY = previewMargin;

    // Draw selected multiblock name above preview
    if (selectedMultiblock != null) {
        Component selectedName = selectedMultiblock.displayName();
        guiGraphics.drawCenteredString(this.font, selectedName,
            previewX + previewWidth / 2, previewY - 15, 0xFFFFFF);
    }

    // Draw preview background and render
    guiGraphics.fill(previewX - 2, previewY - 2, previewX + previewWidth + 2, previewY + previewHeight + 2, 0xFF333333);
    guiGraphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF111111);
    previewRenderer.render(guiGraphics, previewX, previewY, previewWidth, previewHeight, mouseX, mouseY, partialTick);

    // Draw size info for variable-size multiblocks
    if (selectedMultiblock != null && selectedMultiblock.isVariableSize()) {
        var variants = selectedMultiblock.variants();
        if (!variants.isEmpty() && currentSizePresetIndex < variants.size()) {
            var variant = variants.get(currentSizePresetIndex);
            int rightPanelCenterX = leftPanelWidth + (this.width - leftPanelWidth) / 2;
            int sizeTextY = this.height - 45 + 6;
            guiGraphics.drawCenteredString(this.font, variant.getFullDisplayName(), rightPanelCenterX, sizeTextY, 0xFFFFFF);
        }
    }

    // Draw scrollbar if needed
    if (filteredMultiblocks.size() > visibleEntries) {
        renderScrollbar(guiGraphics, leftPanelWidth, listStartY);
    }

    // Draw multiblock info on hover in left panel
    if (!dropdownOpen && mouseX >= margin && mouseX <= leftPanelWidth - margin && mouseY >= listStartY) {
        int hoveredIndex = (mouseY - listStartY) / ENTRY_HEIGHT;
        if (hoveredIndex >= 0 && hoveredIndex < visibleEntries) {
            int multiblockIndex = scrollOffset + hoveredIndex;
            if (multiblockIndex < filteredMultiblocks.size()) {
                MultiblockDefinition multiblock = filteredMultiblocks.get(multiblockIndex);
                guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.multiblockprojector.tooltip", multiblock.modId()),
                    mouseX, mouseY);
            }
        }
    }

    // --- Render dropdown overlay LAST (on top of everything in left panel) ---
    if (dropdownOpen) {
        renderDropdownOverlay(guiGraphics, mouseX, mouseY, leftPanelWidth, margin, dropdownY);
    }
}
```

**Step 2: Add the `renderDropdownOverlay` method**

```java
private void renderDropdownOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                    int leftPanelWidth, int margin, int dropdownY) {
    var tabs = MultiblockIndex.get().getTabs();
    int overlayX = margin;
    int overlayY = dropdownY + DROPDOWN_BUTTON_HEIGHT;
    int overlayWidth = leftPanelWidth - margin * 2;
    int visibleDropdownEntries = Math.min(MAX_DROPDOWN_VISIBLE, tabs.size());
    int overlayHeight = visibleDropdownEntries * DROPDOWN_ENTRY_HEIGHT + 4; // +4 for padding

    // Background with border
    guiGraphics.fill(overlayX - 1, overlayY - 1, overlayX + overlayWidth + 1, overlayY + overlayHeight + 1, 0xFF555555);
    guiGraphics.fill(overlayX, overlayY, overlayX + overlayWidth, overlayY + overlayHeight, 0xFF1a1a1a);

    // Render visible entries
    for (int i = 0; i < visibleDropdownEntries; i++) {
        int tabIdx = dropdownScrollOffset + i;
        if (tabIdx >= tabs.size()) break;

        TabEntry tab = tabs.get(tabIdx);
        int entryY = overlayY + 2 + i * DROPDOWN_ENTRY_HEIGHT;

        // Hover highlight
        boolean hovered = mouseX >= overlayX && mouseX <= overlayX + overlayWidth
            && mouseY >= entryY && mouseY < entryY + DROPDOWN_ENTRY_HEIGHT;
        boolean isSelected = tab.modId().equals(selectedTab);

        if (hovered) {
            guiGraphics.fill(overlayX, entryY, overlayX + overlayWidth, entryY + DROPDOWN_ENTRY_HEIGHT, 0xFF3a3a5a);
        } else if (isSelected) {
            guiGraphics.fill(overlayX, entryY, overlayX + overlayWidth, entryY + DROPDOWN_ENTRY_HEIGHT, 0xFF2a2a3a);
        }

        int textColor = isSelected ? 0xFFFFFF : 0xCCCCCC;
        guiGraphics.drawString(this.font, tab.displayName(), overlayX + 6, entryY + 5, textColor);
    }

    // Scroll indicators
    if (dropdownScrollOffset > 0) {
        guiGraphics.drawCenteredString(this.font, "\u25B2", // ▲
            overlayX + overlayWidth / 2, overlayY - 8, 0xAAAAAA);
    }
    if (dropdownScrollOffset + visibleDropdownEntries < tabs.size()) {
        guiGraphics.drawCenteredString(this.font, "\u25BC", // ▼
            overlayX + overlayWidth / 2, overlayY + overlayHeight, 0xAAAAAA);
    }
}
```

**Step 3: Update `renderScrollbar` and `isClickOnScrollbar` to use `visibleEntries`**

```java
private void renderScrollbar(GuiGraphics guiGraphics, int leftPanelWidth, int startY) {
    int scrollbarX = leftPanelWidth - 8;
    int scrollbarWidth = 6;
    int scrollbarHeight = visibleEntries * ENTRY_HEIGHT;

    // Draw scrollbar track
    guiGraphics.fill(scrollbarX, startY, scrollbarX + scrollbarWidth, startY + scrollbarHeight, 0xFF404040);

    // Calculate scrollbar thumb position and size
    int totalItems = filteredMultiblocks.size();
    float scrollPercentage = (float) scrollOffset / (totalItems - visibleEntries);

    int thumbHeight = Math.max(10, (scrollbarHeight * visibleEntries) / totalItems);
    int thumbY = startY + (int)((scrollbarHeight - thumbHeight) * scrollPercentage);

    // Draw scrollbar thumb
    guiGraphics.fill(scrollbarX + 1, thumbY, scrollbarX + scrollbarWidth - 1, thumbY + thumbHeight, 0xFF808080);
}

private boolean isClickOnScrollbar(double mouseX, double mouseY, int leftPanelWidth, int startY) {
    int scrollbarX = leftPanelWidth - 8;
    int scrollbarWidth = 6;
    int scrollbarHeight = visibleEntries * ENTRY_HEIGHT;

    return mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth &&
           mouseY >= startY && mouseY <= startY + scrollbarHeight;
}
```

**Step 4: Verify it compiles**

Run: `./gradlew compileJava`
Expected: Still errors in input methods. Fixed next.

---

### Task 4: Update input handlers — mouseClicked, mouseScrolled, keyPressed

**Files:**
- Modify: `src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java` — `mouseClicked()`, `mouseScrolled()`, `keyPressed()`

**Step 1: Rewrite `mouseClicked()` with dropdown handling**

```java
@Override
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    int leftPanelWidth = this.width / 2;
    int margin = 10;
    int dropdownY = 10;
    int listStartY = dropdownY + DROPDOWN_BUTTON_HEIGHT + 8;

    // Handle dropdown clicks when open
    if (dropdownOpen && button == 0) {
        var tabs = MultiblockIndex.get().getTabs();
        int overlayX = margin;
        int overlayY = dropdownY + DROPDOWN_BUTTON_HEIGHT;
        int overlayWidth = leftPanelWidth - margin * 2;
        int visibleDropdownEntries = Math.min(MAX_DROPDOWN_VISIBLE, tabs.size());
        int overlayHeight = visibleDropdownEntries * DROPDOWN_ENTRY_HEIGHT + 4;

        // Click inside dropdown overlay
        if (mouseX >= overlayX && mouseX <= overlayX + overlayWidth
            && mouseY >= overlayY && mouseY <= overlayY + overlayHeight) {
            int entryIndex = (int)((mouseY - overlayY - 2) / DROPDOWN_ENTRY_HEIGHT);
            int tabIdx = dropdownScrollOffset + entryIndex;
            if (tabIdx >= 0 && tabIdx < tabs.size()) {
                selectTab(tabs.get(tabIdx).modId());
            }
            return true;
        }

        // Click outside dropdown — close it
        dropdownOpen = false;
        // Don't return — let the click fall through to other handlers
    }

    // Let buttons handle clicks first (dropdown button, multiblock buttons, etc.)
    if (super.mouseClicked(mouseX, mouseY, button)) {
        return true;
    }

    // Handle scrollbar clicks
    if (button == 0 && filteredMultiblocks.size() > visibleEntries
        && isClickOnScrollbar(mouseX, mouseY, leftPanelWidth, listStartY)) {
        int scrollbarHeight = visibleEntries * ENTRY_HEIGHT;

        double clickPercentage = (mouseY - listStartY) / scrollbarHeight;
        int newScrollOffset = (int) (clickPercentage * (filteredMultiblocks.size() - visibleEntries));

        scrollOffset = Math.max(0, Math.min(filteredMultiblocks.size() - visibleEntries, newScrollOffset));
        rebuildWidgets();
        return true;
    }

    // Start dragging in preview area
    if (mouseX > leftPanelWidth && button == 0) {
        isDragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    return false;
}
```

**Step 2: Rewrite `mouseScrolled()` with dropdown scroll support**

```java
@Override
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    int leftPanelWidth = this.width / 2;
    int margin = 10;
    int dropdownY = 10;
    int listStartY = dropdownY + DROPDOWN_BUTTON_HEIGHT + 8;

    // Handle dropdown scrolling when open
    if (dropdownOpen) {
        var tabs = MultiblockIndex.get().getTabs();
        int overlayX = margin;
        int overlayY = dropdownY + DROPDOWN_BUTTON_HEIGHT;
        int overlayWidth = leftPanelWidth - margin * 2;
        int visibleDropdownEntries = Math.min(MAX_DROPDOWN_VISIBLE, tabs.size());
        int overlayHeight = visibleDropdownEntries * DROPDOWN_ENTRY_HEIGHT + 4;

        if (mouseX >= overlayX && mouseX <= overlayX + overlayWidth
            && mouseY >= overlayY && mouseY <= overlayY + overlayHeight) {
            if (scrollY > 0) {
                dropdownScrollOffset = Math.max(0, dropdownScrollOffset - 1);
            } else if (scrollY < 0) {
                dropdownScrollOffset = Math.min(tabs.size() - visibleDropdownEntries, dropdownScrollOffset + 1);
            }
            return true;
        }
    }

    // Handle scrolling in left panel multiblock list
    if (mouseX < leftPanelWidth && mouseY >= listStartY && filteredMultiblocks.size() > visibleEntries) {
        int oldScrollOffset = scrollOffset;
        if (scrollY > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (scrollY < 0) {
            scrollOffset = Math.min(filteredMultiblocks.size() - visibleEntries, scrollOffset + 1);
        }

        if (scrollOffset != oldScrollOffset) {
            rebuildWidgets();
            return true;
        }
    }

    return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
}
```

**Step 3: Update `keyPressed()` to close dropdown on Escape**

Add at the beginning of `keyPressed()`, before the existing ESC handling:
```java
// Close dropdown first if open
if (keyCode == 256 && dropdownOpen) {
    dropdownOpen = false;
    return true;
}
```

**Step 4: Verify it compiles**

Run: `./gradlew compileJava`
Expected: PASS — all references to removed fields/constants should now be replaced.

**Step 5: Commit**

```
git add src/main/java/com/multiblockprojector/client/gui/ProjectorScreen.java
git commit -m "refactor: replace mod tabs with dropdown, dynamic list height, fix scrollbar"
```

---

### Task 5: Test in dev client

**Step 1: Launch dev client**

Run: `./gradlew runClient`

**Step 2: Manual test checklist**

- [ ] Open projector GUI — dropdown button shows first mod name with ▼
- [ ] Click dropdown — overlay appears with all mod names, selected one highlighted
- [ ] Click a different mod — dropdown closes, list updates, dropdown button label updates
- [ ] Click outside dropdown — it closes without changing selection
- [ ] Press Escape while dropdown open — dropdown closes, GUI stays open
- [ ] Press Escape with dropdown closed — GUI closes as before
- [ ] Multiblock list fills available vertical space (more than 7 entries visible on large window)
- [ ] Resize window — list adjusts to new height
- [ ] Scrollbar appears when list has more items than visible space
- [ ] Scroll with mouse wheel — list scrolls correctly
- [ ] Click scrollbar track — jumps to position
- [ ] **Scrollbar stays visible after clicking a multiblock name to preview**
- [ ] Select button always visible at bottom regardless of list length
- [ ] 3D preview still renders and rotates with mouse drag
- [ ] Size +/- buttons work for variable-size multiblocks
- [ ] Selecting a multiblock and clicking Select works (enters projection mode)

**Step 3: Fix any issues found, re-test, amend commit if needed**
