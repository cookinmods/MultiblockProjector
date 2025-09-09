package com.multiblockprojector.common;

import com.multiblockprojector.UniversalProjector;
import com.multiblockprojector.common.items.ProjectorItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Content registration for Universal Projector
 */
public class UPContent {
    
    // Items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, UniversalProjector.MODID);
    
    // Creative Tab
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, UniversalProjector.MODID);
    
    // Item Registry
    public static final DeferredHolder<Item, ProjectorItem> PROJECTOR = ITEMS.register("projector", ProjectorItem::new);
    
    // Creative Tab Registry
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.multiblockprojector.main"))
            .icon(() -> new ItemStack(PROJECTOR.get()))
            .displayItems((params, output) -> {
                output.accept(PROJECTOR.get());
            })
            .build()
    );
    
    public static void init(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        
        UniversalProjector.LOGGER.info("Registered Universal Projector content");
    }
}