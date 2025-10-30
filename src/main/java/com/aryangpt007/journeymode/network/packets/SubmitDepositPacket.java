package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet to submit items in the deposit slot
 */
public record SubmitDepositPacket() implements CustomPacketPayload {
    public static final Type<SubmitDepositPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "submit_deposit"));

    public static final StreamCodec<ByteBuf, SubmitDepositPacket> STREAM_CODEC = StreamCodec.unit(new SubmitDepositPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SubmitDepositPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (serverPlayer.containerMenu instanceof JourneyModeMenu menu) {
                    menu.processDeposit();
                }
            }
        });
    }
}
