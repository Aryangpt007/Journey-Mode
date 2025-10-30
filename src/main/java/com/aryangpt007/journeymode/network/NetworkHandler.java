package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import com.aryangpt007.journeymode.network.packets.RequestItemPacket;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::registerPayloads);
    }

    private static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(JourneyMode.MODID)
            .versioned(PROTOCOL_VERSION);

        registrar.playToServer(
            OpenJourneyMenuPacket.TYPE,
            OpenJourneyMenuPacket.STREAM_CODEC,
            OpenJourneyMenuPacket::handle
        );

        registrar.playToServer(
            RequestItemPacket.TYPE,
            RequestItemPacket.STREAM_CODEC,
            RequestItemPacket::handle
        );
        
        registrar.playToClient(
            SyncJourneyDataPacket.TYPE,
            SyncJourneyDataPacket.STREAM_CODEC,
            SyncJourneyDataPacket::handle
        );
    }
}
