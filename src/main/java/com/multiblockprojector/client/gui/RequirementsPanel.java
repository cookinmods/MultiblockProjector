package com.multiblockprojector.client.gui;

import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.items.BatteryFabricatorItem;
import com.multiblockprojector.common.projector.MultiblockProjection;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class RequirementsPanel {

    private final Font font;
    private List<BlockRequirement> requirements = List.of();
    private int totalFENeeded = 0;
    private int availableFE = 0;
    private boolean isBattery = false;
    private BlockPos linkedEnergyPos = null;
    private BlockPos linkedChestPos = null;

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
        // (Client-side: we can only check local player inventory, not linked chest)
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
            // Can't check linked energy source from client — show "Linked" status instead
            this.availableFE = -1; // -1 = unknown (linked source)
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

    public void clear() {
        this.requirements = List.of();
        this.totalFENeeded = 0;
        this.availableFE = 0;
    }

    /**
     * Render the requirements panel.
     * @return the total height used for rendering
     */
    public int render(GuiGraphics graphics, int x, int y, int width, int maxHeight) {
        if (requirements.isEmpty()) return 0;

        int currentY = y;
        int lineHeight = 12;

        // Title
        graphics.drawString(font, "Requirements:", x + 4, currentY, 0xFFFFFF);
        currentY += lineHeight + 2;

        // Block requirements (scrollable within available space)
        int maxBlockLines = (maxHeight - 50) / lineHeight; // Reserve space for FE and link info
        int shown = 0;
        for (BlockRequirement req : requirements) {
            if (shown >= maxBlockLines) break;

            boolean sufficient = req.have >= req.needed;
            int color = sufficient ? 0x55FF55 : 0xFF5555;
            String icon = sufficient ? "\u2713" : "\u2717"; // checkmark or x
            String text = icon + " " + req.name;
            String count = req.have + "/" + req.needed;

            graphics.drawString(font, text, x + 6, currentY, color);
            graphics.drawString(font, count, x + width - font.width(count) - 6, currentY, color);
            currentY += lineHeight;
            shown++;
        }

        if (shown < requirements.size()) {
            graphics.drawString(font, "+" + (requirements.size() - shown) + " more...", x + 6, currentY, 0x888888);
            currentY += lineHeight;
        }

        currentY += 4;

        // FE line
        String feText;
        int feColor;
        if (isBattery) {
            boolean sufficient = availableFE >= totalFENeeded;
            feColor = sufficient ? 0x55FF55 : 0xFF5555;
            feText = "FE: " + BatteryFabricatorItem.formatEnergy(availableFE) + " / " +
                     BatteryFabricatorItem.formatEnergy(totalFENeeded) + " needed";
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
        currentY += lineHeight;

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
        currentY += lineHeight;

        // Chest line
        if (linkedChestPos != null) {
            graphics.drawString(font, "Chest: (" + linkedChestPos.getX() + ", " +
                linkedChestPos.getY() + ", " + linkedChestPos.getZ() + ")",
                x + 4, currentY, 0x55FF55);
        } else {
            graphics.drawString(font, "Chest: Not linked", x + 4, currentY, 0xFF5555);
        }
        currentY += lineHeight;

        return currentY - y;
    }

    public boolean hasRequirements() {
        return !requirements.isEmpty();
    }
}
