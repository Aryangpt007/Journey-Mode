package com.aryangpt007.journeymode.fabric.network;

import com.aryangpt007.journeymode.fabric.network.packets.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Handles network packet registration for Fabric
 */
public class FabricNetworkHandler {
    
    public static void registerPackets() {
        // Register packet types
        PayloadTypeRegistry.playC2S().register(OpenJourneyMenuPacket.TYPE, OpenJourneyMenuPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RequestItemPacket.TYPE, RequestItemPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SubmitDepositPacket.TYPE, SubmitDepositPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SyncJourneyDataPacket.TYPE, SyncJourneyDataPacket.STREAM_CODEC);
        
        // Register server-side packet handlers
        ServerPlayNetworking.registerGlobalReceiver(OpenJourneyMenuPacket.TYPE, 
            (payload, context) -> OpenJourneyMenuPacket.handle(payload, context));
        ServerPlayNetworking.registerGlobalReceiver(RequestItemPacket.TYPE, 
            (payload, context) -> RequestItemPacket.handle(payload, context));
        ServerPlayNetworking.registerGlobalReceiver(SubmitDepositPacket.TYPE, 
            (payload, context) -> SubmitDepositPacket.handle(payload, context));
    }
    
    public static void registerClientPackets() {
        // Register client-side packet handlers
        ClientPlayNetworking.registerGlobalReceiver(SyncJourneyDataPacket.TYPE, 
            (payload, context) -> SyncJourneyDataPacket.handle(payload, context));
    }
}
