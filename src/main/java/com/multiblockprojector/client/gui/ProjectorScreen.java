package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.IVariableSizeMultiblock;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import com.multiblockprojector.common.items.ProjectorItem;
import com.multiblockprojector.common.network.MessageProjectorSync;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI screen for selecting multiblock structures with mod tabs
 */
public class ProjectorScreen extends Screen {
    private final ItemStack projectorStack;
    private final InteractionHand hand;
    private final Settings settings;

    // All multiblocks from handler
    private final List<IUniversalMultiblock> allMultiblocks;
    // Filtered multiblocks for current tab
    private List<IUniversalMultiblock> filteredMultiblocks;

    // Mod tabs - ordered map of modId -> display name
    private static final Map<String, String> MOD_TABS = new LinkedHashMap<>();
    static {
        MOD_TABS.put("immersiveengineering", "Immersive Engineering");
        MOD_TABS.put("mekanism", "Mekanism");
    }

    // Track which mods have multiblocks
    private final Map<String, Boolean> modHasMultiblocks = new LinkedHashMap<>();
    private String selectedModTab = null;
    private final List<Button> tabButtons = new ArrayList<>();

    private int scrollOffset = 0;
    private static final int ENTRIES_PER_PAGE = 7; // Reduced to make room for tabs
    private static final int ENTRY_HEIGHT = 20;
    private static final int TAB_HEIGHT = 25;

    private SimpleMultiblockPreviewRenderer previewRenderer;
    private IUniversalMultiblock selectedMultiblock;
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
        this.allMultiblocks = UniversalMultiblockHandler.getMultiblocks();
        this.previewRenderer = new SimpleMultiblockPreviewRenderer();

        // Determine which mods have multiblocks
        for (String modId : MOD_TABS.keySet()) {
            boolean hasMultiblocks = allMultiblocks.stream()
                .anyMatch(mb -> mb.getModId().equals(modId));
            modHasMultiblocks.put(modId, hasMultiblocks);

            // Select first available mod tab
            if (selectedModTab == null && hasMultiblocks) {
                selectedModTab = modId;
            }
        }

        // Filter multiblocks for selected tab
        updateFilteredMultiblocks();
    }

    private void updateFilteredMultiblocks() {
        if (selectedModTab == null) {
            filteredMultiblocks = new ArrayList<>(allMultiblocks);
        } else {
            filteredMultiblocks = allMultiblocks.stream()
                .filter(mb -> mb.getModId().equals(selectedModTab))
                .toList();
        }
        scrollOffset = 0;
    }

    private void selectTab(String modId) {
        if (modHasMultiblocks.getOrDefault(modId, false)) {
            selectedModTab = modId;
            updateFilteredMultiblocks();
            // Clear selection when switching tabs
            selectedMultiblock = null;
            sizeDecreaseButton.visible = false;
            sizeIncreaseButton.visible = false;
            previewRenderer.setMultiblock(null);
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        super.init();
        tabButtons.clear();

        // Calculate layout areas - 50/50 split
        int leftPanelWidth = this.width / 2;
        int tabY = 10;
        int tabWidth = (leftPanelWidth - 30) / MOD_TABS.size();
        int startY = tabY + TAB_HEIGHT + 15; // Below tabs

        // Create tab buttons
        int tabIndex = 0;
        for (Map.Entry<String, String> entry : MOD_TABS.entrySet()) {
            String modId = entry.getKey();
            String displayName = entry.getValue();
            boolean hasMultiblocks = modHasMultiblocks.getOrDefault(modId, false);
            boolean isSelected = modId.equals(selectedModTab);

            Button tabButton = Button.builder(
                Component.literal(displayName),
                (btn) -> selectTab(modId)
            )
            .bounds(10 + tabIndex * tabWidth, tabY, tabWidth - 2, TAB_HEIGHT - 2)
            .build();

            // Disable tab if mod has no multiblocks
            tabButton.active = hasMultiblocks;

            this.addRenderableWidget(tabButton);
            tabButtons.add(tabButton);
            tabIndex++;
        }

        // Create buttons for each visible multiblock in left panel
        for (int i = 0; i < Math.min(ENTRIES_PER_PAGE, filteredMultiblocks.size() - scrollOffset); i++) {
            int index = scrollOffset + i;
            if (index >= filteredMultiblocks.size()) break;

            IUniversalMultiblock multiblock = filteredMultiblocks.get(index);

            // Account for scrollbar space if needed
            int buttonWidth = filteredMultiblocks.size() > ENTRIES_PER_PAGE ?
                leftPanelWidth - 30 - 10 : // Leave space for scrollbar
                leftPanelWidth - 30;       // No scrollbar needed

            Button button = Button.builder(
                multiblock.getDisplayName(),
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
        if (selectedMultiblock instanceof IVariableSizeMultiblock varMultiblock) {
            if (currentSizePresetIndex > 0) {
                currentSizePresetIndex--;
                updateSizeButtons(varMultiblock);
                updatePreviewWithSize(varMultiblock);
            }
        }
    }

    private void increaseSizePreset() {
        if (selectedMultiblock instanceof IVariableSizeMultiblock varMultiblock) {
            if (currentSizePresetIndex < varMultiblock.getSizePresets().size() - 1) {
                currentSizePresetIndex++;
                updateSizeButtons(varMultiblock);
                updatePreviewWithSize(varMultiblock);
            }
        }
    }

    private void updateSizeButtons(IVariableSizeMultiblock varMultiblock) {
        int maxIndex = varMultiblock.getSizePresets().size() - 1;
        sizeDecreaseButton.active = currentSizePresetIndex > 0;
        sizeIncreaseButton.active = currentSizePresetIndex < maxIndex;
    }

    private void updatePreviewWithSize(IVariableSizeMultiblock varMultiblock) {
        var preset = varMultiblock.getSizePresets().get(currentSizePresetIndex);
        previewRenderer.setMultiblock(varMultiblock, preset.size());
    }

    private void selectMultiblockForPreview(IUniversalMultiblock multiblock) {
        this.selectedMultiblock = multiblock;

        // Show/hide size buttons based on multiblock type
        if (multiblock instanceof IVariableSizeMultiblock varMultiblock) {
            // Default to middle size (e.g., index 2 for 5 sizes)
            var presets = varMultiblock.getSizePresets();
            this.currentSizePresetIndex = presets.size() / 2;

            sizeDecreaseButton.visible = true;
            sizeIncreaseButton.visible = true;
            updateSizeButtons(varMultiblock);
            updatePreviewWithSize(varMultiblock);
        } else {
            this.currentSizePresetIndex = 0;
            sizeDecreaseButton.visible = false;
            sizeIncreaseButton.visible = false;
            this.previewRenderer.setMultiblock(multiblock);
        }
    }

    private void selectMultiblock(IUniversalMultiblock multiblock) {
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
            if (multiblock instanceof IVariableSizeMultiblock varMultiblock) {
                var preset = varMultiblock.getSizePresets().get(currentSizePresetIndex);
                sizeInfo = Component.literal(" (" + preset.getSizeString() + ")");
            }
            minecraft.player.displayClientMessage(
                Component.translatable("gui.multiblockprojector.selected", multiblock.getDisplayName()).append(sizeInfo),
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
        int tabWidth = (leftPanelWidth - 30) / MOD_TABS.size();
        int tabIndex = 0;
        for (String modId : MOD_TABS.keySet()) {
            if (modId.equals(selectedModTab)) {
                int tabX = 10 + tabIndex * tabWidth;
                // Draw highlight under selected tab
                guiGraphics.fill(tabX, tabY + TAB_HEIGHT - 4, tabX + tabWidth - 2, tabY + TAB_HEIGHT - 2, 0xFFFFFFFF);
            }
            tabIndex++;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Draw vertical separator
        guiGraphics.fill(leftPanelWidth, 0, leftPanelWidth + 2, this.height, 0xFF555555);

        // Draw "no multiblocks" message if tab is empty
        if (filteredMultiblocks.isEmpty() && selectedModTab != null) {
            String modName = MOD_TABS.getOrDefault(selectedModTab, selectedModTab);
            guiGraphics.drawCenteredString(this.font,
                Component.literal("No multiblocks from " + modName),
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
            Component selectedName = selectedMultiblock.getDisplayName();
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
        if (selectedMultiblock instanceof IVariableSizeMultiblock varMultiblock) {
            var presets = varMultiblock.getSizePresets();
            if (!presets.isEmpty() && currentSizePresetIndex < presets.size()) {
                var preset = presets.get(currentSizePresetIndex);
                int rightPanelCenterX = leftPanelWidth + (this.width - leftPanelWidth) / 2;
                int sizeTextY = this.height - 45 + 6; // Vertically centered with buttons (button height 20, font ~8)
                Component sizeText = preset.getFullDisplayName();
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
                    IUniversalMultiblock multiblock = filteredMultiblocks.get(multiblockIndex);
                    guiGraphics.renderTooltip(this.font,
                        Component.translatable("gui.multiblockprojector.tooltip", multiblock.getModId()),
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
