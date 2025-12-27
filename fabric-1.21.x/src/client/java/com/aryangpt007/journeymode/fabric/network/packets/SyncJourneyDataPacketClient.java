package com.aryangpt007.journeymode.fabric.network.packets;

import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.fabric.platform.FabricDataHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class SyncJourneyDataPacketClient {
    private SyncJourneyDataPacketClient() {}

    public static void handle(SyncJourneyDataPacket packet, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            // Update client-side data with server data
            JourneyData journeyData = FabricDataHandler.getJourneyData(context.player());
            journeyData.updateFromSync(packet.collectedCounts(), packet.unlockedItems(), packet.unlockTimestamps());
            FabricDataHandler.saveJourneyData(context.player(), journeyData);
        });
    }
}
