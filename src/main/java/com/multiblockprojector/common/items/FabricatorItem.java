package com.multiblockprojector.common.items;

import com.multiblockprojector.common.network.MessageLinkBlock;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Multiblock Fabricator — survival auto-builder that links to an external energy source and chest.
 * Sneak+right-click on energy block or container to link. Right-click in PROJECTION mode to fabricate.
 */
public class FabricatorItem extends AbstractProjectorItem {

    public FabricatorItem() {
        super();
    }

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Sneak + right-click on a block: link energy or container (only when not projecting)
        if (player.isShiftKeyDown()) {
            Settings settings = getSettings(context.getItemInHand());
            if (settings.getMode() == Settings.Mode.PROJECTION || settings.getMode() == Settings.Mode.BUILDING) {
                return InteractionResult.PASS; // Let handleProjectionRightClick handle cancellation
            }

            BlockPos target = context.getClickedPos();
            Level level = context.getLevel();
            InteractionHand hand = context.getHand();

            if (!level.isClientSide) return InteractionResult.SUCCESS;

            // Check for energy capability first, then container
            var energyCap = level.getCapability(Capabilities.EnergyStorage.BLOCK, target, null);
            if (energyCap != null) {
                MessageLinkBlock.sendToServer(target, hand, MessageLinkBlock.LINK_ENERGY);
                return InteractionResult.SUCCESS;
            }

            var itemCap = level.getCapability(Capabilities.ItemHandler.BLOCK, target, null);
            if (itemCap != null) {
                MessageLinkBlock.sendToServer(target, hand, MessageLinkBlock.LINK_CONTAINER);
                return InteractionResult.SUCCESS;
            }

            // Not a valid target
            player.displayClientMessage(
                Component.literal("Not a valid energy source or container")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        tooltip.add(Component.literal("Auto-builds multiblocks using FE and materials")
            .withStyle(ChatFormatting.AQUA));

        Settings settings = getSettings(stack);

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Linking:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Sneak + Right-click energy block to link power").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Sneak + Right-click container to link storage").withStyle(ChatFormatting.GRAY));

            if (settings.getLinkedEnergyPos() != null) {
                BlockPos p = settings.getLinkedEnergyPos();
                tooltip.add(Component.literal("Energy: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.literal("Energy: Not linked").withStyle(ChatFormatting.RED));
            }

            if (settings.getLinkedChestPos() != null) {
                BlockPos p = settings.getLinkedChestPos();
                tooltip.add(Component.literal("Chest: " + p.getX() + ", " + p.getY() + ", " + p.getZ())
                    .withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.literal("Chest: Not linked").withStyle(ChatFormatting.RED));
            }

            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Projection Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Left-click to rotate 90 degrees").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click to fabricate").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    protected void handleProjectionRightClick(Level world, Player player, InteractionHand hand, ItemStack held, Settings settings) {
        if (player.isShiftKeyDown()) {
            settings.setMode(Settings.Mode.NOTHING_SELECTED);
            settings.setMultiblock(null);
            settings.setPos(null);
            clearProjection(settings);
            settings.applyTo(held);
            settings.sendPacketToServer(hand);
            player.displayClientMessage(Component.literal("Projection cancelled"), true);
        }
        // Non-sneak right-click: fabrication is handled by ProjectorClientHandler dispatch
    }
}
