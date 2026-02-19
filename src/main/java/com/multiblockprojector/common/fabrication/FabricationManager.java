package com.multiblockprojector.common.fabrication;

import com.multiblockprojector.UniversalProjector;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

@EventBusSubscriber(modid = UniversalProjector.MODID)
public class FabricationManager {

    private static final Map<UUID, FabricationTask> ACTIVE_TASKS = new HashMap<>();

    public static void addTask(ServerPlayer player, FabricationTask task) {
        // Cancel any existing task for this player
        FabricationTask existing = ACTIVE_TASKS.get(player.getUUID());
        if (existing != null && !existing.isCompleted()) {
            existing.completeInstantly();
        }
        ACTIVE_TASKS.put(player.getUUID(), task);
    }

    public static boolean hasActiveTask(UUID playerId) {
        FabricationTask task = ACTIVE_TASKS.get(playerId);
        return task != null && !task.isCompleted();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE_TASKS.isEmpty()) return;

        Iterator<Map.Entry<UUID, FabricationTask>> it = ACTIVE_TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FabricationTask> entry = it.next();
            FabricationTask task = entry.getValue();

            if (task.isCompleted()) {
                it.remove();
                continue;
            }

            // Find the player on the server
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Player disconnected — complete instantly
                task.completeInstantly();
                it.remove();
                continue;
            }

            boolean done = task.tick(player);
            if (done) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FabricationTask task = ACTIVE_TASKS.remove(event.getEntity().getUUID());
        if (task != null && !task.isCompleted()) {
            task.completeInstantly();
        }
    }

    public static void clearAll() {
        ACTIVE_TASKS.clear();
    }
}
