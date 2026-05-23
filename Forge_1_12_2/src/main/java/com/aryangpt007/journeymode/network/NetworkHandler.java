package com.aryangpt007.journeymode.network;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.network.packets.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class NetworkHandler {
    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(JourneyMode.MODID);

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(OpenJourneyMenuPacket.Handler.class, OpenJourneyMenuPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(RequestItemPacket.Handler.class, RequestItemPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(SubmitDepositPacket.Handler.class, SubmitDepositPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(SyncTabPacket.Handler.class, SyncTabPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(DeleteCarriedPacket.Handler.class, DeleteCarriedPacket.class, id++, Side.SERVER);
        CHANNEL.registerMessage(SyncJourneyDataPacket.Handler.class, SyncJourneyDataPacket.class, id++, Side.CLIENT);
    }

    public static void sendToServer(IMessage message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendTo(IMessage message, EntityPlayerMP player) {
        CHANNEL.sendTo(message, player);
    }
}
