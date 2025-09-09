package com.multiblockprojector.common.items;

import com.multiblockprojector.common.UPContent;
import com.multiblockprojector.common.projector.Settings;
import com.multiblockprojector.common.projector.MultiblockProjection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
            if (settings.getMultiblock() != null) {
                Component name = settings.getMultiblock().getDisplayName();
                return Component.translatable(selfKey + ".specific", name).withStyle(ChatFormatting.GOLD);
            }
        }
        return Component.translatable(selfKey).withStyle(ChatFormatting.GOLD);
    }
    
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable Level worldIn, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flagIn) {
        Settings settings = getSettings(stack);
        if (settings.getMultiblock() != null) {
            tooltip.add(Component.translatable("desc.multiblockprojector.info.projector.build0"));
            tooltip.add(Component.translatable("desc.multiblockprojector.info.projector.build1", settings.getMultiblock().getDisplayName()));
            
            // Add mod information
            tooltip.add(Component.translatable("desc.multiblockprojector.info.projector.mod", settings.getMultiblock().getModId())
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Right-click to select a multiblock structure")
                .withStyle(ChatFormatting.AQUA));
        }
        
        // Add simple usage instructions for new users
        tooltip.add(Component.literal("How to use:").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("1. Right-click to select structure").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("2. Left-click to place projection").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("3. Build blocks to match projection").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("4. Right-click to rotate while aiming").withStyle(ChatFormatting.GRAY));
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
        if (world.isClientSide && settings.getMultiblock() != null) {
            // Create the projection
            MultiblockProjection projection = new MultiblockProjection(world, settings.getMultiblock());
            
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
        if (settings.getPos() != null && settings.getMultiblock() != null) {
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