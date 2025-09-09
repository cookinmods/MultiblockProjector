package com.multiblockprojector.client;

import com.multiblockprojector.common.projector.MultiblockProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side manager for active multiblock projections
 */
public class ProjectionManager {
    
    private static final Map<BlockPos, MultiblockProjection> ACTIVE_PROJECTIONS = new HashMap<>();
    
    /**
     * Add or update a projection at the given position
     */
    public static void setProjection(BlockPos pos, MultiblockProjection projection) {
        ACTIVE_PROJECTIONS.put(pos.immutable(), projection);
    }
    
    /**
     * Remove projection at the given position
     */
    public static void removeProjection(BlockPos pos) {
        ACTIVE_PROJECTIONS.remove(pos);
    }
    
    /**
     * Get projection at the given position
     */
    @Nullable
    public static MultiblockProjection getProjection(BlockPos pos) {
        return ACTIVE_PROJECTIONS.get(pos);
    }
    
    /**
     * Get all active projections
     */
    public static Map<BlockPos, MultiblockProjection> getAllProjections() {
        return new HashMap<>(ACTIVE_PROJECTIONS);
    }
    
    /**
     * Clear all projections (useful when changing worlds)
     */
    public static void clearAll() {
        ACTIVE_PROJECTIONS.clear();
    }
    
    /**
     * Check if there's a projection at the given position
     */
    public static boolean hasProjection(BlockPos pos) {
        return ACTIVE_PROJECTIONS.containsKey(pos);
    }
    
    /**
     * Remove projections that are too far from the player
     */
    public static void cleanupDistantProjections(Level level, BlockPos playerPos, double maxDistance) {
        ACTIVE_PROJECTIONS.entrySet().removeIf(entry -> {
            BlockPos projectionPos = entry.getKey();
            return projectionPos.distSqr(playerPos) > maxDistance * maxDistance;
        });
    }
}