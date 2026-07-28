package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
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
                JourneyDataAttachment journeyData = serverPlayer.getData(JourneyMode.JOURNEY_DATA);
                
                ItemStack stack = JourneyDataAttachment.itemStackFromKey(packet.itemId, serverPlayer.level().registryAccess());

                // §1 Shared Team Catalogs: unlock check must follow the same team-or-personal
                // resolution as everywhere else - a team member can fetch anything the TEAM
                // unlocked, not just their own personal unlocks. This is the anti-cheat
                // boundary (server is authoritative), so getting this branch right matters.
                boolean unlocked = journeyData.getTeamId() != null
                    ? com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(serverPlayer.getUUID())
                        .map(team -> team.isUnlocked(packet.itemId)).orElse(false)
                    : journeyData.isUnlocked(packet.itemId);

                if (!stack.isEmpty() && unlocked) {
                    // Clamp to this item's own stack limit, not a flat 64. A "full stack" is per item -
                    // swords stack to 1, potions to 16, modded items to whatever they declare - and a
                    // 64-count stack of a size-1 item gets spread across 64 inventory slots by
                    // Inventory.add(), which is exactly the duplication users saw. Enforced here rather
                    // than only client-side, since the count arrives over the network and cannot be
                    // trusted: a modified client asking for 64 diamond swords now gets one.
                    stack.setCount(Math.max(1, Math.min(packet.count, stack.getMaxStackSize())));
                    
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
