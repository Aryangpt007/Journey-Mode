package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class SyncTabPacket implements IMessage {
    private boolean inJourneyTab;

    public SyncTabPacket() {}

    public SyncTabPacket(boolean inJourneyTab) {
        this.inJourneyTab = inJourneyTab;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        inJourneyTab = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(inJourneyTab);
    }

    public static class Handler implements IMessageHandler<SyncTabPacket, IMessage> {
        @Override
        public IMessage onMessage(SyncTabPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player != null && player.openContainer instanceof JourneyModeMenu) {
                    ((JourneyModeMenu) player.openContainer).setInJourneyTab(message.inJourneyTab);
                }
            });
            return null;
        }
    }
}
