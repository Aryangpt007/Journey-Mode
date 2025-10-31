package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import com.aryangpt007.journeymode.platform.neoforge.NeoForgeDataHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet to open the Journey Mode menu
 */
public record OpenJourneyMenuPacket() implements CustomPacketPayload {
    public static final Type<OpenJourneyMenuPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JourneyMode.MODID, "open_journey_menu"));

    public static final StreamCodec<ByteBuf, OpenJourneyMenuPacket> STREAM_CODEC = StreamCodec.unit(new OpenJourneyMenuPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenJourneyMenuPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                // Check if Journey Mode is enabled for this player
                JourneyData data = NeoForgeDataHandler.getJourneyData(serverPlayer);
                if (data == null || !data.isEnabled()) {
                    serverPlayer.displayClientMessage(
                        JourneyMode.translatable("disabled_message"),
                        false
                    );
                    return;
                }
                
                serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, player) -> new JourneyModeMenu(containerId, playerInventory),
                    JourneyMode.translatable("menu.title")
                ));
            }
        });
    }
}
