package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.MultiblockDefinition.SizeVariant;
import com.multiblockprojector.common.items.ProjectorItem;
import com.multiblockprojector.common.network.MessageProjectorSync;
import com.multiblockprojector.common.projector.Settings;
import com.multiblockprojector.client.schematic.SchematicIndex;
import com.multiblockprojector.common.registry.MultiblockIndex;
import com.multiblockprojector.common.registry.MultiblockIndex.TabEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI screen for selecting multiblock structures with a mod selector
 * sub-screen and scrollable list using AbstractSelectionList.
 */
public class ProjectorScreen extends Screen {
    /** Remembers the last selected mod tab across screen openings. */
    private static String lastSelectedTab = null;

    private final ItemStack projectorStack;
    private final InteractionHand hand;
    private final Settings settings;

    private List<MultiblockDefinition> filteredMultiblocks;
    private String selectedTab;

    // Layout constants
    private static final int MARGIN = 10;
    private static final int TAB_SELECTOR_Y = 10;
    private static final int TAB_SELECTOR_HEIGHT = 20;
    private static final int ENTRY_HEIGHT = 20;

    // Layout (computed in init())
    private int leftPanelWidth;
    private int listStartY;

    // Widgets
    private MultiblockListWidget multiblockList;
    private Button modSelectorButton;
    private Button sizeDecreaseButton;
    private Button sizeIncreaseButton;

    private SimpleMultiblockPreviewRenderer previewRenderer;
    private MultiblockDefinition selectedMultiblock;
    private boolean selectedIsSchematic = false;
    private ResourceLocation selectedSchematicId = null;
    private int currentSizePresetIndex = 0;
    private boolean isDragging = false;

    public ProjectorScreen(ItemStack projectorStack, InteractionHand hand) {
        super(Component.translatable("gui.multiblockprojector.projector"));
        this.projectorStack = projectorStack;
        this.hand = hand;
        this.settings = ProjectorItem.getSettings(projectorStack);
        this.previewRenderer = new SimpleMultiblockPreviewRenderer();

        var index = MultiblockIndex.get();
        var tabs = index.getTabs();
        SchematicIndex.rescan();
        var schematicIndex = SchematicIndex.get();
        var allTabs = new java.util.ArrayList<>(tabs);
        allTabs.addAll(schematicIndex.getTabs());
        if (lastSelectedTab != null && allTabs.stream().anyMatch(t -> t.modId().equals(lastSelectedTab))) {
            this.selectedTab = lastSelectedTab;
        } else {
            this.selectedTab = tabs.size() > 1 ? tabs.get(1).modId() : MultiblockIndex.ALL_TAB;
        }
        updateFilteredMultiblocks();
    }

    private void updateFilteredMultiblocks() {
        var schematicIndex = SchematicIndex.get();
        if (MultiblockIndex.ALL_TAB.equals(selectedTab)) {
            var merged = new java.util.ArrayList<>(MultiblockIndex.get().getForTab(selectedTab));
            merged.addAll(schematicIndex.getAll());
            merged.sort(java.util.Comparator.comparing(d -> d.displayName().getString(), String.CASE_INSENSITIVE_ORDER));
            filteredMultiblocks = merged;
        } else if (schematicIndex.hasTab(selectedTab)) {
            filteredMultiblocks = schematicIndex.getForTab(selectedTab);
        } else {
            filteredMultiblocks = MultiblockIndex.get().getForTab(selectedTab);
        }
    }

    private void selectTab(String tabId) {
        selectedTab = tabId;
        lastSelectedTab = tabId;
        updateFilteredMultiblocks();
        selectedMultiblock = null;
        previewRenderer.setMultiblock(null);
        rebuildWidgets();
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Empty — prevents default blur/dirt background
    }

    @Override
    protected void init() {
        super.init();

        leftPanelWidth = this.width / 2;

        // --- Mod selector button (opens sub-screen) ---
        modSelectorButton = Button.builder(
            Component.literal(getSelectedTabDisplayName() + " \u25BC"),
            btn -> openModSelector()
        ).bounds(MARGIN, TAB_SELECTOR_Y, leftPanelWidth - MARGIN * 2, TAB_SELECTOR_HEIGHT).build();
        this.addRenderableWidget(modSelectorButton);

        // --- Calculate dynamic list area ---
        listStartY = TAB_SELECTOR_Y + TAB_SELECTOR_HEIGHT + 4;
        int selectButtonY = this.height - 30;
        int listHeight = selectButtonY - listStartY - 6;

        // --- Multiblock list (AbstractSelectionList) ---
        multiblockList = new MultiblockListWidget(this.minecraft, leftPanelWidth, listHeight, listStartY, ENTRY_HEIGHT);
        refreshListEntries();
        this.addRenderableWidget(multiblockList);

        // --- Select button pinned to bottom ---
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.multiblockprojector.select"),
            btn -> selectMultiblock(selectedMultiblock)
        ).bounds(MARGIN, selectButtonY, leftPanelWidth - MARGIN * 2, 20).build());

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

    private void openModSelector() {
        this.minecraft.setScreen(new ModSelectorScreen(this, selectedTab, this::selectTab));
    }

    private void refreshListEntries() {
        multiblockList.refreshEntries(filteredMultiblocks, selectedMultiblock);
    }

    private String getSelectedTabDisplayName() {
        var registryTabs = MultiblockIndex.get().getTabs();
        for (var tab : registryTabs) {
            if (tab.modId().equals(selectedTab)) return tab.displayName();
        }
        var schematicTabs = SchematicIndex.get().getTabs();
        for (var tab : schematicTabs) {
            if (tab.modId().equals(selectedTab)) return tab.displayName();
        }
        return "All";
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
        var schematicIndex = SchematicIndex.get();
        this.selectedIsSchematic = schematicIndex.hasTab(multiblock.modId());
        this.selectedSchematicId = selectedIsSchematic ? schematicIndex.getSchematicId(multiblock) : null;

        if (multiblock.isVariableSize()) {
            this.currentSizePresetIndex = multiblock.variants().size() / 2;
            updatePreviewWithSize(multiblock);
        } else {
            this.currentSizePresetIndex = 0;
            this.previewRenderer.setMultiblock(multiblock);
        }

        boolean showSizeButtons = multiblock.isVariableSize();
        if (sizeDecreaseButton != null) sizeDecreaseButton.visible = showSizeButtons;
        if (sizeIncreaseButton != null) sizeIncreaseButton.visible = showSizeButtons;
        if (showSizeButtons) {
            updateSizeButtons(multiblock);
        }
    }

    private void selectMultiblock(MultiblockDefinition multiblock) {
        if (multiblock == null) return;

        if (selectedIsSchematic && selectedSchematicId != null) {
            var entry = SchematicIndex.get().getEntryById(selectedSchematicId);
            if (entry != null) {
                settings.setSchematic(entry);
            } else {
                return;
            }
        } else {
            settings.setMultiblock(multiblock);
        }
        settings.setMode(Settings.Mode.PROJECTION);
        settings.setSizePresetIndex(currentSizePresetIndex);
        settings.applyTo(projectorStack);

        MessageProjectorSync.sendToServer(settings, hand);

        this.minecraft.setScreen(null);

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
        // Draw panel backgrounds
        guiGraphics.fill(0, 0, leftPanelWidth, this.height, 0x80000000);
        guiGraphics.fill(leftPanelWidth, 0, this.width, this.height, 0x80404040);

        // Render all widgets (mod selector button, multiblock list, select button)
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
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // If the screen closes without a multiblock being selected (e.g. ESC, game quit),
        // reset mode from MULTIBLOCK_SELECTION back to NOTHING_SELECTED so the item
        // doesn't get stuck in "selecting" mode across sessions.
        if (settings.getMode() == Settings.Mode.MULTIBLOCK_SELECTION) {
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.applyTo(projectorStack);
            MessageProjectorSync.sendToServer(settings, hand);
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            Settings.Mode currentMode = settings.getMode();
            boolean hasProjection = settings.getMultiblock() != null && settings.getPos() != null;
            boolean isPlaced = settings.isPlaced();

            if (currentMode != Settings.Mode.PROJECTION && currentMode != Settings.Mode.BUILDING) {
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setMultiblock(null);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(projectorStack);
                MessageProjectorSync.sendToServer(settings, hand);
            } else if (currentMode == Settings.Mode.PROJECTION && !hasProjection) {
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setMultiblock(null);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(projectorStack);
                MessageProjectorSync.sendToServer(settings, hand);
            } else if (currentMode == Settings.Mode.BUILDING && (!hasProjection || !isPlaced)) {
                settings.setMode(Settings.Mode.NOTHING_SELECTED);
                settings.setMultiblock(null);
                settings.setPos(null);
                settings.setPlaced(false);
                settings.applyTo(projectorStack);
                MessageProjectorSync.sendToServer(settings, hand);
            }

            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (mouseX > leftPanelWidth && isDragging) {
            previewRenderer.onMouseDragged(mouseX, mouseY, deltaX, deltaY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Let widgets (mod selector button, list, select button) handle clicks
        if (super.mouseClicked(mouseX, mouseY, button)) {
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

    // ---- Inner widget: Scrollable multiblock list ----

    private class MultiblockListWidget extends AbstractSelectionList<MultiblockListWidget.Entry> {

        public MultiblockListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
        }

        public void refreshEntries(List<MultiblockDefinition> multiblocks, MultiblockDefinition selected) {
            this.clearEntries();
            Entry selectedEntry = null;
            for (MultiblockDefinition mb : multiblocks) {
                Entry entry = new Entry(mb);
                this.addEntry(entry);
                if (mb == selected) {
                    selectedEntry = entry;
                }
            }
            if (selectedEntry != null) {
                this.setSelected(selectedEntry);
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 16;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getX() + this.width - MARGIN;
        }

        @Override
        public int getRowLeft() {
            return this.getX() + MARGIN;
        }

        @Override
        protected void renderListBackground(@Nonnull GuiGraphics graphics) {
            // Empty — parent screen draws panel background
        }

        @Override
        protected void renderListSeparators(@Nonnull GuiGraphics graphics) {
            // Empty — no separator lines
        }

        @Override
        protected void enableScissor(@Nonnull GuiGraphics graphics) {
            graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
        }

        @Override
        protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
            // Default narration
        }

        private class Entry extends AbstractSelectionList.Entry<Entry> {
            final MultiblockDefinition multiblock;

            Entry(MultiblockDefinition multiblock) {
                this.multiblock = multiblock;
            }

            @Override
            public void render(@Nonnull GuiGraphics graphics, int index, int top, int left,
                              int width, int height, int mouseX, int mouseY,
                              boolean hovering, float partialTick) {
                int color = hovering ? 0xFFFFFF : 0xCCCCCC;
                graphics.drawString(font, multiblock.displayName(),
                    left + 4, top + (height - font.lineHeight) / 2, color);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    MultiblockListWidget.this.setSelected(this);
                    selectMultiblockForPreview(multiblock);
                    return true;
                }
                return false;
            }
        }
    }

    // ---- Inner screen: Mod selector sub-screen ----

    private static class ModSelectorScreen extends Screen {
        private final Screen parent;
        private final String currentTabId;
        private final Consumer<String> onSelect;

        private static final int PANEL_PADDING = 8;
        private static final int TITLE_HEIGHT = 18;

        private ModTabListWidget tabList;
        private int panelX, panelY, panelW, panelH;

        protected ModSelectorScreen(Screen parent, String currentTabId, Consumer<String> onSelect) {
            super(Component.literal("Select Mod"));
            this.parent = parent;
            this.currentTabId = currentTabId;
            this.onSelect = onSelect;
        }

        @Override
        public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Empty — prevents default blur. We draw our own dimmed background in render().
        }

        @Override
        protected void init() {
            super.init();

            // Size the list to fit content, capped to screen
            var tabs = MultiblockIndex.get().getTabs();
            var allTabs = new java.util.ArrayList<>(tabs);
            allTabs.addAll(SchematicIndex.get().getTabs());
            int listWidth = Math.min(220, this.width - 60);
            int listItemHeight = 20;
            int contentHeight = allTabs.size() * listItemHeight;
            int maxListHeight = this.height - 80;
            int listHeight = Math.min(contentHeight + 4, maxListHeight);

            // Compute panel bounds (panel wraps list + title)
            panelW = listWidth + PANEL_PADDING * 2;
            panelH = TITLE_HEIGHT + listHeight + PANEL_PADDING * 2;
            panelX = (this.width - panelW) / 2;
            panelY = (this.height - panelH) / 2;

            // Position list inside panel, below title
            int listX = panelX + PANEL_PADDING;
            int listY = panelY + PANEL_PADDING + TITLE_HEIGHT;

            tabList = new ModTabListWidget(this.minecraft, listWidth, listHeight, listY, listItemHeight);
            tabList.setX(listX);

            for (TabEntry tab : allTabs) {
                tabList.addTabEntry(tab, tab.modId().equals(currentTabId));
            }

            this.addRenderableWidget(tabList);
        }

        @Override
        public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Dim the background
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0000000);

            // Panel border + background
            guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF555555);
            guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF2D2D2D);

            // List area inset background
            int listAreaX = panelX + PANEL_PADDING;
            int listAreaY = panelY + PANEL_PADDING + TITLE_HEIGHT;
            guiGraphics.fill(listAreaX, listAreaY,
                listAreaX + tabList.getWidth(), listAreaY + tabList.getHeight(), 0xFF1A1A1A);

            // Title centered in panel header
            guiGraphics.drawCenteredString(this.font, this.title,
                panelX + panelW / 2, panelY + PANEL_PADDING + (TITLE_HEIGHT - font.lineHeight) / 2, 0xFFFFFF);

            // Render widgets (the list)
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) {
                this.minecraft.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            // Click outside the panel closes the screen
            if (button == 0) {
                this.minecraft.setScreen(parent);
                return true;
            }
            return false;
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(parent);
        }

        private void selectTabEntry(TabEntry tab) {
            onSelect.accept(tab.modId());
            this.minecraft.setScreen(parent);
        }

        private class ModTabListWidget extends AbstractSelectionList<ModTabListWidget.TabListEntry> {

            public ModTabListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
                super(mc, width, height, y, itemHeight);
            }

            public void addTabEntry(TabEntry tab, boolean selected) {
                TabListEntry entry = new TabListEntry(tab);
                this.addEntry(entry);
                if (selected) {
                    this.setSelected(entry);
                }
            }

            @Override
            public int getRowWidth() {
                return this.width - 12;
            }

            @Override
            protected int getScrollbarPosition() {
                return this.getX() + this.width - 6;
            }

            @Override
            public int getRowLeft() {
                return this.getX() + 2;
            }

            @Override
            protected void renderSelection(@Nonnull GuiGraphics graphics, int y, int width, int height, int borderColor, int fillColor) {
                // Empty — entry render() handles both hover and selected highlights
            }

            @Override
            protected void renderListBackground(@Nonnull GuiGraphics graphics) {
                // Empty — parent screen draws inset background
            }

            @Override
            protected void renderListSeparators(@Nonnull GuiGraphics graphics) {
                // Empty — no separator lines
            }

            @Override
            protected void enableScissor(@Nonnull GuiGraphics graphics) {
                graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
            }

            @Override
            protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
            }

            private class TabListEntry extends AbstractSelectionList.Entry<TabListEntry> {
                final TabEntry tab;

                TabListEntry(TabEntry tab) {
                    this.tab = tab;
                }

                @Override
                public void render(@Nonnull GuiGraphics graphics, int index, int top, int left,
                                  int width, int height, int mouseX, int mouseY,
                                  boolean hovering, float partialTick) {
                    boolean selected = ModTabListWidget.this.getSelected() == this;
                    if (hovering) {
                        graphics.fill(left, top, left + width, top + height, 0x40FFFFFF);
                    } else if (selected) {
                        graphics.fill(left, top, left + width, top + height, 0x30FFFFFF);
                    }
                    int color = selected ? 0xFFFFFF : (hovering ? 0xFFFFFF : 0xCCCCCC);
                    graphics.drawString(font, tab.displayName(),
                        left + 6, top + (height - font.lineHeight) / 2, color);
                }

                @Override
                public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    if (button == 0) {
                        selectTabEntry(tab);
                        return true;
                    }
                    return false;
                }
            }
        }
    }
}
