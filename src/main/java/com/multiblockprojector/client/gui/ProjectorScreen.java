package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockDefinition.SizeVariant;
import com.multiblockprojector.common.items.ProjectorItem;
import com.multiblockprojector.common.network.MessageProjectorSync;
import com.multiblockprojector.common.projector.Settings;
import com.multiblockprojector.common.registry.MultiblockIndex;
import com.multiblockprojector.common.registry.MultiblockIndex.TabEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * GUI screen for selecting multiblock structures with mod dropdown
 */
public class ProjectorScreen extends Screen {
    private final ItemStack projectorStack;
    private final InteractionHand hand;
    private final Settings settings;

    // Filtered multiblocks for current tab
    private List<MultiblockDefinition> filteredMultiblocks;

    private String selectedTab;
    // Layout constants
    private static final int MARGIN = 10;
    private static final int DROPDOWN_Y = 10;
    private static final int DROPDOWN_BUTTON_HEIGHT = 20;
    private static final int DROPDOWN_ENTRY_HEIGHT = 18;
    private static final int MAX_DROPDOWN_VISIBLE = 8;
    private static final int ENTRY_HEIGHT = 20;

    // Dropdown state
    private boolean dropdownOpen = false;
    private int dropdownScrollOffset = 0;

    // Multiblock list layout (computed in init())
    private int leftPanelWidth;
    private int listStartY;
    private int scrollOffset = 0;
    private int visibleEntries = 7;

    private SimpleMultiblockPreviewRenderer previewRenderer;
    private MultiblockDefinition selectedMultiblock;
    private int currentSizePresetIndex = 0;
    private Button sizeDecreaseButton;
    private Button sizeIncreaseButton;
    private boolean isDragging = false;

    public ProjectorScreen(ItemStack projectorStack, InteractionHand hand) {
        super(Component.translatable("gui.multiblockprojector.projector"));
        this.projectorStack = projectorStack;
        this.hand = hand;
        this.settings = ProjectorItem.getSettings(projectorStack);
        this.previewRenderer = new SimpleMultiblockPreviewRenderer();

        var index = MultiblockIndex.get();
        var tabs = index.getTabs();
        // Default to first mod tab (skip "All"), fall back to "All"
        this.selectedTab = tabs.size() > 1 ? tabs.get(1).modId() : MultiblockIndex.ALL_TAB;
        updateFilteredMultiblocks();
    }

    private void updateFilteredMultiblocks() {
        filteredMultiblocks = MultiblockIndex.get().getForTab(selectedTab);
        scrollOffset = 0;
    }

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

    @Override
    protected void init() {
        super.init();

        leftPanelWidth = this.width / 2;

        // --- Dropdown button at top ---
        String selectedName = getSelectedTabDisplayName();
        this.addRenderableWidget(Button.builder(
            Component.literal(selectedName + " \u25BC"), // ▼
            btn -> toggleDropdown()
        ).bounds(MARGIN, DROPDOWN_Y, leftPanelWidth - MARGIN * 2, DROPDOWN_BUTTON_HEIGHT).build());

        // --- Calculate dynamic list area ---
        listStartY = DROPDOWN_Y + DROPDOWN_BUTTON_HEIGHT + 8;
        int selectButtonY = this.height - 30;
        visibleEntries = Math.max(1, (selectButtonY - listStartY - 10) / ENTRY_HEIGHT);

        // Clamp scroll offset when screen resizes
        if (filteredMultiblocks.size() > visibleEntries) {
            scrollOffset = Math.max(0, Math.min(scrollOffset, filteredMultiblocks.size() - visibleEntries));
        } else {
            scrollOffset = 0;
        }

        // --- Multiblock list buttons ---
        boolean needsScrollbar = filteredMultiblocks.size() > visibleEntries;
        int buttonWidth = needsScrollbar
            ? leftPanelWidth - MARGIN * 2 - 12  // leave space for scrollbar
            : leftPanelWidth - MARGIN * 2;

        for (int i = 0; i < Math.min(visibleEntries, filteredMultiblocks.size() - scrollOffset); i++) {
            int index = scrollOffset + i;
            if (index >= filteredMultiblocks.size()) break;

            MultiblockDefinition multiblock = filteredMultiblocks.get(index);

            Button button = Button.builder(
                multiblock.displayName(),
                btn -> selectMultiblockForPreview(multiblock)
            ).bounds(MARGIN, listStartY + i * ENTRY_HEIGHT, buttonWidth, 18).build();

            this.addRenderableWidget(button);
        }

        // --- Select button pinned to bottom ---
        int selectButtonWidth = needsScrollbar
            ? leftPanelWidth - MARGIN * 2 - 12
            : leftPanelWidth - MARGIN * 2;

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.multiblockprojector.select"),
            btn -> selectMultiblock(selectedMultiblock)
        ).bounds(MARGIN, selectButtonY, selectButtonWidth, 20).build());

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

    private void decreaseSizePreset() {
        if (selectedMultiblock != null && selectedMultiblock.isVariableSize()) {
            if (currentSizePresetIndex > 0) {
                currentSizePresetIndex--;
                updateSizeButtons(selectedMultiblock);
                updatePreviewWithSize(selectedMultiblock);
            }
        }
    }

    private void increaseSizePreset() {
        if (selectedMultiblock != null && selectedMultiblock.isVariableSize()) {
            if (currentSizePresetIndex < selectedMultiblock.variants().size() - 1) {
                currentSizePresetIndex++;
                updateSizeButtons(selectedMultiblock);
                updatePreviewWithSize(selectedMultiblock);
            }
        }
    }

    private void updateSizeButtons(MultiblockDefinition multiblock) {
        int maxIndex = multiblock.variants().size() - 1;
        sizeDecreaseButton.active = currentSizePresetIndex > 0;
        sizeIncreaseButton.active = currentSizePresetIndex < maxIndex;
    }

    private void updatePreviewWithSize(MultiblockDefinition multiblock) {
        var variant = multiblock.variants().get(currentSizePresetIndex);
        previewRenderer.setMultiblock(multiblock, variant);
    }

    private void selectMultiblockForPreview(MultiblockDefinition multiblock) {
        this.selectedMultiblock = multiblock;

        // Show/hide size buttons based on multiblock type
        if (multiblock.isVariableSize()) {
            var variants = multiblock.variants();
            this.currentSizePresetIndex = variants.size() / 2;

            sizeDecreaseButton.visible = true;
            sizeIncreaseButton.visible = true;
            updateSizeButtons(multiblock);
            updatePreviewWithSize(multiblock);
        } else {
            this.currentSizePresetIndex = 0;
            sizeDecreaseButton.visible = false;
            sizeIncreaseButton.visible = false;
            this.previewRenderer.setMultiblock(multiblock);
        }
    }

    private void selectMultiblock(MultiblockDefinition multiblock) {
        if (multiblock == null) return;

        settings.setMultiblock(multiblock);
        settings.setMode(Settings.Mode.PROJECTION);
        settings.setSizePresetIndex(currentSizePresetIndex);
        settings.applyTo(projectorStack);

        // Send packet to server
        MessageProjectorSync.sendToServer(settings, hand);

        // Close GUI
        this.minecraft.setScreen(null);

        // Show confirmation message
        if (minecraft.player != null) {
            Component sizeInfo = Component.empty();
            if (multiblock.isVariableSize()) {
                var variant = multiblock.variants().get(currentSizePresetIndex);
                sizeInfo = Component.literal(" (" + variant.getSizeString() + ")");
            }
            minecraft.player.displayClientMessage(
                Component.translatable("gui.multiblockprojector.selected", multiblock.displayName()).append(sizeInfo),
                true
            );
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

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
            renderScrollbar(guiGraphics);
        }

        // Draw multiblock info on hover in left panel
        if (!dropdownOpen && mouseX >= MARGIN && mouseX <= leftPanelWidth - MARGIN && mouseY >= listStartY) {
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
            renderDropdownOverlay(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderDropdownOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var tabs = MultiblockIndex.get().getTabs();
        int overlayX = MARGIN;
        int overlayY = DROPDOWN_Y + DROPDOWN_BUTTON_HEIGHT;
        int overlayWidth = leftPanelWidth - MARGIN * 2;
        int visibleDropdownEntries = Math.min(MAX_DROPDOWN_VISIBLE, tabs.size());
        int overlayHeight = visibleDropdownEntries * DROPDOWN_ENTRY_HEIGHT + 4;

        // Background with border
        guiGraphics.fill(overlayX - 1, overlayY - 1, overlayX + overlayWidth + 1, overlayY + overlayHeight + 1, 0xFF555555);
        guiGraphics.fill(overlayX, overlayY, overlayX + overlayWidth, overlayY + overlayHeight, 0xFF1a1a1a);

        // Render visible entries
        for (int i = 0; i < visibleDropdownEntries; i++) {
            int tabIdx = dropdownScrollOffset + i;
            if (tabIdx >= tabs.size()) break;

            TabEntry tab = tabs.get(tabIdx);
            int entryY = overlayY + 2 + i * DROPDOWN_ENTRY_HEIGHT;

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
            guiGraphics.drawCenteredString(this.font, "\u25B2",
                overlayX + overlayWidth / 2, overlayY - 8, 0xAAAAAA);
        }
        if (dropdownScrollOffset + visibleDropdownEntries < tabs.size()) {
            guiGraphics.drawCenteredString(this.font, "\u25BC",
                overlayX + overlayWidth / 2, overlayY + overlayHeight, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Close dropdown first if open
        if (keyCode == 256 && dropdownOpen) {
            dropdownOpen = false;
            return true;
        }

        if (keyCode == 256) { // ESC key
            // Check if projector is in an operating state
            Settings.Mode currentMode = settings.getMode();
            boolean hasProjection = settings.getMultiblock() != null && settings.getPos() != null;
            boolean isPlaced = settings.isPlaced();

            // If not in projection mode with a ghost projection, or build mode with projection,
            // return to nothing selected state
            if (currentMode != Settings.Mode.PROJECTION && currentMode != Settings.Mode.BUILDING) {
                // Not in operating state - reset to nothing selected
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setMultiblock(null);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(projectorStack);

                // Send packet to server
                MessageProjectorSync.sendToServer(settings, hand);
            } else if (currentMode == Settings.Mode.PROJECTION && !hasProjection) {
                // In projection mode but no ghost projection - reset to nothing selected
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setMultiblock(null);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(projectorStack);

                // Send packet to server
                MessageProjectorSync.sendToServer(settings, hand);
            } else if (currentMode == Settings.Mode.BUILDING && (!hasProjection || !isPlaced)) {
                // In building mode but no proper projection - reset to nothing selected
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setMultiblock(null);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(projectorStack);

                // Send packet to server
                MessageProjectorSync.sendToServer(settings, hand);
            }
            // If in proper operating state (projection with ghost or building with placed projection),
            // just close GUI without changing state

            // Close GUI
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Handle dropdown scrolling when open
        if (dropdownOpen) {
            var tabs = MultiblockIndex.get().getTabs();
            int overlayX = MARGIN;
            int overlayY = DROPDOWN_Y + DROPDOWN_BUTTON_HEIGHT;
            int overlayWidth = leftPanelWidth - MARGIN * 2;
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

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Handle dragging in preview area for rotation
        if (mouseX > leftPanelWidth && isDragging) {
            previewRenderer.onMouseDragged(mouseX, mouseY, deltaX, deltaY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle dropdown clicks when open
        if (dropdownOpen && button == 0) {
            var tabs = MultiblockIndex.get().getTabs();
            int overlayX = MARGIN;
            int overlayY = DROPDOWN_Y + DROPDOWN_BUTTON_HEIGHT;
            int overlayWidth = leftPanelWidth - MARGIN * 2;
            int visibleDropdownEntries = Math.min(MAX_DROPDOWN_VISIBLE, tabs.size());
            int overlayHeight = visibleDropdownEntries * DROPDOWN_ENTRY_HEIGHT + 4;

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
        }

        // Let buttons handle clicks first
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Handle scrollbar clicks
        if (button == 0 && filteredMultiblocks.size() > visibleEntries
            && isClickOnScrollbar(mouseX, mouseY)) {
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
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderScrollbar(GuiGraphics guiGraphics) {
        int scrollbarX = leftPanelWidth - 8;
        int scrollbarWidth = 6;
        int scrollbarHeight = visibleEntries * ENTRY_HEIGHT;

        guiGraphics.fill(scrollbarX, listStartY, scrollbarX + scrollbarWidth, listStartY + scrollbarHeight, 0xFF404040);

        int totalItems = filteredMultiblocks.size();
        float scrollPercentage = (float) scrollOffset / (totalItems - visibleEntries);

        int thumbHeight = Math.max(10, (scrollbarHeight * visibleEntries) / totalItems);
        int thumbY = listStartY + (int)((scrollbarHeight - thumbHeight) * scrollPercentage);

        guiGraphics.fill(scrollbarX + 1, thumbY, scrollbarX + scrollbarWidth - 1, thumbY + thumbHeight, 0xFF808080);
    }

    private boolean isClickOnScrollbar(double mouseX, double mouseY) {
        int scrollbarX = leftPanelWidth - 8;
        int scrollbarWidth = 6;
        int scrollbarHeight = visibleEntries * ENTRY_HEIGHT;

        return mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth &&
               mouseY >= listStartY && mouseY <= listStartY + scrollbarHeight;
    }
}
