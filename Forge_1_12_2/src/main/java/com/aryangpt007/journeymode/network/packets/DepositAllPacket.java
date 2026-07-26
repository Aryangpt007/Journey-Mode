package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Deposit All. No item payload - the server reads the player's own inventory, same trust model as
 * SubmitDepositPacket. includeHotbar mirrors whether the player shift-clicked the button client-side.
 */
public class DepositAllPacket implements IMessage {
    private boolean includeHotbar;

    public DepositAllPacket() {}

    public DepositAllPacket(boolean includeHotbar) {
        this.includeHotbar = includeHotbar;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        includeHotbar = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(includeHotbar);
    }

    public static class Handler implements IMessageHandler<DepositAllPacket, IMessage> {
        @Override
        public IMessage onMessage(DepositAllPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player != null && player.openContainer instanceof JourneyModeMenu) {
                    ((JourneyModeMenu) player.openContainer).processDepositAll(message.includeHotbar);
                }
            });
            return null;
        }
    }
}
