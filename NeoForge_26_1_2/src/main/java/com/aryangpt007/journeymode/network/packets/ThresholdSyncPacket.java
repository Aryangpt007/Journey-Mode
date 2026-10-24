package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.ThresholdTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> client: the recipe-derived unlock threshold of every item.
 *
 * <p>Needed because the client has not been sent the recipe list since Minecraft 1.21.2, so on a
 * dedicated server it cannot run {@link com.aryangpt007.journeymode.data.RecipeDepthCalculator}
 * itself. Without this the Deposit tab and progress tooltips would show a stack-size guess that
 * disagrees with what the server actually enforces.
 *
 * <p>Carries the calculator's answer only - config, datapack and API rules are synced separately
 * (ConfigSyncPacket) and applied in front of these values on the client, so the two can change
 * independently. Items travel as registry ids: two small varints per entry, which keeps even a
 * 30,000-item modpack far below the 1 MiB custom-payload ceiling.
 */
public record ThresholdSyncPacket(Map<Item, Integer> thresholds) implements CustomPacketPayload {
    public static final Type<ThresholdSyncPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(JourneyMode.MODID, "threshold_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThresholdSyncPacket> STREAM_CODEC = StreamCodec.of(
        ThresholdSyncPacket::encode,
        ThresholdSyncPacket::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, ThresholdSyncPacket packet) {
        buf.writeVarInt(packet.thresholds.size());
        for (Map.Entry<Item, Integer> entry : packet.thresholds.entrySet()) {
            buf.writeVarInt(BuiltInRegistries.ITEM.getId(entry.getKey()));
            buf.writeVarInt(entry.getValue());
        }
    }

    private static ThresholdSyncPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Item, Integer> thresholds = new HashMap<>(Math.max(16, size * 2));
        for (int i = 0; i < size; i++) {
            Item item = BuiltInRegistries.ITEM.byId(buf.readVarInt());
            int threshold = buf.readVarInt();
            thresholds.put(item, Math.max(1, threshold));
        }
        return new ThresholdSyncPacket(thresholds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ThresholdSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ThresholdTable.setSynced(packet.thresholds));
    }
}
