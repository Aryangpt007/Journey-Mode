package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class RequestItemPacket implements IMessage {
    private String itemId;
    private int count;

    public RequestItemPacket() {}

    public RequestItemPacket(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            itemId = packetBuffer.readString(32767);
            count = packetBuffer.readVarInt();
        } catch (Exception ignored) {}
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeString(itemId);
        packetBuffer.writeVarInt(count);
    }

    public static class Handler implements IMessageHandler<RequestItemPacket, IMessage> {
        @Override
        public IMessage onMessage(RequestItemPacket message, MessageContext ctx) {
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                EntityPlayerMP player = ctx.getServerHandler().player;
                if (player != null) {
                    IJourneyData journeyData = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
                    if (journeyData != null) {
                        String[] parts = message.itemId.split(":");
                        String regName = message.itemId;
                        int meta = 0;
                        if (parts.length > 2) {
                            regName = parts[0] + ":" + parts[1];
                            try {
                                meta = Integer.parseInt(parts[2]);
                            } catch (NumberFormatException ignored) {}
                        }
                        
                        ResourceLocation itemLoc = new ResourceLocation(regName);
                        Item item = ForgeRegistries.ITEMS.getValue(itemLoc);
                        
                        if (item != null) {
                            ItemStack stack = new ItemStack(item, Math.min(message.count, 64), meta);
                            if (journeyData.isUnlocked(stack)) {
                                if (!player.inventory.addItemStackToInventory(stack)) {
                                    EntityItem entityItem = new EntityItem(
                                        player.world,
                                        player.posX,
                                        player.posY,
                                        player.posZ,
                                        stack
                                    );
                                    player.world.spawnEntity(entityItem);
                                }
                            } else {
                                JourneyMode.LOGGER.warn("Player {} tried to request locked or invalid item: {}", 
                                    player.getName(), message.itemId);
                            }
                        }
                    }
                }
            });
            return null;
        }
    }
}
