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
    
    public ProjectorScreen(ItemStack projectorStack, InteractionHand hand) {
        super(Component.translatable("gui.multiblockprojector.projector"));
        this.projectorStack = projectorStack;
        this.hand = hand;
        this.settings = ProjectorItem.getSettings(projectorStack);
        this.availableMultiblocks = UniversalMultiblockHandler.getMultiblocks();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int startY = (this.height - (ENTRIES_PER_PAGE * ENTRY_HEIGHT)) / 2;
        
        // Create buttons for each visible multiblock
        for (int i = 0; i < Math.min(ENTRIES_PER_PAGE, availableMultiblocks.size() - scrollOffset); i++) {
            int index = scrollOffset + i;
            if (index >= availableMultiblocks.size()) break;
            
            IUniversalMultiblock multiblock = availableMultiblocks.get(index);
            
            Button button = Button.builder(
                multiblock.getDisplayName(),
                (btn) -> selectMultiblock(multiblock)
            )
            .bounds(this.width / 2 - 100, startY + i * ENTRY_HEIGHT, 200, 18)
            .build();
            
            this.addRenderableWidget(button);
        }
        
        // Add scroll buttons if needed
        if (scrollOffset > 0) {
            this.addRenderableWidget(Button.builder(
                Component.literal("↑"),
                (btn) -> {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                    rebuildWidgets();
                }
            ).bounds(this.width / 2 + 105, startY, 20, 18).build());
        }
        
        if (scrollOffset + ENTRIES_PER_PAGE < availableMultiblocks.size()) {
            this.addRenderableWidget(Button.builder(
                Component.literal("↓"),
                (btn) -> {
                    scrollOffset = Math.min(availableMultiblocks.size() - ENTRIES_PER_PAGE, scrollOffset + 1);
                    rebuildWidgets();
                }
            ).bounds(this.width / 2 + 105, startY + (ENTRIES_PER_PAGE - 1) * ENTRY_HEIGHT, 20, 18).build());
        }
        
        // Add close button
        this.addRenderableWidget(Button.builder(
            Component.translatable("gui.done"),
            (btn) -> this.minecraft.setScreen(null)
        ).bounds(this.width / 2 - 50, startY + ENTRIES_PER_PAGE * ENTRY_HEIGHT + 10, 100, 20).build());
    }
    
    private void selectMultiblock(IUniversalMultiblock multiblock) {
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
        
        // Draw title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        
        // Draw instructions
        Component instruction = Component.translatable("gui.multiblockprojector.select_multiblock");
        guiGraphics.drawCenteredString(this.font, instruction, this.width / 2, 35, 0xAAAAAA);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        // Draw multiblock info on hover
        if (mouseX >= this.width / 2 - 100 && mouseX <= this.width / 2 + 100) {
            int startY = (this.height - (ENTRIES_PER_PAGE * ENTRY_HEIGHT)) / 2;
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
            // ESC: Return to nothing selected mode
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            settings.setPlaced(false);
            settings.applyTo(projectorStack);
            
            // Send packet to server
            MessageProjectorSync.sendToServer(settings, hand);
            
            // Close GUI
            this.minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (availableMultiblocks.size() <= ENTRIES_PER_PAGE) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        
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
        
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}