package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.platform.neoforge.NeoForgeDataHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet sent when player requests an item from Journey Mode
 */
public record RequestItemPacket(String itemId, int count) implements CustomPacketPayload {
    public static final Type<RequestItemPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "request_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestItemPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        RequestItemPacket::itemId,
        ByteBufCodecs.VAR_INT,
        RequestItemPacket::count,
        RequestItemPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestItemPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                JourneyData journeyData = NeoForgeDataHandler.getJourneyData(serverPlayer);
                
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
                    JourneyMode.LOGGER.warn("Player {} tried to request locked or invalid item: {}", 
                        serverPlayer.getName().getString(), packet.itemId);
                }
            }
        });
    }
}
