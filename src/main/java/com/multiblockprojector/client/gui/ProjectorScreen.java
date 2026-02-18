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
import java.util.ArrayList;
import java.util.List;

/**
 * GUI screen for selecting multiblock structures with mod tabs
 */
public class ProjectorScreen extends Screen {
    private final ItemStack projectorStack;
    private final InteractionHand hand;
    private final Settings settings;

    // Filtered multiblocks for current tab
    private List<MultiblockDefinition> filteredMultiblocks;

    private String selectedTab;
    private final List<Button> tabButtons = new ArrayList<>();
    private int tabScrollOffset = 0;
    private Button tabLeftButton;
    private Button tabRightButton;
    private static final int MAX_VISIBLE_TABS = 4;

    private int scrollOffset = 0;
    private static final int ENTRIES_PER_PAGE = 7; // Reduced to make room for tabs
    private static final int ENTRY_HEIGHT = 20;
    private static final int TAB_HEIGHT = 25;

    private SimpleMultiblockPreviewRenderer previewRenderer;
    private MultiblockDefinition selectedMultiblock;
    private int currentSizePresetIndex = 0;
    private Button sizeDecreaseButton;
    private Button sizeIncreaseButton;
    private boolean isDragging = false;
    private double lastMouseX, lastMouseY;

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
        tabButtons.clear();

        // Calculate layout areas - 50/50 split
        int leftPanelWidth = this.width / 2;
        int tabY = 10;
        int startY = tabY + TAB_HEIGHT + 15; // Below tabs

        // Create tab buttons from MultiblockIndex
        var tabs = MultiblockIndex.get().getTabs();
        int visibleTabs = Math.min(MAX_VISIBLE_TABS, tabs.size());
        int tabWidth = (leftPanelWidth - 30 - (tabs.size() > MAX_VISIBLE_TABS ? 30 : 0)) / visibleTabs;

        // Add left arrow if needed
        if (tabs.size() > MAX_VISIBLE_TABS) {
            tabLeftButton = Button.builder(Component.literal("<"), btn -> {
                if (tabScrollOffset > 0) { tabScrollOffset--; rebuildWidgets(); }
            }).bounds(10, tabY, 12, TAB_HEIGHT - 2).build();
            tabLeftButton.active = tabScrollOffset > 0;
            this.addRenderableWidget(tabLeftButton);
        }

        // Create visible tab buttons
        int tabStartX = tabs.size() > MAX_VISIBLE_TABS ? 24 : 10;
        for (int i = 0; i < visibleTabs; i++) {
            int tabIdx = tabScrollOffset + i;
            if (tabIdx >= tabs.size()) break;
            TabEntry tab = tabs.get(tabIdx);

            Button tabButton = Button.builder(
                Component.literal(tab.displayName()),
                btn -> selectTab(tab.modId())
            ).bounds(tabStartX + i * tabWidth, tabY, tabWidth - 2, TAB_HEIGHT - 2).build();

            this.addRenderableWidget(tabButton);
            tabButtons.add(tabButton);
        }

        // Add right arrow if needed
        if (tabs.size() > MAX_VISIBLE_TABS) {
            tabRightButton = Button.builder(Component.literal(">"), btn -> {
                if (tabScrollOffset < tabs.size() - MAX_VISIBLE_TABS) { tabScrollOffset++; rebuildWidgets(); }
            }).bounds(tabStartX + visibleTabs * tabWidth + 2, tabY, 12, TAB_HEIGHT - 2).build();
            tabRightButton.active = tabScrollOffset < tabs.size() - MAX_VISIBLE_TABS;
            this.addRenderableWidget(tabRightButton);
        }

        // Create buttons for each visible multiblock in left panel
        for (int i = 0; i < Math.min(ENTRIES_PER_PAGE, filteredMultiblocks.size() - scrollOffset); i++) {
            int index = scrollOffset + i;
            if (index >= filteredMultiblocks.size()) break;

            MultiblockDefinition multiblock = filteredMultiblocks.get(index);

            // Account for scrollbar space if needed
            int buttonWidth = filteredMultiblocks.size() > ENTRIES_PER_PAGE ?
                leftPanelWidth - 30 - 10 : // Leave space for scrollbar
                leftPanelWidth - 30;       // No scrollbar needed

            Button button = Button.builder(
                multiblock.displayName(),
                (btn) -> selectMultiblockForPreview(multiblock)
            )
            .bounds(10, startY + i * ENTRY_HEIGHT, buttonWidth, 18)
            .build();

            this.addRenderableWidget(button);
        }

        // Add select button at bottom
        int buttonY = startY + ENTRIES_PER_PAGE * ENTRY_HEIGHT + 20;

        // Select button (account for scrollbar space)
        int selectButtonWidth = filteredMultiblocks.size() > ENTRIES_PER_PAGE ?
            leftPanelWidth - 20 - 10 : // Leave space for scrollbar
            leftPanelWidth - 20;       // No scrollbar needed

        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.multiblockprojector.select"),
            (btn) -> selectMultiblock(selectedMultiblock)
        ).bounds(10, buttonY, selectButtonWidth, 20).build());

        // Add size control buttons in right panel (initially hidden, shown when variable-size multiblock selected)
        // Layout: [ - ]  Size: Medium (9x11x9)  [ + ] all on one horizontal line
        int rightPanelCenterX = leftPanelWidth + (this.width - leftPanelWidth) / 2;
        int sizeButtonY = this.height - 45;
        int sizeButtonWidth = 30;
        // Space for text in the middle (about 120 pixels for "Size: Medium (9x11x9)")
        int textWidth = 130;
        int totalWidth = sizeButtonWidth * 2 + textWidth + 10; // buttons + text + padding

        sizeDecreaseButton = Button.builder(
            Component.literal("-"),
            (btn) -> decreaseSizePreset()
        ).bounds(rightPanelCenterX - totalWidth / 2, sizeButtonY, sizeButtonWidth, 20).build();
        sizeDecreaseButton.visible = false;
        this.addRenderableWidget(sizeDecreaseButton);

        sizeIncreaseButton = Button.builder(
            Component.literal("+"),
            (btn) -> increaseSizePreset()
        ).bounds(rightPanelCenterX + totalWidth / 2 - sizeButtonWidth, sizeButtonY, sizeButtonWidth, 20).build();
        sizeIncreaseButton.visible = false;
        this.addRenderableWidget(sizeIncreaseButton);
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

        int leftPanelWidth = this.width / 2;
        int tabY = 10;
        int startY = tabY + TAB_HEIGHT + 15;

        // Draw left panel background
        guiGraphics.fill(0, 0, leftPanelWidth, this.height, 0x80000000);

        // Draw right panel background (grey)
        guiGraphics.fill(leftPanelWidth, 0, this.width, this.height, 0x80404040);

        // Draw selected tab indicator
        var tabs = MultiblockIndex.get().getTabs();
        int visibleTabs = Math.min(MAX_VISIBLE_TABS, tabs.size());
        int tabWidth = (leftPanelWidth - 30 - (tabs.size() > MAX_VISIBLE_TABS ? 30 : 0)) / visibleTabs;
        int tabStartX = tabs.size() > MAX_VISIBLE_TABS ? 24 : 10;

        for (int i = 0; i < visibleTabs; i++) {
            int tabIdx = tabScrollOffset + i;
            if (tabIdx >= tabs.size()) break;
            TabEntry tab = tabs.get(tabIdx);
            if (tab.modId().equals(selectedTab)) {
                int tabX = tabStartX + i * tabWidth;
                // Draw highlight under selected tab
                guiGraphics.fill(tabX, tabY + TAB_HEIGHT - 4, tabX + tabWidth - 2, tabY + TAB_HEIGHT - 2, 0xFFFFFFFF);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw vertical separator
        guiGraphics.fill(leftPanelWidth, 0, leftPanelWidth + 2, this.height, 0xFF555555);

        // Draw "no multiblocks" message if tab is empty
        if (filteredMultiblocks.isEmpty() && selectedTab != null) {
            guiGraphics.drawCenteredString(this.font,
                Component.literal("No multiblocks from " + selectedTab),
                leftPanelWidth / 2, startY + 40, 0x888888);
        }

        // Render preview in right panel (leave room at bottom for size controls)
        int previewMargin = 20;
        int previewWidth = (this.width - leftPanelWidth) - (previewMargin * 2);
        int bottomReserved = 80; // Space for size label and buttons
        int previewHeight = this.height - previewMargin - bottomReserved;

        // Center the preview in the right panel
        int previewX = leftPanelWidth + previewMargin;
        int previewY = previewMargin;

        // Draw selected multiblock name above preview
        if (selectedMultiblock != null) {
            Component selectedName = selectedMultiblock.displayName();
            int textX = previewX + previewWidth / 2;
            int textY = previewY - 15;
            guiGraphics.drawCenteredString(this.font, selectedName, textX, textY, 0xFFFFFF);
        }

        // Draw preview background
        guiGraphics.fill(previewX - 2, previewY - 2, previewX + previewWidth + 2, previewY + previewHeight + 2, 0xFF333333);
        guiGraphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF111111);

        // Render the multiblock preview
        previewRenderer.render(guiGraphics, previewX, previewY, previewWidth, previewHeight, mouseX, mouseY, partialTick);

        // Draw size info for variable-size multiblocks (between the - and + buttons)
        if (selectedMultiblock != null && selectedMultiblock.isVariableSize()) {
            var variants = selectedMultiblock.variants();
            if (!variants.isEmpty() && currentSizePresetIndex < variants.size()) {
                var variant = variants.get(currentSizePresetIndex);
                int rightPanelCenterX = leftPanelWidth + (this.width - leftPanelWidth) / 2;
                int sizeTextY = this.height - 45 + 6; // Vertically centered with buttons (button height 20, font ~8)
                Component sizeText = variant.getFullDisplayName();
                guiGraphics.drawCenteredString(this.font, sizeText, rightPanelCenterX, sizeTextY, 0xFFFFFF);
            }
        }

        // Draw scrollbar if needed
        if (filteredMultiblocks.size() > ENTRIES_PER_PAGE) {
            renderScrollbar(guiGraphics, leftPanelWidth, startY);
        }

        // Draw multiblock info on hover in left panel
        if (mouseX >= 10 && mouseX <= leftPanelWidth - 30 && mouseY >= startY) {
            int hoveredIndex = (mouseY - startY) / ENTRY_HEIGHT;

            if (hoveredIndex >= 0 && hoveredIndex < ENTRIES_PER_PAGE) {
                int multiblockIndex = scrollOffset + hoveredIndex;
                if (multiblockIndex < filteredMultiblocks.size()) {
                    MultiblockDefinition multiblock = filteredMultiblocks.get(multiblockIndex);
                    guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.multiblockprojector.tooltip", multiblock.modId()),
                        mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        int leftPanelWidth = this.width / 2;
        int tabY = 10;
        int startY = tabY + TAB_HEIGHT + 15;

        // Handle scrolling in left panel (below tabs)
        if (mouseX < leftPanelWidth && mouseY >= startY && filteredMultiblocks.size() > ENTRIES_PER_PAGE) {
            int oldScrollOffset = scrollOffset;
            if (scrollY > 0) {
                // Scroll up
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                // Scroll down
                scrollOffset = Math.min(filteredMultiblocks.size() - ENTRIES_PER_PAGE, scrollOffset + 1);
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
        int leftPanelWidth = this.width / 2;

        // Handle dragging in preview area for rotation
        if (mouseX > leftPanelWidth && isDragging) {
            previewRenderer.onMouseDragged(mouseX, mouseY, deltaX, deltaY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Let buttons handle clicks first
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int leftPanelWidth = this.width / 2;
        int tabY = 10;
        int startY = tabY + TAB_HEIGHT + 15;

        // Handle scrollbar clicks
        if (button == 0 && filteredMultiblocks.size() > ENTRIES_PER_PAGE && isClickOnScrollbar(mouseX, mouseY, leftPanelWidth, startY)) {
            int scrollbarHeight = ENTRIES_PER_PAGE * ENTRY_HEIGHT;

            // Calculate click position relative to scrollbar
            double clickPercentage = (mouseY - startY) / scrollbarHeight;
            int newScrollOffset = (int) (clickPercentage * (filteredMultiblocks.size() - ENTRIES_PER_PAGE));

            // Clamp to valid range
            scrollOffset = Math.max(0, Math.min(filteredMultiblocks.size() - ENTRIES_PER_PAGE, newScrollOffset));
            rebuildWidgets();
            return true;
        }

        // Start dragging in preview area (only if not clicking on buttons)
        if (mouseX > leftPanelWidth && button == 0) {
            isDragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
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

    private void renderScrollbar(GuiGraphics guiGraphics, int leftPanelWidth, int startY) {
        int scrollbarX = leftPanelWidth - 8;
        int scrollbarWidth = 6;
        int scrollbarHeight = ENTRIES_PER_PAGE * ENTRY_HEIGHT;

        // Draw scrollbar track
        guiGraphics.fill(scrollbarX, startY, scrollbarX + scrollbarWidth, startY + scrollbarHeight, 0xFF404040);

        // Calculate scrollbar thumb position and size
        int totalItems = filteredMultiblocks.size();
        int visibleItems = ENTRIES_PER_PAGE;
        float scrollPercentage = (float) scrollOffset / (totalItems - visibleItems);

        int thumbHeight = Math.max(10, (scrollbarHeight * visibleItems) / totalItems);
        int thumbY = startY + (int)((scrollbarHeight - thumbHeight) * scrollPercentage);

        // Draw scrollbar thumb
        guiGraphics.fill(scrollbarX + 1, thumbY, scrollbarX + scrollbarWidth - 1, thumbY + thumbHeight, 0xFF808080);
    }

    private boolean isClickOnScrollbar(double mouseX, double mouseY, int leftPanelWidth, int startY) {
        int scrollbarX = leftPanelWidth - 8;
        int scrollbarWidth = 6;
        int scrollbarHeight = ENTRIES_PER_PAGE * ENTRY_HEIGHT;

        return mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth &&
               mouseY >= startY && mouseY <= startY + scrollbarHeight;
    }
}
