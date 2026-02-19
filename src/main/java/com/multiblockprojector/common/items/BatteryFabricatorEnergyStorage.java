package com.multiblockprojector.common.items;

import com.multiblockprojector.common.projector.Settings;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.world.item.ItemStack;

/**
 * IEnergyStorage implementation for BatteryFabricatorItem.
 * Stores energy in the item's CUSTOM_DATA via Settings.
 */
public class BatteryFabricatorEnergyStorage implements IEnergyStorage {

    private final ItemStack stack;

    public BatteryFabricatorEnergyStorage(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        Settings settings = AbstractProjectorItem.getSettings(stack);
        int stored = settings.getStoredEnergy();
        int accepted = Math.min(BatteryFabricatorItem.MAX_ENERGY - stored, maxReceive);
        if (!simulate && accepted > 0) {
            settings.setStoredEnergy(stored + accepted);
            settings.applyTo(stack);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        Settings settings = AbstractProjectorItem.getSettings(stack);
        int stored = settings.getStoredEnergy();
        int extracted = Math.min(stored, maxExtract);
        if (!simulate && extracted > 0) {
            settings.setStoredEnergy(stored - extracted);
            settings.applyTo(stack);
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return AbstractProjectorItem.getSettings(stack).getStoredEnergy();
    }

    @Override
    public int getMaxEnergyStored() {
        return BatteryFabricatorItem.MAX_ENERGY;
    }

    @Override
    public boolean canExtract() { return true; }

    @Override
    public boolean canReceive() { return true; }
}
