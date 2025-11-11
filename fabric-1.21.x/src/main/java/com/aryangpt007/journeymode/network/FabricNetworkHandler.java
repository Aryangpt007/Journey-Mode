package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyModeFabric;
import com.aryangpt007.journeymode.network.packets.FabricOpenJourneyMenuPacket;
import com.aryangpt007.journeymode.network.packets.FabricRequestItemPacket;
import com.aryangpt007.journeymode.network.packets.FabricSubmitDepositPacket;
import com.aryangpt007.journeymode.network.packets.FabricSyncJourneyDataPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric network handler for Journey Mode packets
 */
public class FabricNetworkHandler {
    
    public static void register() {
        // Register server-bound packets
        PayloadTypeRegistry.playC2S().register(
            FabricOpenJourneyMenuPacket.TYPE,
            FabricOpenJourneyMenuPacket.CODEC
        );
        
        PayloadTypeRegistry.playC2S().register(
            FabricRequestItemPacket.TYPE,
            FabricRequestItemPacket.CODEC
        );
        
        PayloadTypeRegistry.playC2S().register(
            FabricSubmitDepositPacket.TYPE,
            FabricSubmitDepositPacket.CODEC
        );
        
        // Register client-bound packets
        PayloadTypeRegistry.playS2C().register(
            FabricSyncJourneyDataPacket.TYPE,
            FabricSyncJourneyDataPacket.CODEC
        );
        
        // Register packet handlers
        FabricOpenJourneyMenuPacket.registerHandler();
        FabricRequestItemPacket.registerHandler();
        FabricSubmitDepositPacket.registerHandler();
        FabricSyncJourneyDataPacket.registerHandler();
    }
    
    /**
     * Send a packet to the client
     */
    public static void sendToClient(ServerPlayer player, Object packet) {
        if (packet instanceof CustomPacketPayload payload) {
            ServerPlayNetworking.send(player, payload);
        }
    }
    
    /**
     * Send a packet to the server
     */
    public static void sendToServer(Object packet) {
        if (packet instanceof CustomPacketPayload payload) {
            ClientPlayNetworking.send(payload);
        }
    }
}
