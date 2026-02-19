package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.items.BatteryFabricatorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import java.util.*;

public class RequirementsPanel {

    private static final int LINE_HEIGHT = 12;
    private static final int STATUS_LINES_HEIGHT = LINE_HEIGHT * 3 + 6; // FE + energy + chest + padding

    private final Font font;
    private List<BlockRequirement> requirements = List.of();
    private int totalFENeeded = 0;
    private int availableFE = 0;
    private boolean isBattery = false;
    private BlockPos linkedEnergyPos = null;
    private BlockPos linkedChestPos = null;

    private RequirementListWidget listWidget;

    public record BlockRequirement(Block block, String name, int needed, int have) {}

    public RequirementsPanel(Font font) {
        this.font = font;
    }

    public void update(MultiblockDefinition multiblock, int sizePresetIndex, ItemStack fabricatorStack) {
        Settings settings = AbstractProjectorItem.getSettings(fabricatorStack);
        isBattery = fabricatorStack.getItem() instanceof BatteryFabricatorItem;
        linkedEnergyPos = settings.getLinkedEnergyPos();
        linkedChestPos = settings.getLinkedChestPos();

        // Get selected variant
        var variant = multiblock.variants().get(Math.min(sizePresetIndex, multiblock.variants().size() - 1));
        var level = Minecraft.getInstance().level;
        var structure = multiblock.structureProvider().create(variant, level);

        // Count required blocks
        Map<Block, Integer> required = new LinkedHashMap<>();
        int totalNonAir = 0;
        List<Float> hardnesses = new ArrayList<>();

        for (var entry : structure.blocks().entrySet()) {
            BlockState display = entry.getValue().displayState(0);
            if (!display.isAir()) {
                required.merge(display.getBlock(), 1, Integer::sum);
                totalNonAir++;
                hardnesses.add(Math.max(display.getDestroySpeed(null, BlockPos.ZERO), 0.1f));
            }
        }

        // Calculate FE cost
        double totalFE = 0;
        for (float hardness : hardnesses) {
            totalFE += 800.0 * hardness * (1.0 + 0.0008 * totalNonAir);
        }
        this.totalFENeeded = (int) Math.ceil(totalFE);

        // Count available blocks from player inventory
        Map<Block, Integer> available = new HashMap<>();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack invStack = inv.getItem(i);
                if (!invStack.isEmpty() && invStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                    available.merge(blockItem.getBlock(), invStack.getCount(), Integer::sum);
                }
            }
        }

        // Get available FE
        if (isBattery) {
            this.availableFE = settings.getStoredEnergy();
        } else {
            this.availableFE = -1;
        }

        // Build requirements list
        List<BlockRequirement> reqs = new ArrayList<>();
        for (var entry : required.entrySet()) {
            String name = entry.getKey().getName().getString();
            int have = available.getOrDefault(entry.getKey(), 0);
            reqs.add(new BlockRequirement(entry.getKey(), name, entry.getValue(), have));
        }
        this.requirements = reqs;
    }

    public void updateBasicInfo(ItemStack fabricatorStack) {
        Settings settings = AbstractProjectorItem.getSettings(fabricatorStack);
        isBattery = fabricatorStack.getItem() instanceof BatteryFabricatorItem;
        linkedEnergyPos = settings.getLinkedEnergyPos();
        linkedChestPos = settings.getLinkedChestPos();
        if (isBattery) {
            this.availableFE = settings.getStoredEnergy();
        } else {
            this.availableFE = -1;
        }
    }

    public void clear() {
        this.requirements = List.of();
        this.totalFENeeded = 0;
        if (listWidget != null) {
            listWidget.refreshRequirements(requirements);
        }
    }

    /**
     * Creates or recreates the scrollable list widget for the given bounds.
     * Must be called from init() and the returned widget added via addRenderableWidget().
     */
    public RequirementListWidget createListWidget(int x, int width, int listY, int listHeight) {
        listWidget = new RequirementListWidget(Minecraft.getInstance(), width, listHeight, listY, LINE_HEIGHT);
        listWidget.setX(x);
        refreshListEntries();
        return listWidget;
    }

    private void refreshListEntries() {
        if (listWidget == null) return;
        listWidget.refreshRequirements(requirements);
    }

    /**
     * Call after update() to refresh the list widget entries.
     */
    public void refreshEntries() {
        refreshListEntries();
    }

    /**
     * Render the fixed status lines (FE, energy, chest) below the scrollable list.
     */
    public void renderStatusLines(GuiGraphics graphics, int x, int y, int width) {
        int currentY = y;

        // FE line
        String feText;
        int feColor;
        if (totalFENeeded == 0 && requirements.isEmpty()) {
            feText = "FE: —";
            feColor = 0x888888;
        } else if (isBattery) {
            feColor = availableFE >= totalFENeeded ? 0x55FF55 : 0xFF5555;
            feText = "FE: " + BatteryFabricatorItem.formatEnergy(totalFENeeded) + " needed";
        } else if (availableFE == -1) {
            feText = "FE: " + BatteryFabricatorItem.formatEnergy(totalFENeeded) + " needed";
            feColor = linkedEnergyPos != null ? 0xFFFF55 : 0xFF5555;
        } else {
            boolean sufficient = availableFE >= totalFENeeded;
            feColor = sufficient ? 0x55FF55 : 0xFF5555;
            feText = "FE: " + BatteryFabricatorItem.formatEnergy(availableFE) + " / " +
                     BatteryFabricatorItem.formatEnergy(totalFENeeded) + " needed";
        }
        graphics.drawString(font, feText, x + 4, currentY, feColor);
        currentY += LINE_HEIGHT;

        // Energy source line
        if (isBattery) {
            graphics.drawString(font, "Internal: " + BatteryFabricatorItem.formatEnergy(availableFE) + " FE",
                x + 4, currentY, 0xAAAAFF);
        } else if (linkedEnergyPos != null) {
            graphics.drawString(font, "Energy: (" + linkedEnergyPos.getX() + ", " +
                linkedEnergyPos.getY() + ", " + linkedEnergyPos.getZ() + ")",
                x + 4, currentY, 0x55FF55);
        } else {
            graphics.drawString(font, "Energy: Not linked", x + 4, currentY, 0xFF5555);
        }
        currentY += LINE_HEIGHT;

        // Chest line
        if (linkedChestPos != null) {
            graphics.drawString(font, "Chest: (" + linkedChestPos.getX() + ", " +
                linkedChestPos.getY() + ", " + linkedChestPos.getZ() + ")",
                x + 4, currentY, 0x55FF55);
        } else {
            graphics.drawString(font, "Chest: Not linked", x + 4, currentY, 0xFF5555);
        }
    }

    public boolean hasRequirements() {
        return !requirements.isEmpty();
    }

    public List<BlockRequirement> getRequirements() {
        return requirements;
    }

    public static int getStatusLinesHeight() {
        return STATUS_LINES_HEIGHT;
    }

    // ---- Inner widget: Scrollable block requirements list ----

    public class RequirementListWidget extends AbstractSelectionList<RequirementListWidget.Entry> {

        private final int panelWidth;

        public RequirementListWidget(Minecraft mc, int width, int height, int y, int itemHeight) {
            super(mc, width, height, y, itemHeight);
            this.panelWidth = width;
        }

        public void refreshRequirements(List<BlockRequirement> reqs) {
            this.clearEntries();
            for (BlockRequirement req : reqs) {
                this.addEntry(new Entry(req));
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
        protected void renderListBackground(@Nonnull GuiGraphics graphics) {
            // Empty — parent draws background
        }

        @Override
        protected void renderListSeparators(@Nonnull GuiGraphics graphics) {
            // Empty
        }

        @Override
        protected void renderSelection(@Nonnull GuiGraphics graphics, int y, int width, int height, int borderColor, int fillColor) {
            // Empty — no selection highlight for requirements
        }

        @Override
        protected void enableScissor(@Nonnull GuiGraphics graphics) {
            graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
        }

        @Override
        protected void updateWidgetNarration(@Nonnull NarrationElementOutput output) {
        }

        public class Entry extends AbstractSelectionList.Entry<Entry> {
            final BlockRequirement req;

            Entry(BlockRequirement req) {
                this.req = req;
            }

            @Override
            public void render(@Nonnull GuiGraphics graphics, int index, int top, int left,
                              int width, int height, int mouseX, int mouseY,
                              boolean hovering, float partialTick) {
                boolean sufficient = req.have >= req.needed;
                int color = sufficient ? 0x55FF55 : 0xFF5555;
                String icon = sufficient ? "\u2713" : "\u2717";
                String text = icon + " " + req.name;
                String count = req.have + "/" + req.needed;

                graphics.drawString(font, text, left + 4, top + 1, color);
                graphics.drawString(font, count, left + width - font.width(count) - 4, top + 1, color);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                return false; // No selection behavior
            }
        }
    }
}
