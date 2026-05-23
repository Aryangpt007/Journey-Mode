package com.aryangpt007.journeymode.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class DeleteCarriedPacket implements IMessage {
    public DeleteCarriedPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<DeleteCarriedPacket, IMessage> {
        @Override
        public IMessage onMessage(DeleteCarriedPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player != null) {
                    player.inventory.setItemStack(ItemStack.EMPTY);
                    player.connection.sendPacket(new SPacketSetSlot(-1, -1, ItemStack.EMPTY));
                    player.openContainer.detectAndSendChanges();
                }
            });
            return null;
        }
    }
}
