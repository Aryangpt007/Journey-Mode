package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyModeFabric;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packet to submit items in the deposit slot
 */
public record FabricSubmitDepositPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FabricSubmitDepositPacket> TYPE = 
        new CustomPacketPayload.Type<>(JourneyModeFabric.id("submit_deposit"));

    public static final StreamCodec<FriendlyByteBuf, FabricSubmitDepositPacket> CODEC = 
        StreamCodec.unit(new FabricSubmitDepositPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.containerMenu instanceof JourneyModeMenu menu) {
                    menu.processDeposit();
                }
            });
        });
    }
}
