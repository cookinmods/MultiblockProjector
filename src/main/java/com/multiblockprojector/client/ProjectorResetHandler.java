package com.multiblockprojector.client;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.client.ProjectionManager;
import com.multiblockprojector.client.BlockValidationManager;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.InteractionHand;

/**
 * Handles resetting projector NBT when projectors leave inventory or players disconnect/reconnect
 */
@EventBusSubscriber(modid = UniversalProjector.MODID, value = Dist.CLIENT)
public class ProjectorResetHandler {
    
    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Clear all projections when player logs out (they'll be gone anyway)
        ProjectionManager.clearAll();
        BlockValidationManager.clearAll();
        
        // Reset all projectors in inventory for session reset
        Player player = event.getPlayer();
        if (player != null) {
            resetAllProjectorsInInventory(player);
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Clear all projections when player logs in (session reset)
        ProjectionManager.clearAll();
        BlockValidationManager.clearAll();
        
        // Reset all projectors in inventory for session reset
        Player player = event.getPlayer();
        if (player != null) {
            resetAllProjectorsInInventory(player);
        }
    }
    
    @SubscribeEvent
    public static void onPlayerRespawn(ClientPlayerNetworkEvent.Clone event) {
        // Clear all projections when player respawns/changes dimensions
        ProjectionManager.clearAll();
        BlockValidationManager.clearAll();
        
        // Reset all projectors in inventory for session reset
        Player player = event.getPlayer();
        if (player != null) {
            resetAllProjectorsInInventory(player);
        }
    }
    
    @SubscribeEvent
    public static void onItemDropped(EntityJoinLevelEvent event) {
        // Check if a projector item was dropped
        if (event.getEntity() instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.getItem() instanceof AbstractProjectorItem) {
                Settings settings = AbstractProjectorItem.getSettings(stack);
                
                // If this projector had any projections, clear them since it's returning to no selection mode
                if (settings.getPos() != null) {
                    // Clear the building projection if it exists
                    ProjectionManager.removeProjection(settings.getPos());
                    BlockValidationManager.clearValidation(settings.getPos());
                }
                
                // Also clear any aim projections if this was the active projector
                // (This will be handled by the normal cleanup when the player's held item changes)
                
                // Reset the projector's NBT when dropped (return to no selection mode)
                resetProjectorNBT(stack);
                
                UniversalProjector.LOGGER.info("Projector dropped - returned to no selection mode and cleared projections");
            }
        }
    }
    
    /**
     * Reset all projectors in player's entire inventory (for session reset)
     */
    private static void resetAllProjectorsInInventory(Player player) {
        // Check main inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractProjectorItem) {
                resetProjectorNBT(stack);
            }
        }
    }
    
    /**
     * Reset projector ItemStack NBT to nothing selected mode
     */
    private static void resetProjectorNBT(ItemStack stack) {
        if (!(stack.getItem() instanceof AbstractProjectorItem)) return;

        Settings settings = AbstractProjectorItem.getSettings(stack);
        settings.setMode(Settings.Mode.NOTHING_SELECTED);
        settings.setMultiblock(null);
        settings.setPos(null);
        settings.setPlaced(false);
        settings.applyTo(stack);

        UniversalProjector.LOGGER.info("Reset projector NBT to nothing selected mode");
    }
}