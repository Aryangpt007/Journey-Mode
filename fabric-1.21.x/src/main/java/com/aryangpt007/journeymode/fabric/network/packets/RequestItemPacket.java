package com.aryangpt007.journeymode.fabric.network.packets;

import com.aryangpt007.journeymode.fabric.JourneyModeFabric;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.fabric.platform.FabricDataHandler;
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
public record RequestItemPacket(String itemId, int count) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestItemPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JourneyModeFabric.MOD_ID, "request_item"));

    public static final StreamCodec<FriendlyByteBuf, RequestItemPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        RequestItemPacket::itemId,
        ByteBufCodecs.VAR_INT,
        RequestItemPacket::count,
        RequestItemPacket::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestItemPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer serverPlayer = context.player();
        context.server().execute(() -> {
            JourneyData journeyData = FabricDataHandler.getJourneyData(serverPlayer);
            
            ResourceLocation itemResourceLocation = ResourceLocation.parse(packet.itemId);
            Item item = BuiltInRegistries.ITEM.get(itemResourceLocation);
            
            if (item != null && journeyData.isUnlocked(packet.itemId)) {
                ItemStack stack = new ItemStack(item, Math.min(packet.count, 64));
                
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
                JourneyModeFabric.LOGGER.warn("Player {} tried to request locked or invalid item: {}", 
                    serverPlayer.getName().getString(), packet.itemId);
            }
        });
    }
}
