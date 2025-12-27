package com.aryangpt007.journeymode.fabric.network;

import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.fabric.network.packets.SyncJourneyDataPacket;
import com.aryangpt007.journeymode.fabric.platform.FabricDataHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class FabricClientNetworkHandler {

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(SyncJourneyDataPacket.TYPE, (packet, context) -> {
            context.client().execute(() -> {
                var player = context.player();
                if (player != null && player.level().isClientSide) {
                    JourneyData data = FabricDataHandler.getJourneyData(player);
                    data.updateFromSync(packet.collectedCounts(), packet.unlockedItems(), packet.unlockTimestamps());
                    FabricDataHandler.saveJourneyData(player, data);
                }
            });
        });
    }
}
