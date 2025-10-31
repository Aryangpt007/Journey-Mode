package com.aryangpt007.journeymode.fabric.network.packets;

import com.aryangpt007.journeymode.fabric.JourneyModeFabric;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.fabric.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.fabric.platform.FabricDataHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/**
 * Packet to open the Journey Mode menu
 */
public record OpenJourneyMenuPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenJourneyMenuPacket> TYPE = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JourneyModeFabric.MOD_ID, "open_journey_menu"));

    public static final StreamCodec<FriendlyByteBuf, OpenJourneyMenuPacket> STREAM_CODEC = 
        StreamCodec.unit(new OpenJourneyMenuPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenJourneyMenuPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer serverPlayer = context.player();
        context.server().execute(() -> {
            // Check if Journey Mode is enabled for this player
            JourneyData data = FabricDataHandler.getJourneyData(serverPlayer);
            if (data == null || !data.isEnabled()) {
                serverPlayer.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("journeymode.disabled_message"),
                    false
                );
                return;
            }
            
            serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, playerInventory, player) -> new JourneyModeMenu(containerId, playerInventory),
                net.minecraft.network.chat.Component.translatable("journeymode.menu.title")
            ));
        });
    }
}
