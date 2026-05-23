package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to sync active tab state.
 * Ported to Forge 1.20.1.
 */
public class SyncTabPacket {
    private final boolean inJourneyTab;

    public SyncTabPacket(boolean inJourneyTab) {
        this.inJourneyTab = inJourneyTab;
    }

    public boolean isInJourneyTab() {
        return inJourneyTab;
    }

    public static void encode(SyncTabPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.inJourneyTab);
    }

    public static SyncTabPacket decode(FriendlyByteBuf buf) {
        return new SyncTabPacket(buf.readBoolean());
    }

    public static void handle(SyncTabPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.get().getSender();
            if (serverPlayer != null) {
                if (serverPlayer.containerMenu instanceof JourneyModeMenu menu) {
                    menu.setInJourneyTab(msg.inJourneyTab);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
