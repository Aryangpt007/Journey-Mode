package com.aryangpt007.journeymode.fabric.commands;

import com.aryangpt007.journeymode.fabric.JourneyModeFabric;
import com.aryangpt007.journeymode.core.JourneyData;
import com.aryangpt007.journeymode.fabric.platform.FabricDataHandler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command to enable or disable Journey Mode for individual players
 * Usage: /journeymode [on|off|status]
 */
public class JourneyModeCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("journeymode")
                .then(Commands.literal("on")
                    .executes(context -> setJourneyMode(context.getSource(), true)))
                .then(Commands.literal("off")
                    .executes(context -> setJourneyMode(context.getSource(), false)))
                .then(Commands.literal("status")
                    .executes(context -> queryJourneyMode(context.getSource())))
                .executes(context -> queryJourneyMode(context.getSource()))
        );
    }
    
    private static int setJourneyMode(CommandSourceStack source, boolean enabled) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        
        JourneyData data = FabricDataHandler.getJourneyData(player);
        if (data == null) {
            source.sendFailure(Component.literal("Failed to access Journey Mode data"));
            return 0;
        }
        
        data.setEnabled(enabled);
        FabricDataHandler.saveJourneyData(player, data);
        
        if (enabled) {
            source.sendSuccess(() -> JourneyModeFabric.translatable("command.enabled"), false);
        } else {
            source.sendSuccess(() -> JourneyModeFabric.translatable("command.disabled"), false);
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private static int queryJourneyMode(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        
        JourneyData data = FabricDataHandler.getJourneyData(player);
        if (data == null) {
            source.sendFailure(Component.literal("Failed to access Journey Mode data"));
            return 0;
        }
        
        boolean enabled = data.isEnabled();
        if (enabled) {
            source.sendSuccess(() -> JourneyModeFabric.translatable("command.status.enabled"), false);
        } else {
            source.sendSuccess(() -> JourneyModeFabric.translatable("command.status.disabled"), false);
        }
        
        return Command.SINGLE_SUCCESS;
    }
}
