package com.multiblockprojector.common.items;

import com.multiblockprojector.common.network.MessageLinkBlock;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
 * Battery Powered Multiblock Fabricator — survival auto-builder with internal 32M FE storage.
 * Only links to containers (no external energy linking). Chargeable via any mod's charging mechanism.
 */
public class BatteryFabricatorItem extends AbstractProjectorItem {

    public static final int MAX_ENERGY = 32_000_000;

    public BatteryFabricatorItem() {
        super();
    }

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Sneak + right-click: only link containers (no energy linking — we have internal battery)
        if (player.isShiftKeyDown()) {
            Settings settings = getSettings(context.getItemInHand());
            if (settings.getMode() == Settings.Mode.PROJECTION || settings.getMode() == Settings.Mode.BUILDING) {
                return InteractionResult.PASS; // Let handleProjectionRightClick handle cancellation
            }

            BlockPos target = context.getClickedPos();
            Level level = context.getLevel();
            InteractionHand hand = context.getHand();

            if (!level.isClientSide) return InteractionResult.SUCCESS;

            var itemCap = level.getCapability(Capabilities.ItemHandler.BLOCK, target, null);
            if (itemCap != null) {
                MessageLinkBlock.sendToServer(target, hand, MessageLinkBlock.LINK_CONTAINER);
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(
                Component.literal("Not a valid container")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        tooltip.add(Component.literal("Auto-builds multiblocks using stored FE and materials")
            .withStyle(ChatFormatting.AQUA));

        Settings settings = getSettings(stack);
        int stored = settings.getStoredEnergy();

        tooltip.add(Component.literal("FE: " + formatEnergy(stored) + " / " + formatEnergy(MAX_ENERGY))
            .withStyle(stored > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));

        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Linking:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Sneak + Right-click container to link storage").withStyle(ChatFormatting.GRAY));

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

    @Override
    public boolean isBarVisible(@Nonnull ItemStack stack) {
        Settings settings = getSettings(stack);
        return settings.getStoredEnergy() > 0;
    }

    @Override
    public int getBarWidth(@Nonnull ItemStack stack) {
        Settings settings = getSettings(stack);
        return Math.round(13.0F * settings.getStoredEnergy() / (float) MAX_ENERGY);
    }

    @Override
    public int getBarColor(@Nonnull ItemStack stack) {
        Settings settings = getSettings(stack);
        float ratio = (float) settings.getStoredEnergy() / MAX_ENERGY;
        return Mth.hsvToRgb(0.0F, 0.0F, 0.5F + ratio * 0.5F); // White-ish energy bar
    }

    public static String formatEnergy(int energy) {
        if (energy >= 1_000_000) {
            return String.format("%.1fM", energy / 1_000_000.0);
        } else if (energy >= 1_000) {
            return String.format("%.1fK", energy / 1_000.0);
        }
        return String.valueOf(energy);
    }
}
