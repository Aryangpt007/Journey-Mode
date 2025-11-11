package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyModeFabric;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.platform.fabric.FabricDataHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Packet sent when player requests an item from Journey Mode
 */
public record FabricRequestItemPacket(String itemId, int count) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FabricRequestItemPacket> TYPE = 
        new CustomPacketPayload.Type<>(JourneyModeFabric.id("request_item"));

    public static final StreamCodec<FriendlyByteBuf, FabricRequestItemPacket> CODEC = 
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            FabricRequestItemPacket::itemId,
            ByteBufCodecs.VAR_INT,
            FabricRequestItemPacket::count,
            FabricRequestItemPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                JourneyData journeyData = FabricDataHandler.getJourneyData(player);
                
                ResourceLocation itemResourceLocation = ResourceLocation.parse(packet.itemId);
                Item item = BuiltInRegistries.ITEM.get(itemResourceLocation);
                
                if (item != null && journeyData.isUnlocked(packet.itemId)) {
                    ItemStack stack = new ItemStack(item, Math.min(packet.count, 64));
                    
                    // Try to add to inventory, if full drop on ground
                    if (!player.getInventory().add(stack)) {
                        ItemEntity itemEntity = new ItemEntity(
                            player.level(),
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            stack
                        );
                        player.level().addFreshEntity(itemEntity);
                    }
                } else {
                    JourneyModeFabric.LOGGER.warn("Player {} tried to request locked or invalid item: {}", 
                        player.getName().getString(), packet.itemId);
                }
            });
        });
    }
}
