package com.multiblockprojector.common.items;

import com.multiblockprojector.common.UPContent;
import com.multiblockprojector.common.projector.Settings;
import com.multiblockprojector.common.projector.MultiblockProjection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Universal Multiblock Projector Item
 * Ported from Immersive Petroleum and enhanced for universal compatibility
 */
public class ProjectorItem extends Item {
    
    public ProjectorItem() {
        super(new Item.Properties()
            .stacksTo(1)
        );
    }
    
    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        String selfKey = getDescriptionId(stack);
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            Settings settings = getSettings(stack);
            Settings.Mode mode = settings.getMode();
            
            switch (mode) {
                case NOTHING_SELECTED:
                    return Component.translatable(selfKey).withStyle(ChatFormatting.GOLD);
                case MULTIBLOCK_SELECTION:
                    return Component.translatable(selfKey + ".selecting").withStyle(ChatFormatting.GOLD);
                case PROJECTION:
                    return Component.translatable(selfKey + ".projection_mode").withStyle(ChatFormatting.AQUA);
                case BUILDING:
                    return Component.translatable(selfKey + ".building_mode").withStyle(ChatFormatting.DARK_PURPLE);
                default:
                    return Component.translatable(selfKey).withStyle(ChatFormatting.GOLD);
            }
        }
        return Component.translatable(selfKey).withStyle(ChatFormatting.GOLD);
    }
    
    public void appendHoverText(@Nonnull ItemStack stack, TooltipContext ctx, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        Settings settings = getSettings(stack);
        if (settings.getLegacyMultiblock() != null) {
            tooltip.add(Component.translatable("desc.multiblockprojector.info.projector.build0"));
        } else {
            tooltip.add(Component.literal("Creates Projections of multiblock structures")
                .withStyle(ChatFormatting.AQUA));
        }
        
        // Show instructions based on shift key
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Default Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Right-click to open multiblock menu").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));

            tooltip.add(Component.literal("Projection Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Right-click to rotate 90 degrees").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Left-click to place projection").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));

            tooltip.add(Component.literal("Build Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Build blocks to match projection").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Right-click with projector in hand to cancel").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));

            tooltip.add(Component.literal("Creative Mode:").withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.literal("Sneak + Left-click for autobuild in Projection Mode").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Hold Shift for instructions").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
    
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(Level world, Player player, @Nonnull InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Settings settings = getSettings(held);
        
        if (world.isClientSide) {
            switch (settings.getMode()) {
                case NOTHING_SELECTED:
                    // Right Click: Open multiblock selection menu directly
                    settings.setMode(Settings.Mode.MULTIBLOCK_SELECTION);
                    settings.applyTo(held);
                    settings.sendPacketToServer(hand);
                    openGUI(hand, held);
                    break;
                    
                case MULTIBLOCK_SELECTION:
                    // This is handled by GUI opening
                    break;
                    
                case PROJECTION:
                    if (player.isShiftKeyDown()) {
                        // Sneak + Right Click: Back to nothing selected mode
                        settings.setMode(Settings.Mode.NOTHING_SELECTED);
                        settings.setMultiblock(null);
                        settings.setPos(null);
                        clearProjection(settings);
                        settings.applyTo(held);
                        settings.sendPacketToServer(hand);
                        player.displayClientMessage(Component.literal("Projection cancelled"), true);
                    } else {
                        // Right Click: Rotate projection (handled by ClientHandler)
                        // Do nothing here - rotation is handled in the client tick
                    }
                    break;
                    
                case BUILDING:
                    // Right Click: Cancel building mode and return to nothing selected
                    clearProjection(settings);
                    com.multiblockprojector.client.BlockValidationManager.clearValidation(settings.getPos());
                    settings.setMode(Settings.Mode.NOTHING_SELECTED);
                    settings.setMultiblock(null);
                    settings.setPos(null);
                    settings.setPlaced(false);
                    settings.applyTo(held);
                    settings.sendPacketToServer(hand);
                    player.displayClientMessage(Component.literal("Building mode cancelled"), true);
                    break;
            }
        }
        
        return InteractionResultHolder.success(held);
    }
    
    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        // useOn is called for both left and right clicks, but we only want left click for placement
        // We'll handle placement logic in the client handler instead
        return InteractionResult.PASS;
    }
    
    public static Settings getSettings(@Nullable ItemStack stack) {
        return new Settings(stack);
    }
    
    private void openGUI(InteractionHand hand, ItemStack held) {
        // Open the multiblock selection screen on client side
        if (net.minecraft.client.Minecraft.getInstance() != null) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.multiblockprojector.client.gui.ProjectorScreen(held, hand)
            );
        }
    }
    
    private void createProjection(BlockPos pos, Settings settings, Level world) {
        if (world.isClientSide && settings.getLegacyMultiblock() != null) {
            // Create the projection with size from settings
            var size = MultiblockProjection.getSizeFromSettings(settings.getLegacyMultiblock(), settings);
            MultiblockProjection projection = new MultiblockProjection(world, settings.getLegacyMultiblock(), size);
            
            // Apply rotation and mirroring from settings
            projection.setRotation(settings.getRotation());
            projection.setFlip(settings.isMirrored());
            
            // Register with client projection manager
            com.multiblockprojector.client.ProjectionManager.setProjection(pos, projection);
        }
    }
    
    private void clearProjection(Settings settings) {
        if (settings.getPos() != null) {
            com.multiblockprojector.client.ProjectionManager.removeProjection(settings.getPos());
            com.multiblockprojector.client.BlockValidationManager.clearValidation(settings.getPos());
        }
    }
    
    private void updateProjection(Settings settings) {
        if (settings.getPos() != null && settings.getLegacyMultiblock() != null) {
            Level world = net.minecraft.client.Minecraft.getInstance().level;
            if (world != null) {
                // Remove old projection
                clearProjection(settings);
                // Create new projection with updated settings
                createProjection(settings.getPos(), settings, world);
            }
        }
    }
}