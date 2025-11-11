package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyModeFabric;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.platform.fabric.FabricDataHandler;
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
public record FabricOpenJourneyMenuPacket() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FabricOpenJourneyMenuPacket> TYPE = 
        new CustomPacketPayload.Type<>(JourneyModeFabric.id("open_journey_menu"));

    public static final StreamCodec<FriendlyByteBuf, FabricOpenJourneyMenuPacket> CODEC = 
        StreamCodec.unit(new FabricOpenJourneyMenuPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void registerHandler() {
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                // Check if Journey Mode is enabled for this player
                JourneyData data = FabricDataHandler.getJourneyData(player);
                if (data == null || !data.isEnabled()) {
                    player.displayClientMessage(
                        JourneyModeFabric.translatable("disabled_message"),
                        false
                    );
                    return;
                }
                
                player.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, p) -> new JourneyModeMenu(containerId, playerInventory),
                    JourneyModeFabric.translatable("menu.title")
                ));
            });
        });
    }
}
