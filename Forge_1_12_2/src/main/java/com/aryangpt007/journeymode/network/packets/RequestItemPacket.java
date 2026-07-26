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
                        ItemStack stack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(message.itemId);

                        // §1 Shared Team Catalogs: unlock check must follow the same
                        // team-or-personal resolution as everywhere else - a team member can
                        // fetch anything the TEAM unlocked, not just their own personal unlocks.
                        // This is the anti-cheat boundary (server is authoritative), so getting
                        // this branch right matters.
                        boolean unlocked;
                        if (journeyData.getTeamId() != null) {
                            com.aryangpt007.journeymode.data.TeamData team =
                                com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUniqueID());
                            unlocked = team != null && team.isUnlocked(message.itemId);
                        } else {
                            unlocked = journeyData.isUnlocked(message.itemId);
                        }

                        if (!stack.isEmpty() && unlocked) {
                            stack.setCount(Math.min(message.count, 64));
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
            });
            return null;
        }
    }
}
