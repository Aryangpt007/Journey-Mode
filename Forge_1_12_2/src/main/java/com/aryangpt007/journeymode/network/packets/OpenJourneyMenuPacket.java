package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.GuiHandler;
import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class OpenJourneyMenuPacket implements IMessage {

    public OpenJourneyMenuPacket() {}

    @Override
    public void fromBytes(ByteBuf buf) {}

    @Override
    public void toBytes(ByteBuf buf) {}

    public static class Handler implements IMessageHandler<OpenJourneyMenuPacket, IMessage> {
        @Override
        public IMessage onMessage(OpenJourneyMenuPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player != null) {
                    IJourneyData data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
                    if (data != null) {
                        if (!data.isEnabled()) {
                            player.sendMessage(new TextComponentString("§cJourney Mode is disabled. Use /journeymode on to enable it."));
                            return;
                        }
                        
                        player.openGui(
                            JourneyMode.instance,
                            GuiHandler.JOURNEY_MODE_GUI_ID,
                            player.world,
                            (int) player.posX,
                            (int) player.posY,
                            (int) player.posZ
                        );
                    }
                }
            });
            return null;
        }
    }
}
