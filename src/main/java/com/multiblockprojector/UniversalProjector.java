package com.multiblockprojector;

import com.multiblockprojector.common.CommonProxy;
import com.multiblockprojector.common.UPContent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(UniversalProjector.MODID)
public class UniversalProjector {
    public static final String MODID = "multiblockprojector";
    public static final Logger LOGGER = LogManager.getLogger();
    
    public static CommonProxy proxy;
    
    public UniversalProjector(IEventBus modEventBus) {
        LOGGER.info("Initializing Multiblock Projector");
        
        // Set up proxy
        if (FMLEnvironment.dist == Dist.CLIENT) {
            try {
                Class<?> clientProxy = Class.forName("com.multiblockprojector.client.ClientProxy");
                proxy = (CommonProxy) clientProxy.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                LOGGER.error("Failed to load client proxy", e);
                proxy = new CommonProxy();
            }
        } else {
            proxy = new CommonProxy();
        }
        
        // Register content
        UPContent.init(modEventBus);
        
        // Setup lifecycle events
        modEventBus.addListener(this::commonSetup);
        
        // Initialize proxy
        proxy.init();
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Multiblock Projector common setup");
    }
    
    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}