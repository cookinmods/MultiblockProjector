package com.multiblockprojector.data;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = "multiblockprojector", bus = EventBusSubscriber.Bus.MOD)
public class DataGenHandler {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var gen = event.getGenerator();
        gen.addProvider(event.includeServer() || event.includeClient(),
            new ExampleStructureProvider(gen.getPackOutput()));
    }
}
