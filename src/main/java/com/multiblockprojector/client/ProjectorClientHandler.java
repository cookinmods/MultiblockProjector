package com.multiblockprojector.client;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.client.BlockValidationManager;
import com.multiblockprojector.common.items.AbstractProjectorItem;
import com.multiblockprojector.common.network.MessageAutoBuild;
import com.multiblockprojector.common.projector.MultiblockProjection;
import com.multiblockprojector.common.projector.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;

/**
 * Handles client-side projector behavior like projection following aim and ESC handling
 */
@EventBusSubscriber(modid = UniversalProjector.MODID, value = Dist.CLIENT)
public class ProjectorClientHandler {

    private static BlockPos lastAimPos = null;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Player player = mc.player;
        ItemStack held = player.getMainHandItem();

        // Always check for completed multiblocks regardless of held item
        checkAllBuildingProjectionsForCompletion(player, mc.level);

        if (!(held.getItem() instanceof AbstractProjectorItem)) {
            // Clear aim projection if not holding projector, but keep building projections
            if (lastAimPos != null) {
                ProjectionManager.removeProjection(lastAimPos);
                lastAimPos = null;
            }
            return;
        }

        Settings settings = AbstractProjectorItem.getSettings(held);

        // Clean up only aim projections that don't match current state
        cleanupAimProjections(settings);

        if (settings.getMode() == Settings.Mode.PROJECTION && settings.getMultiblock() != null) {
            // Update projection position based on player aim
            updateProjectionAim(player, settings, mc.level);
        } else if (settings.getMode() == Settings.Mode.BUILDING && settings.getPos() != null && settings.getMultiblock() != null) {
            // Validate blocks during building mode
            MultiblockProjection projection = ProjectionManager.getProjection(settings.getPos());
            if (projection != null) {
                BlockValidationManager.validateProjection(settings.getPos(), projection, mc.level);
            }
        } else {
            // Clear projection if not in projection mode
            if (lastAimPos != null) {
                ProjectionManager.removeProjection(lastAimPos);
                lastAimPos = null;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Player player = mc.player;
        ItemStack held = player.getMainHandItem();

        if (!(held.getItem() instanceof AbstractProjectorItem)) return;

        Settings settings = AbstractProjectorItem.getSettings(held);

        if (settings.getMode() == Settings.Mode.PROJECTION && settings.getMultiblock() != null) {
            if (event.getAction() == 1) { // Mouse button press
                if (event.getButton() == 0) { // Left click → rotate
                    if (lastAimPos != null) {
                        settings.rotateCW();
                        settings.applyTo(held);
                        settings.sendPacketToServer(InteractionHand.MAIN_HAND);

                        updateProjectionAtPos(lastAimPos, settings, player.level());
                        event.setCanceled(true);
                    }
                } else if (event.getButton() == 1 && !player.isShiftKeyDown()) { // Right click → item action
                    if (lastAimPos != null) {
                        handleProjectionAction(player, held, settings, lastAimPos);
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    /**
     * Dispatch the projection action based on item type.
     * Base ProjectorItem places projection; subclasses will auto-build or fabricate.
     */
    private static void handleProjectionAction(Player player, ItemStack held, Settings settings, BlockPos pos) {
        // Base behavior — place projection and enter building mode
        placeProjection(player, settings, held, pos);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Check for ESC key (key code 256)
        if (event.getKey() == 256 && event.getAction() == 1) { // 1 = key press
            Player player = mc.player;
            ItemStack held = player.getMainHandItem();

            if (held.getItem() instanceof AbstractProjectorItem) {
                Settings settings = AbstractProjectorItem.getSettings(held);

                if (settings.getMode() == Settings.Mode.PROJECTION ||
                    settings.getMode() == Settings.Mode.BUILDING) {

                    // ESC: Return to nothing selected mode
                    settings.setMode(Settings.Mode.NOTHING_SELECTED);
                    settings.setPos(null);
                    settings.setPlaced(false);

                    // Clear any active projection
                    if (lastAimPos != null) {
                        ProjectionManager.removeProjection(lastAimPos);
                        lastAimPos = null;
                    }

                    // Clear building mode projection and validation data
                    if (settings.getPos() != null) {
                        ProjectionManager.removeProjection(settings.getPos());
                        BlockValidationManager.clearValidation(settings.getPos());
                    }

                    settings.applyTo(held);
                    settings.sendPacketToServer(InteractionHand.MAIN_HAND);
                }
            }
        }
    }

    private static void updateProjectionAim(Player player, Settings settings, Level level) {
        // Ray trace to find target block
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(64.0)); // 64 block range for easier placement

        BlockHitResult result = level.clip(new ClipContext(
            eyePos, endPos,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player
        ));

        if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos targetPos = result.getBlockPos().above();

            // Only update if position changed
            if (!targetPos.equals(lastAimPos)) {
                // Remove old projection
                if (lastAimPos != null) {
                    ProjectionManager.removeProjection(lastAimPos);
                }

                // Create new projection at target position
                var variant = MultiblockProjection.getVariantFromSettings(settings.getMultiblock(), settings);
                MultiblockProjection projection = new MultiblockProjection(level, settings.getMultiblock(), variant);
                projection.setRotation(settings.getRotation());
                projection.setFlip(settings.isMirrored());

                ProjectionManager.setProjection(targetPos, projection);
                lastAimPos = targetPos;
            }
        } else {
            // No block in range - clear projection
            if (lastAimPos != null) {
                ProjectionManager.removeProjection(lastAimPos);
                lastAimPos = null;
            }
        }
    }

    private static void placeProjection(Player player, Settings settings, ItemStack held, BlockPos pos) {
        // Swing the projector for visual feedback FIRST
        player.swing(InteractionHand.MAIN_HAND, true);

        // Create the projection at the specified position
        var variant = MultiblockProjection.getVariantFromSettings(settings.getMultiblock(), settings);
        MultiblockProjection projection = new MultiblockProjection(player.level(), settings.getMultiblock(), variant);
        projection.setRotation(settings.getRotation());
        projection.setFlip(settings.isMirrored());
        ProjectionManager.setProjection(pos, projection);

        // Switch to building mode
        settings.setMode(Settings.Mode.BUILDING);
        settings.setPos(pos);
        settings.setPlaced(true);
        settings.applyTo(held);
        settings.sendPacketToServer(InteractionHand.MAIN_HAND);

        // Clear the aim projection
        if (lastAimPos != null && !lastAimPos.equals(pos)) {
            ProjectionManager.removeProjection(lastAimPos);
        }
        lastAimPos = null;

        // Show confirmation message in orange
        player.displayClientMessage(
            Component.translatable("gui.multiblockprojector.projection_created",
                settings.getMultiblock().displayName()).withStyle(net.minecraft.ChatFormatting.GOLD),
            true
        );
    }

    static void autoBuildProjection(Player player, Settings settings, ItemStack held, BlockPos pos) {
        // Swing the projector for visual feedback
        player.swing(InteractionHand.MAIN_HAND, true);

        // Send network message to server to perform auto-build
        MessageAutoBuild.sendToServer(pos, InteractionHand.MAIN_HAND);

        // Clear the aim projection immediately (server will handle settings changes)
        if (lastAimPos != null) {
            ProjectionManager.removeProjection(lastAimPos);
            lastAimPos = null;
        }
    }

    private static void updateProjectionAtPos(BlockPos pos, Settings settings, Level level) {
        // Remove existing projection
        ProjectionManager.removeProjection(pos);

        // Create new projection with updated settings
        var variant = MultiblockProjection.getVariantFromSettings(settings.getMultiblock(), settings);
        MultiblockProjection projection = new MultiblockProjection(level, settings.getMultiblock(), variant);
        projection.setRotation(settings.getRotation());
        projection.setFlip(settings.isMirrored());

        ProjectionManager.setProjection(pos, projection);
    }

    /**
     * Clear ALL projections - used only for NOTHING_SELECTED mode or session resets
     */
    private static void clearAllProjections() {
        if (lastAimPos != null) {
            ProjectionManager.removeProjection(lastAimPos);
            lastAimPos = null;
        }

        // Clear all block validation data too
        BlockValidationManager.clearAll();

        // Clear any projections that might be lingering
        ProjectionManager.clearAll();
    }

    /**
     * Clean up only aim projections that don't match current projector state
     */
    private static void cleanupAimProjections(Settings settings) {
        if (settings.getMode() == Settings.Mode.NOTHING_SELECTED) {
            // No projections should exist in nothing selected mode
            clearAllProjections();
            return;
        }

        if (settings.getMode() == Settings.Mode.BUILDING) {
            // Clear any aim projection when in building mode
            if (lastAimPos != null) {
                ProjectionManager.removeProjection(lastAimPos);
                lastAimPos = null;
            }
            return;
        }

        // For PROJECTION mode and others, normal aim projection handling continues
    }

    /**
     * Check all active building projections for completion, regardless of held item
     */
    private static void checkAllBuildingProjectionsForCompletion(Player player, Level level) {
        // Check each projector in inventory for building mode projections
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractProjectorItem) {
                Settings settings = AbstractProjectorItem.getSettings(stack);

                if (settings.getMode() == Settings.Mode.BUILDING && settings.getPos() != null && settings.getMultiblock() != null) {
                    MultiblockProjection projection = ProjectionManager.getProjection(settings.getPos());
                    if (projection != null) {
                        // Validate projection and check for new incorrect blocks
                        boolean hasNewIncorrectBlocks = BlockValidationManager.validateProjection(settings.getPos(), projection, level);

                        // Show error message if new incorrect blocks were placed
                        if (hasNewIncorrectBlocks) {
                            player.displayClientMessage(
                                Component.literal("Incorrect block placed!")
                                    .withStyle(net.minecraft.ChatFormatting.RED),
                                true
                            );
                        }

                        // Check if projection is complete
                        if (BlockValidationManager.isProjectionComplete(settings.getPos(), projection, level)) {
                            // Store the position before clearing settings
                            BlockPos completedPos = settings.getPos();

                            // Projection is complete - return to nothing selected mode
                            settings.setMode(Settings.Mode.NOTHING_SELECTED);
                            settings.setPos(null);
                            settings.setPlaced(false);
                            settings.applyTo(stack);

                            // Clear projection and validation data using stored position
                            ProjectionManager.removeProjection(completedPos);
                            BlockValidationManager.clearValidation(completedPos);

                            // Show completion message with green color
                            player.displayClientMessage(
                                Component.literal("Multiblock structure completed!")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN),
                                true
                            );
                        }
                    }
                }
            }
        }
    }
}
