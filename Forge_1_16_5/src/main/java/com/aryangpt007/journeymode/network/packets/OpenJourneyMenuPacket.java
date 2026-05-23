package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to open the Journey Mode menu on the server.
 * Ported to Forge 1.20.1.
 */
public class OpenJourneyMenuPacket {
    public OpenJourneyMenuPacket() {}

    public static void encode(OpenJourneyMenuPacket msg, PacketBuffer buf) {}

    public static OpenJourneyMenuPacket decode(PacketBuffer buf) {
        return new OpenJourneyMenuPacket();
    }

    public static void handle(OpenJourneyMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity serverPlayer = ctx.get().getSender();
            if (serverPlayer != null) {
                serverPlayer.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).ifPresent(data -> {
                    if (!data.isEnabled()) {
                        serverPlayer.displayClientMessage(
                            JourneyMode.translatable("disabled_message"),
                            false
                        );
                        return;
                    }
                    
                    serverPlayer.openMenu(new SimpleNamedContainerProvider(
                        (containerId, playerInventory, player) -> new JourneyModeMenu(containerId, playerInventory),
                        JourneyMode.translatable("menu.title")
                    ));
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
