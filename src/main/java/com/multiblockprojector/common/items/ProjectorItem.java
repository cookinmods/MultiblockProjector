package com.multiblockprojector.common.items;

import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Universal Multiblock Projector Item — base projector with projection + manual building.
 * No auto-build capability. Works identically in creative and survival.
 */
public class ProjectorItem extends AbstractProjectorItem {

    public ProjectorItem() {
        super();
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        Settings settings = getSettings(stack);
        if (settings.getMultiblock() != null) {
            tooltip.add(Component.translatable("desc.multiblockprojector.info.projector.build0"));
        } else {
            tooltip.add(Component.literal("Creates Projections of multiblock structures")
                .withStyle(ChatFormatting.AQUA));
        }

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Default Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Right-click to open multiblock menu").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));

            tooltip.add(Component.literal("Projection Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Left-click to rotate 90 degrees").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click to place projection").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));

            tooltip.add(Component.literal("Build Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Build blocks to match projection").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click with projector in hand to cancel").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void handleProjectionRightClick(Level world, Player player, InteractionHand hand, ItemStack held, Settings settings) {
        if (player.isShiftKeyDown()) {
            // Sneak + Right Click: Cancel projection
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            clearProjection(settings);
            settings.applyTo(held);
            settings.sendPacketToServer(hand);
            player.displayClientMessage(Component.literal("Projection cancelled"), true);
        }
        // Non-sneak right-click in PROJECTION: place projection (handled by ProjectorClientHandler)
    }
}
