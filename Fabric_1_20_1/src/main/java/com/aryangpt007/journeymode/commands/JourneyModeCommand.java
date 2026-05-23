package com.aryangpt007.journeymode.commands;

import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command to enable or disable Journey Mode for individual players.
 * Ported to Fabric 1.20.1.
 */
public class JourneyModeCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("journeymode")
                .then(Commands.literal("on")
                    .executes(context -> setJourneyMode(context.getSource(), true)))
                .then(Commands.literal("off")
                    .executes(context -> setJourneyMode(context.getSource(), false)))
                .then(Commands.literal("reset")
                    .executes(context -> resetJourneyMode(context.getSource())))
                .executes(context -> queryJourneyMode(context.getSource()))
        );
    }
    
    private static int setJourneyMode(CommandSourceStack source, boolean enabled) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        
        JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
        data.setEnabled(enabled);
        
        // Save to global JSON file in real-time
        GlobalDataHandler.savePlayerUnlocks(player, data);
        
        // Sync to client
        GlobalDataHandler.syncToClient(player, data);
        
        if (enabled) {
            source.sendSuccess(() -> Component.translatable("journeymode.command.enabled"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("journeymode.command.disabled"), false);
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private static int queryJourneyMode(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        
        JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
        boolean enabled = data.isEnabled();
        if (enabled) {
            source.sendSuccess(() -> Component.translatable("journeymode.command.status.enabled"), false);
        } else {
            source.sendSuccess(() -> Component.translatable("journeymode.command.status.disabled"), false);
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int resetJourneyMode(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        
        JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
        data.reset();
        
        // Save to global JSON file in real-time
        GlobalDataHandler.savePlayerUnlocks(player, data);
        
        // Sync to client
        GlobalDataHandler.syncToClient(player, data);
        
        source.sendSuccess(() -> Component.literal("§aYour Journey Mode unlocks and progress have been successfully reset!"), false);
        return Command.SINGLE_SUCCESS;
    }
}
