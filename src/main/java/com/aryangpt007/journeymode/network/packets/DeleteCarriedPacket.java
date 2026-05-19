package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet sent from client to server to delete the currently carried (cursor) item stack
 */
public record DeleteCarriedPacket() implements CustomPacketPayload {
    public static final Type<DeleteCarriedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "delete_carried"));

    public static final StreamCodec<ByteBuf, DeleteCarriedPacket> STREAM_CODEC = StreamCodec.unit(new DeleteCarriedPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeleteCarriedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // Clear the carried item stack on player's container menu (cursor stack)
                serverPlayer.containerMenu.setCarried(ItemStack.EMPTY);
                serverPlayer.containerMenu.broadcastChanges();
            }
        });
    }
}
