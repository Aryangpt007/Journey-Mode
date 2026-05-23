package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to submit items in the deposit slot.
 * Ported to Forge 1.20.1.
 */
public class SubmitDepositPacket {
    public SubmitDepositPacket() {}

    public static void encode(SubmitDepositPacket msg, FriendlyByteBuf buf) {}

    public static SubmitDepositPacket decode(FriendlyByteBuf buf) {
        return new SubmitDepositPacket();
    }

    public static void handle(SubmitDepositPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.get().getSender();
            if (serverPlayer != null) {
                if (serverPlayer.containerMenu instanceof JourneyModeMenu menu) {
                    menu.processDeposit();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
