package com.aryangpt007.journeymode.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to delete the currently carried (cursor) item stack.
 * Ported to Forge 1.20.1.
 */
public class DeleteCarriedPacket {
    public DeleteCarriedPacket() {}

    public static void encode(DeleteCarriedPacket msg, FriendlyByteBuf buf) {}

    public static DeleteCarriedPacket decode(FriendlyByteBuf buf) {
        return new DeleteCarriedPacket();
    }

    public static void handle(DeleteCarriedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
