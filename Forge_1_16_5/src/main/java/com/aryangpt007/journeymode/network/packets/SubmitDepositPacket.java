package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to submit items in the deposit slot.
 * Ported to Forge 1.20.1.
 */
public class SubmitDepositPacket {
    public SubmitDepositPacket() {}

    public static void encode(SubmitDepositPacket msg, PacketBuffer buf) {}

    public static SubmitDepositPacket decode(PacketBuffer buf) {
        return new SubmitDepositPacket();
    }

    public static void handle(SubmitDepositPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity serverPlayer = ctx.get().getSender();
            if (serverPlayer != null) {
                if (serverPlayer.containerMenu instanceof JourneyModeMenu) {
                    JourneyModeMenu menu = (JourneyModeMenu) serverPlayer.containerMenu;
                    menu.processDeposit();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
