package com.multiblockprojector.common.network;

import com.multiblockprojector.UniversalProjector;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Network handler for Universal Projector mod packets
 */
@EventBusSubscriber(modid = UniversalProjector.MODID, bus = EventBusSubscriber.Bus.MOD)
public class NetworkHandler {
    
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(UniversalProjector.MODID);
        
        registrar.playBidirectional(
            MessageProjectorSync.TYPE,
            MessageProjectorSync.STREAM_CODEC,
            new DirectionalPayloadHandler<>(
                NetworkHandler::handleClientSide,
                NetworkHandler::handleServerSide
            )
        );
        
        registrar.playToServer(
            MessageAutoBuild.TYPE,
            MessageAutoBuild.STREAM_CODEC,
            NetworkHandler::handleAutoBuildServerSide
        );

        registrar.playToServer(
            MessageLinkBlock.TYPE,
            MessageLinkBlock.STREAM_CODEC,
            NetworkHandler::handleLinkBlockServerSide
        );

        registrar.playToServer(
            MessageFabricate.TYPE,
            MessageFabricate.STREAM_CODEC,
            NetworkHandler::handleFabricateServerSide
        );

        registrar.playToClient(
            MessageFabricationProgress.TYPE,
            MessageFabricationProgress.STREAM_CODEC,
            NetworkHandler::handleFabricationProgressClientSide
        );

        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            registrar.playToServer(
                MessageClipboardWrite.TYPE,
                MessageClipboardWrite.STREAM_CODEC,
                NetworkHandler::handleClipboardWriteServerSide
            );
        }
    }
    
    private static void handleClientSide(MessageProjectorSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                MessageProjectorSync.handleClientSide(packet, context.player());
            }
        });
    }
    
    private static void handleServerSide(MessageProjectorSync packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                MessageProjectorSync.handleServerSide(packet, context.player());
            }
        });
    }
    
    private static void handleAutoBuildServerSide(MessageAutoBuild packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                MessageAutoBuild.handleServerSide(packet, context.player());
            }
        });
    }

    private static void handleLinkBlockServerSide(MessageLinkBlock packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                MessageLinkBlock.handleServerSide(packet, context.player());
            }
        });
    }

    private static void handleFabricateServerSide(MessageFabricate packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                MessageFabricate.handleServerSide(packet, context.player());
            }
        });
    }

    private static void handleFabricationProgressClientSide(MessageFabricationProgress packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                MessageFabricationProgress.handleClientSide(packet, context.player());
            }
        });
    }

    private static void handleClipboardWriteServerSide(MessageClipboardWrite packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                MessageClipboardWrite.handleServerSide(packet, context.player());
            }
        });
    }
}