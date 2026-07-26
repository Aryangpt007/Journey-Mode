package com.aryangpt007.journeymode.network.packets;

import com.aryangpt007.journeymode.menu.JourneyModeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * §8 Deposit All. No item payload - the server reads the player's own inventory, same trust
 * model as SubmitDepositPacket. includeHotbar mirrors whether the player shift-clicked the
 * button client-side.
 */
public class DepositAllPacket {
    private final boolean includeHotbar;

    public DepositAllPacket(boolean includeHotbar) {
        this.includeHotbar = includeHotbar;
    }

    public static void encode(DepositAllPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.includeHotbar);
    }

    public static DepositAllPacket decode(FriendlyByteBuf buf) {
        return new DepositAllPacket(buf.readBoolean());
    }

    public static void handle(DepositAllPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof JourneyModeMenu menu) {
                menu.processDepositAll(msg.includeHotbar);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
