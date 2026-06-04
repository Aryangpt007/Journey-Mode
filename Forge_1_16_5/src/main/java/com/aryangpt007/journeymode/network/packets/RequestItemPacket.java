package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.util.registry.Registry;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent when player requests an item from Journey Mode.
 */
public class RequestItemPacket {
    private final String itemId;
    private final int count;

    public RequestItemPacket(String itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public String getItemId() {
        return itemId;
    }

    public int getCount() {
        return count;
    }

    public static void encode(RequestItemPacket msg, PacketBuffer buf) {
        buf.writeUtf(msg.itemId);
        buf.writeVarInt(msg.count);
    }

    public static RequestItemPacket decode(PacketBuffer buf) {
        return new RequestItemPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(RequestItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity serverPlayer = ctx.get().getSender();
            if (serverPlayer != null) {
                serverPlayer.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).ifPresent(journeyData -> {
                    ItemStack stack = com.aryangpt007.journeymode.data.JourneyDataAttachment.itemStackFromKey(msg.itemId);
                    
                    if (!stack.isEmpty() && journeyData.isUnlocked(msg.itemId)) {
                        stack.setCount(Math.min(msg.count, 64));
                        
                        // Try to add to inventory, if full drop on ground
                        if (!serverPlayer.inventory.add(stack)) {
                            ItemEntity itemEntity = new ItemEntity(
                                serverPlayer.level,
                                serverPlayer.getX(),
                                serverPlayer.getY(),
                                serverPlayer.getZ(),
                                stack
                            );
                            serverPlayer.level.addFreshEntity(itemEntity);
                        }
                    } else {
                        JourneyMode.LOGGER.warn("PlayerEntity {} tried to request locked or invalid item: {}", 
                            serverPlayer.getName().getString(), msg.itemId);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
