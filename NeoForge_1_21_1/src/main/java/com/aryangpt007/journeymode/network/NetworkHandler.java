package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.network.packets.OpenJourneyMenuPacket;
import com.aryangpt007.journeymode.network.packets.RequestItemPacket;
import com.aryangpt007.journeymode.network.packets.SubmitDepositPacket;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import com.aryangpt007.journeymode.network.packets.SyncTabPacket;
import com.aryangpt007.journeymode.network.packets.DeleteCarriedPacket;
import com.aryangpt007.journeymode.network.packets.ConfigSyncPacket;
import com.aryangpt007.journeymode.network.packets.DepositAllPacket;
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
        
        registrar.playToServer(
            SubmitDepositPacket.TYPE,
            SubmitDepositPacket.STREAM_CODEC,
            SubmitDepositPacket::handle
        );

        registrar.playToServer(
            SyncTabPacket.TYPE,
            SyncTabPacket.STREAM_CODEC,
            SyncTabPacket::handle
        );

        registrar.playToServer(
            DeleteCarriedPacket.TYPE,
            DeleteCarriedPacket.STREAM_CODEC,
            DeleteCarriedPacket::handle
        );

        registrar.playToServer(
            DepositAllPacket.TYPE,
            DepositAllPacket.STREAM_CODEC,
            DepositAllPacket::handle
        );

        registrar.playToClient(
            SyncJourneyDataPacket.TYPE,
            SyncJourneyDataPacket.STREAM_CODEC,
            SyncJourneyDataPacket::handle
        );

        registrar.playToClient(
            ConfigSyncPacket.TYPE,
            ConfigSyncPacket.STREAM_CODEC,
            ConfigSyncPacket::handle
        );
    }
}
