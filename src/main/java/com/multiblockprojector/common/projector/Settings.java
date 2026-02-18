package com.multiblockprojector.common.projector;

import com.multiblockprojector.api.IUniversalMultiblock;
import com.multiblockprojector.api.MultiblockDefinition;
import com.multiblockprojector.api.UniversalMultiblockHandler;
import com.multiblockprojector.common.network.MessageProjectorSync;
import com.multiblockprojector.common.registry.MultiblockIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Settings storage for projector tool
 * Ported from Immersive Petroleum and updated for universal multiblock support
 */
public class Settings {
    public static final String KEY_SELF = "settings";
    public static final String KEY_MODE = "mode";
    public static final String KEY_MULTIBLOCK = "multiblock";
    public static final String KEY_MIRROR = "mirror";
    public static final String KEY_PLACED = "placed";
    public static final String KEY_ROTATION = "rotation";
    public static final String KEY_POSITION = "pos";
    public static final String KEY_AUTO_BUILD = "autoBuild";
    public static final String KEY_SIZE_PRESET = "sizePreset";

    private Mode mode;
    private Rotation rotation;
    private BlockPos pos = null;
    private ResourceLocation multiblockId = null;
    private boolean mirror;
    private boolean isPlaced;
    private int sizePresetIndex = 0;
    
    public Settings() {
        this(new CompoundTag());
    }
    
    public Settings(@Nullable final ItemStack stack) {
        this(((Supplier<CompoundTag>) () -> {
            if (stack == null) {
                return new CompoundTag();
            }
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag rootTag = customData.copyTag();
            if (rootTag.contains(KEY_SELF, Tag.TAG_COMPOUND)) {
                return rootTag.getCompound(KEY_SELF);
            }
            return new CompoundTag();
        }).get());
    }
    
    public Settings(CompoundTag settingsNbt) {
        if (settingsNbt == null || settingsNbt.isEmpty()) {
            this.mode = Mode.NOTHING_SELECTED;
            this.rotation = Rotation.NONE;
            this.mirror = false;
            this.isPlaced = false;
            this.sizePresetIndex = 0;
        } else {
            this.mode = Mode.values()[Mth.clamp(settingsNbt.getInt(KEY_MODE), 0, Mode.values().length - 1)];
            this.rotation = Rotation.values()[settingsNbt.contains(KEY_ROTATION) ? settingsNbt.getInt(KEY_ROTATION) : 0];
            this.mirror = settingsNbt.getBoolean(KEY_MIRROR);
            this.isPlaced = settingsNbt.getBoolean(KEY_PLACED);
            this.sizePresetIndex = settingsNbt.getInt(KEY_SIZE_PRESET);

            if (settingsNbt.contains(KEY_MULTIBLOCK, Tag.TAG_STRING)) {
                String str = settingsNbt.getString(KEY_MULTIBLOCK);
                this.multiblockId = ResourceLocation.parse(str);
            }

            if (settingsNbt.contains(KEY_POSITION, Tag.TAG_COMPOUND)) {
                CompoundTag pos = settingsNbt.getCompound(KEY_POSITION);
                int x = pos.getInt("x");
                int y = pos.getInt("y");
                int z = pos.getInt("z");
                this.pos = new BlockPos(x, y, z);
            }
        }
    }
    
    /** Rotate by 90° Clockwise */
    public void rotateCW() {
        this.rotation = this.rotation.getRotated(Rotation.CLOCKWISE_90);
    }
    
    /** Rotate by 90° Counter-Clockwise */
    public void rotateCCW() {
        this.rotation = this.rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
    }
    
    public void flip() {
        this.mirror = !this.mirror;
    }
    
    public void switchMode() {
        int id = this.mode.ordinal() + 1;
        this.mode = Mode.values()[id % Mode.values().length];
    }
    
    public void sendPacketToServer(InteractionHand hand) {
        MessageProjectorSync.sendToServer(this, hand);
    }
    
    public void sendPacketToClient(Player player, InteractionHand hand) {
        MessageProjectorSync.sendToClient(player, this, hand);
    }
    
    // Getters and setters
    
    public Rotation getRotation() { return this.rotation; }
    public void setRotation(Rotation rotation) { this.rotation = rotation; }
    
    public Mode getMode() { return this.mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    
    @Nullable
    public MultiblockDefinition getMultiblock() {
        if (multiblockId == null) return null;
        return MultiblockIndex.get().getById(multiblockId).orElse(null);
    }

    public void setMultiblock(@Nullable MultiblockDefinition multiblock) {
        if (multiblock == null) {
            this.multiblockId = null;
        } else {
            this.multiblockId = MultiblockIndex.get().getId(multiblock).orElse(null);
        }
    }

    public void setMultiblockId(@Nullable ResourceLocation id) {
        this.multiblockId = id;
    }

    @Nullable
    public ResourceLocation getMultiblockId() {
        return this.multiblockId;
    }

    /**
     * Legacy bridge: returns IUniversalMultiblock for callers not yet migrated to MultiblockDefinition.
     * @deprecated Use {@link #getMultiblock()} which returns {@link MultiblockDefinition}.
     */
    @Deprecated
    @Nullable
    public IUniversalMultiblock getLegacyMultiblock() {
        if (multiblockId == null) return null;
        return UniversalMultiblockHandler.getByUniqueName(multiblockId);
    }

    public boolean isMirrored() { return this.mirror; }
    public void setMirror(boolean mirror) { this.mirror = mirror; }
    
    public boolean isPlaced() { return this.isPlaced; }
    public void setPlaced(boolean isPlaced) { this.isPlaced = isPlaced; }
    
    @Nullable
    public BlockPos getPos() { return this.pos; }
    public void setPos(@Nullable BlockPos pos) { this.pos = pos; }

    public int getSizePresetIndex() { return this.sizePresetIndex; }
    public void setSizePresetIndex(int index) { this.sizePresetIndex = Math.max(0, index); }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt(KEY_MODE, this.mode.ordinal());
        nbt.putInt(KEY_ROTATION, this.rotation.ordinal());
        nbt.putBoolean(KEY_MIRROR, this.mirror);
        nbt.putBoolean(KEY_PLACED, this.isPlaced);
        nbt.putInt(KEY_SIZE_PRESET, this.sizePresetIndex);

        if (this.multiblockId != null) {
            nbt.putString(KEY_MULTIBLOCK, this.multiblockId.toString());
        }

        if (this.pos != null) {
            CompoundTag pos = new CompoundTag();
            pos.putInt("x", this.pos.getX());
            pos.putInt("y", this.pos.getY());
            pos.putInt("z", this.pos.getZ());
            nbt.put(KEY_POSITION, pos);
        }

        return nbt;
    }
    
    public ItemStack applyTo(ItemStack stack) {
        CustomData currentData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag rootTag = currentData.copyTag();
        rootTag.put(KEY_SELF, this.toNbt());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(rootTag));
        return stack;
    }
    
    @Override
    public String toString() {
        return "\"Settings\":[" + toNbt().toString() + "]";
    }
    
    public enum Mode {
        NOTHING_SELECTED, MULTIBLOCK_SELECTION, PROJECTION, BUILDING;
        
        final String translation;
        
        Mode() {
            this.translation = "desc.multiblockprojector.info.projector.mode_" + ordinal();
        }
        
        public Component getTranslated() {
            return Component.translatable(this.translation);
        }
    }
}