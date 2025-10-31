package com.aryangpt007.journeymode.fabric.network.packets;

import com.aryangpt007.journeymode.fabric.JourneyModeFabric;
import com.aryangpt007.journeymode.fabric.menu.JourneyModeMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Packet to submit items in the deposit slot
 */
public record SubmitDepositPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SubmitDepositPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JourneyModeFabric.MOD_ID, "submit_deposit"));

    public static final StreamCodec<FriendlyByteBuf, SubmitDepositPacket> STREAM_CODEC = 
        StreamCodec.unit(new SubmitDepositPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubmitDepositPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer serverPlayer = context.player();
        context.server().execute(() -> {
            if (serverPlayer.containerMenu instanceof JourneyModeMenu menu) {
                menu.processDeposit();
            }
        });
    }
}
