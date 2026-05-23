package com.aryangpt007.journeymode.network.packets;

import net.minecraft.network.PacketBuffer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent from client to server to delete the currently carried (cursor) item stack.
 */
public class DeleteCarriedPacket {
    public DeleteCarriedPacket() {}

    public static void encode(DeleteCarriedPacket msg, PacketBuffer buf) {}

    public static DeleteCarriedPacket decode(PacketBuffer buf) {
        return new DeleteCarriedPacket();
    }

    public static void handle(DeleteCarriedPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                player.inventory.setCarried(ItemStack.EMPTY);
                player.connection.send(new net.minecraft.network.play.server.SSetSlotPacket(-1, -1, ItemStack.EMPTY));
                player.containerMenu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
