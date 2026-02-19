package com.multiblockprojector.common;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server-side handler that resets all projector items to NOTHING_SELECTED
 * when a player logs out or logs in, so items don't get stuck in transient
 * modes like MULTIBLOCK_SELECTION.
 */
@EventBusSubscriber(modid = UniversalProjector.MODID)
public class ServerProjectorResetHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        resetAllProjectorsInInventory(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        resetAllProjectorsInInventory(event.getEntity());
    }

    private static void resetAllProjectorsInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractProjectorItem) {
                Settings settings = AbstractProjectorItem.getSettings(stack);
                if (settings.getMode() != Settings.Mode.NOTHING_SELECTED) {
                    settings.setMode(Settings.Mode.NOTHING_SELECTED);
                    settings.setPos(null);
                    settings.setPlaced(false);
                    settings.applyTo(stack);
                }
            }
        }
    }
}
