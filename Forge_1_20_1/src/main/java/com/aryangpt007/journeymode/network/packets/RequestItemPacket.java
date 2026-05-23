package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent when player requests an item from Journey Mode.
 * Ported to Forge 1.20.1.
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

    public static void encode(RequestItemPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.itemId);
        buf.writeVarInt(msg.count);
    }

    public static RequestItemPacket decode(FriendlyByteBuf buf) {
        return new RequestItemPacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(RequestItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.get().getSender();
            if (serverPlayer != null) {
                serverPlayer.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).ifPresent(journeyData -> {
                    ResourceLocation itemResourceLocation = new ResourceLocation(msg.itemId);
                    Item item = BuiltInRegistries.ITEM.get(itemResourceLocation);
                    
                    if (item != null && journeyData.isUnlocked(item)) {
                        ItemStack stack = new ItemStack(item, Math.min(msg.count, 64));
                        
                        // Try to add to inventory, if full drop on ground
                        if (!serverPlayer.getInventory().add(stack)) {
                            ItemEntity itemEntity = new ItemEntity(
                                serverPlayer.level(),
                                serverPlayer.getX(),
                                serverPlayer.getY(),
                                serverPlayer.getZ(),
                                stack
                            );
                            serverPlayer.level().addFreshEntity(itemEntity);
                        }
                    } else {
                        JourneyMode.LOGGER.warn("Player {} tried to request locked or invalid item: {}", 
                            serverPlayer.getName().getString(), msg.itemId);
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
