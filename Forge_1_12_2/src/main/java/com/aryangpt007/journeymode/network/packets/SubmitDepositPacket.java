package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SubmitDepositPacket implements IMessage {
    public SubmitDepositPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<SubmitDepositPacket, IMessage> {
        @Override
        public IMessage onMessage(SubmitDepositPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player != null && player.openContainer instanceof JourneyModeMenu) {
                    ((JourneyModeMenu) player.openContainer).processDeposit();
                }
            });
            return null;
        }
    }
}
