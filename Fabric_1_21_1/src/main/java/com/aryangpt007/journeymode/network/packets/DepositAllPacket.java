package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * §8 Deposit All. No item payload - the server reads the player's own inventory, same trust
 * model as SubmitDepositPacket. includeHotbar mirrors whether the player shift-clicked the
 * button client-side.
 */
public record DepositAllPacket(boolean includeHotbar) implements CustomPacketPayload {
    public static final Type<DepositAllPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "deposit_all"));

    public static final StreamCodec<ByteBuf, DepositAllPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL,
        DepositAllPacket::includeHotbar,
        DepositAllPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
