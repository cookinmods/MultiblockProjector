package com.multiblockprojector.common.items;

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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Abstract base class for all projector item variants.
 * Extracts shared logic from ProjectorItem so that multiple projector types
 * (standard, creative, fabricator, battery fabricator) can share common behavior.
 */
public abstract class AbstractProjectorItem extends Item {

    public AbstractProjectorItem() {
        this(new Item.Properties().stacksTo(1));
    }

    public AbstractProjectorItem(Item.Properties properties) {
        super(properties.stacksTo(1));
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
                    handleProjectionRightClick(world, player, hand, held, settings);
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

    /**
     * Called when the player right-clicks while in PROJECTION mode.
     * Subclasses define what happens (e.g., cancel on sneak, place, rotate, etc.).
     *
     * @param world    the level
     * @param player   the player
     * @param hand     the hand used
     * @param held     the held item stack
     * @param settings the current projector settings
     */
    protected abstract void handleProjectionRightClick(Level world, Player player, InteractionHand hand, ItemStack held, Settings settings);

    @Override
    @Nonnull
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    public static Settings getSettings(@Nullable ItemStack stack) {
        return new Settings(stack);
    }

    protected void openGUI(InteractionHand hand, ItemStack held) {
        if (net.minecraft.client.Minecraft.getInstance() != null) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.multiblockprojector.client.gui.ProjectorScreen(held, hand)
            );
        }
    }

    protected void createProjection(BlockPos pos, Settings settings, Level world) {
        if (world.isClientSide && settings.getMultiblock() != null) {
            var variant = MultiblockProjection.getVariantFromSettings(settings.getMultiblock(), settings);
            MultiblockProjection projection = new MultiblockProjection(world, settings.getMultiblock(), variant);

            projection.setRotation(settings.getRotation());
            projection.setFlip(settings.isMirrored());

            com.multiblockprojector.client.ProjectionManager.setProjection(pos, projection);
        }
    }

    protected void clearProjection(Settings settings) {
        if (settings.getPos() != null) {
            com.multiblockprojector.client.ProjectionManager.removeProjection(settings.getPos());
            com.multiblockprojector.client.BlockValidationManager.clearValidation(settings.getPos());
        }
    }

    protected void updateProjection(Settings settings) {
        if (settings.getPos() != null && settings.getMultiblock() != null) {
            Level world = net.minecraft.client.Minecraft.getInstance().level;
            if (world != null) {
                clearProjection(settings);
                createProjection(settings.getPos(), settings, world);
            }
        }
    }
}
