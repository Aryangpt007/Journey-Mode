package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.network.packets.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(JourneyMode.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenJourneyMenuPacket.class, OpenJourneyMenuPacket::encode, OpenJourneyMenuPacket::decode, OpenJourneyMenuPacket::handle);
        CHANNEL.registerMessage(id++, RequestItemPacket.class, RequestItemPacket::encode, RequestItemPacket::decode, RequestItemPacket::handle);
        CHANNEL.registerMessage(id++, SubmitDepositPacket.class, SubmitDepositPacket::encode, SubmitDepositPacket::decode, SubmitDepositPacket::handle);
        CHANNEL.registerMessage(id++, SyncTabPacket.class, SyncTabPacket::encode, SyncTabPacket::decode, SyncTabPacket::handle);
        CHANNEL.registerMessage(id++, DeleteCarriedPacket.class, DeleteCarriedPacket::encode, DeleteCarriedPacket::decode, DeleteCarriedPacket::handle);
        CHANNEL.registerMessage(id++, SyncJourneyDataPacket.class, SyncJourneyDataPacket::encode, SyncJourneyDataPacket::decode, SyncJourneyDataPacket::handle);
    }
}
