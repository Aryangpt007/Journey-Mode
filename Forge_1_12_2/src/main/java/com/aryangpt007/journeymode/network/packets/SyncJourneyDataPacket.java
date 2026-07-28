package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

public class SyncJourneyDataPacket implements IMessage {
    private Map<String, Integer> collectedCounts;
    private Set<String> unlockedItems;
    private Map<String, Long> unlockTimestamps;
    private boolean enabled;
    private boolean showTooltips;
    private String teamDisplayName; // empty string = no team

    public SyncJourneyDataPacket() {
        this.collectedCounts = new HashMap<>();
        this.unlockedItems = new HashSet<>();
        this.unlockTimestamps = new HashMap<>();
        this.enabled = true;
        this.showTooltips = true;
        this.teamDisplayName = "";
    }

    /**
     * §2/§3: carries `enabled`/`showTooltips` too - the old 3-argument-equivalent shape never
     * did, so the client's own capability instance (a separate object from the server's, even in
     * singleplayer) silently never reflected /journeymode off or a tooltip preference change.
     * §1 Shared Team Catalogs: also carries the team's display name for the client-side badge -
     * empty string means "not on a team".
     */
    public SyncJourneyDataPacket(Map<String, Integer> collectedCounts, Set<String> unlockedItems, Map<String, Long> unlockTimestamps, boolean enabled, boolean showTooltips, String teamDisplayName) {
        this.collectedCounts = collectedCounts;
        this.unlockedItems = unlockedItems;
        this.unlockTimestamps = unlockTimestamps;
        this.enabled = enabled;
        this.showTooltips = showTooltips;
        this.teamDisplayName = teamDisplayName == null ? "" : teamDisplayName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        try {
            int collectedSize = packetBuffer.readInt();
            collectedCounts = new HashMap<>();
            for (int i = 0; i < collectedSize; i++) {
                collectedCounts.put(packetBuffer.readString(32767), packetBuffer.readInt());
            }

            int unlockedSize = packetBuffer.readInt();
            unlockedItems = new HashSet<>();
            for (int i = 0; i < unlockedSize; i++) {
                unlockedItems.add(packetBuffer.readString(32767));
            }

            int timestampsSize = packetBuffer.readInt();
            unlockTimestamps = new HashMap<>();
            for (int i = 0; i < timestampsSize; i++) {
                unlockTimestamps.put(packetBuffer.readString(32767), packetBuffer.readLong());
            }

            enabled = packetBuffer.readBoolean();
            showTooltips = packetBuffer.readBoolean();
            teamDisplayName = packetBuffer.readString(32767);
        } catch (Exception ignored) {}
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(collectedCounts.size());
        for (Map.Entry<String, Integer> entry : collectedCounts.entrySet()) {
            packetBuffer.writeString(entry.getKey());
            packetBuffer.writeInt(entry.getValue());
        }
        packetBuffer.writeInt(unlockedItems.size());
        for (String item : unlockedItems) {
            packetBuffer.writeString(item);
        }
        packetBuffer.writeInt(unlockTimestamps.size());
        for (Map.Entry<String, Long> entry : unlockTimestamps.entrySet()) {
            packetBuffer.writeString(entry.getKey());
            packetBuffer.writeLong(entry.getValue());
        }
        packetBuffer.writeBoolean(enabled);
        packetBuffer.writeBoolean(showTooltips);
        packetBuffer.writeString(teamDisplayName);
    }

    public static class Handler implements IMessageHandler<SyncJourneyDataPacket, IMessage> {
        @Override
        public IMessage onMessage(SyncJourneyDataPacket message, MessageContext ctx) {
            runClient(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void runClient(SyncJourneyDataPacket message) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                net.minecraft.client.entity.EntityPlayerSP player = net.minecraft.client.Minecraft.getMinecraft().player;
                if (player != null) {
                    IJourneyData data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
                    if (data != null) {
                        data.updateFromSync(message.collectedCounts, message.unlockedItems, message.unlockTimestamps, message.enabled, message.showTooltips, message.teamDisplayName);
                        celebrateNewUnlocks(player, data.getAndClearNewlyUnlocked());
                    }
                }
            });
        }

        /**
         * §11 Visual Polish: action-bar message on threshold crossing, driven
         * purely by client-side diffing (see JourneyData.updateFromSync) - no dedicated
         * "newly_unlocked" packet field needed. A full graphical toast (with custom textures) is
         * deliberately out of scope for this pass - there's no art-asset pipeline in play here,
         * so this uses the same action-bar message style the rest of the mod already uses. No
         * unicode symbols in the message text (matches the mojibake fix elsewhere in this mod).
         */
        @SideOnly(Side.CLIENT)
        private void celebrateNewUnlocks(net.minecraft.client.entity.EntityPlayerSP player, Set<String> newlyUnlocked) {
            if (newlyUnlocked.isEmpty()) return;

            if (newlyUnlocked.size() == 1) {
                String key = newlyUnlocked.iterator().next();
                net.minecraft.item.ItemStack stack = com.aryangpt007.journeymode.data.JourneyData.itemStackFromKey(key);
                String name = stack.isEmpty() ? key : stack.getDisplayName();
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§6Unlocked: " + name + "!"), true);
            } else {
                player.sendStatusMessage(new net.minecraft.util.text.TextComponentString("§6Unlocked " + newlyUnlocked.size() + " items!"), true);
            }
        }
    }
}
