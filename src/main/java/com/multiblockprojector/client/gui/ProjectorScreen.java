package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.IUniversalMultiblock;
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
import java.util.List;

/**
 * GUI screen for selecting multiblock structures
 */
public class ProjectorScreen extends Screen {
    private final ItemStack projectorStack;
    private final InteractionHand hand;
    private final Settings settings;
    
    private List<IUniversalMultiblock> availableMultiblocks;
    private int scrollOffset = 0;
    private static final int ENTRIES_PER_PAGE = 8;
    private static final int ENTRY_HEIGHT = 20;
    
    private SimpleMultiblockPreviewRenderer previewRenderer;
    private IUniversalMultiblock selectedMultiblock;
    private boolean isDragging = false;
    private double lastMouseX, lastMouseY;
    
    public ProjectorScreen(ItemStack projectorStack, InteractionHand hand) {
        super(Component.translatable("gui.multiblockprojector.projector"));
        this.projectorStack = projectorStack;
        this.hand = hand;
        this.settings = ProjectorItem.getSettings(projectorStack);
        this.availableMultiblocks = UniversalMultiblockHandler.getMultiblocks();
        this.previewRenderer = new SimpleMultiblockPreviewRenderer();
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate layout areas - 50/50 split
        int leftPanelWidth = this.width / 2;
        int startY = 50;
        
        // Create buttons for each visible multiblock in left panel
        for (int i = 0; i < Math.min(ENTRIES_PER_PAGE, availableMultiblocks.size() - scrollOffset); i++) {
            int index = scrollOffset + i;
            if (index >= availableMultiblocks.size()) break;
            
            IUniversalMultiblock multiblock = availableMultiblocks.get(index);
            
            // Account for scrollbar space if needed
            int buttonWidth = availableMultiblocks.size() > ENTRIES_PER_PAGE ? 
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
        
        // Scrollbar will be rendered in render() method if needed
        
        // Add select button at bottom
        int buttonY = startY + ENTRIES_PER_PAGE * ENTRY_HEIGHT + 20;
        
        // Select button (account for scrollbar space)
        int selectButtonWidth = availableMultiblocks.size() > ENTRIES_PER_PAGE ? 
            leftPanelWidth - 20 - 10 : // Leave space for scrollbar
            leftPanelWidth - 20;       // No scrollbar needed
        
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.multiblockprojector.select"),
            (btn) -> selectMultiblock(selectedMultiblock)
        ).bounds(10, buttonY, selectButtonWidth, 20).build());
    }
    
    private void selectMultiblockForPreview(IUniversalMultiblock multiblock) {
        this.selectedMultiblock = multiblock;
        this.previewRenderer.setMultiblock(multiblock);
    }
    
    private void selectMultiblock(IUniversalMultiblock multiblock) {
        if (multiblock == null) return;
        
        settings.setMultiblock(multiblock);
        settings.setMode(Settings.Mode.PROJECTION);
        settings.applyTo(projectorStack);
        
        // Send packet to server
        MessageProjectorSync.sendToServer(settings, hand);
        
        // Close GUI
        this.minecraft.setScreen(null);
        
        // Show confirmation message
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                Component.translatable("gui.multiblockprojector.selected", multiblock.getDisplayName()),
                true
            );
        }
    }
    
    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        
        int leftPanelWidth = this.width / 2;
        
        // Draw left panel background
        guiGraphics.fill(0, 0, leftPanelWidth, this.height, 0x80000000);
        
        // Draw right panel background (grey)
        guiGraphics.fill(leftPanelWidth, 0, this.width, this.height, 0x80404040);
        
        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, leftPanelWidth / 2, 20, 0xFFFFFF);
        
        // Draw instructions
        Component instruction = Component.translatable("gui.multiblockprojector.select_multiblock");
        guiGraphics.drawCenteredString(this.font, instruction, leftPanelWidth / 2, 35, 0xAAAAAA);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw vertical separator
        guiGraphics.fill(leftPanelWidth, 0, leftPanelWidth + 2, this.height, 0xFF555555);
        
        // Render preview in right panel
        int previewMargin = 20;
        int previewWidth = (this.width - leftPanelWidth) - (previewMargin * 2);
        int previewHeight = this.height - (previewMargin * 2);
        
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
        
        // Draw scrollbar if needed
        if (availableMultiblocks.size() > ENTRIES_PER_PAGE) {
            renderScrollbar(guiGraphics, leftPanelWidth);
        }
        
        // Draw multiblock info on hover in left panel
        if (mouseX >= 10 && mouseX <= leftPanelWidth - 30) {
            int startY = 50;
            int hoveredIndex = (mouseY - startY) / ENTRY_HEIGHT;
            
            if (hoveredIndex >= 0 && hoveredIndex < ENTRIES_PER_PAGE) {
                int multiblockIndex = scrollOffset + hoveredIndex;
                if (multiblockIndex < availableMultiblocks.size()) {
                    IUniversalMultiblock multiblock = availableMultiblocks.get(multiblockIndex);
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
        
        // Handle scrolling in left panel
        if (mouseX < leftPanelWidth && availableMultiblocks.size() > ENTRIES_PER_PAGE) {
            int oldScrollOffset = scrollOffset;
            if (scrollY > 0) {
                // Scroll up
                scrollOffset = Math.max(0, scrollOffset - 1);
            } else if (scrollY < 0) {
                // Scroll down
                scrollOffset = Math.min(availableMultiblocks.size() - ENTRIES_PER_PAGE, scrollOffset + 1);
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
        int leftPanelWidth = this.width / 2;
        
        // Handle scrollbar clicks
        if (button == 0 && availableMultiblocks.size() > ENTRIES_PER_PAGE && isClickOnScrollbar(mouseX, mouseY, leftPanelWidth)) {
            int scrollbarStartY = 50;
            int scrollbarHeight = ENTRIES_PER_PAGE * ENTRY_HEIGHT;
            
            // Calculate click position relative to scrollbar
            double clickPercentage = (mouseY - scrollbarStartY) / scrollbarHeight;
            int newScrollOffset = (int) (clickPercentage * (availableMultiblocks.size() - ENTRIES_PER_PAGE));
            
            // Clamp to valid range
            scrollOffset = Math.max(0, Math.min(availableMultiblocks.size() - ENTRIES_PER_PAGE, newScrollOffset));
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
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    private void renderScrollbar(GuiGraphics guiGraphics, int leftPanelWidth) {
        int scrollbarX = leftPanelWidth - 8;
        int scrollbarWidth = 6;
        int scrollbarStartY = 50;
        int scrollbarHeight = ENTRIES_PER_PAGE * ENTRY_HEIGHT;
        
        // Draw scrollbar track
        guiGraphics.fill(scrollbarX, scrollbarStartY, scrollbarX + scrollbarWidth, scrollbarStartY + scrollbarHeight, 0xFF404040);
        
        // Calculate scrollbar thumb position and size
        int totalItems = availableMultiblocks.size();
        int visibleItems = ENTRIES_PER_PAGE;
        float scrollPercentage = (float) scrollOffset / (totalItems - visibleItems);
        
        int thumbHeight = Math.max(10, (scrollbarHeight * visibleItems) / totalItems);
        int thumbY = scrollbarStartY + (int)((scrollbarHeight - thumbHeight) * scrollPercentage);
        
        // Draw scrollbar thumb
        guiGraphics.fill(scrollbarX + 1, thumbY, scrollbarX + scrollbarWidth - 1, thumbY + thumbHeight, 0xFF808080);
    }
    
    private boolean isClickOnScrollbar(double mouseX, double mouseY, int leftPanelWidth) {
        int scrollbarX = leftPanelWidth - 8;
        int scrollbarWidth = 6;
        int scrollbarStartY = 50;
        int scrollbarHeight = ENTRIES_PER_PAGE * ENTRY_HEIGHT;
        
        return mouseX >= scrollbarX && mouseX <= scrollbarX + scrollbarWidth &&
               mouseY >= scrollbarStartY && mouseY <= scrollbarStartY + scrollbarHeight;
    }
}