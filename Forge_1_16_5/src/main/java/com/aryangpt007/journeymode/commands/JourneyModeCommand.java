package com.aryangpt007.journeymode.commands;

import net.minecraft.util.text.StringTextComponent;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;

/**
 * Command to enable or disable Journey Mode for individual players.
 * Ported to Forge 1.20.1.
 */
public class JourneyModeCommand {
    
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
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
    
    private static int setJourneyMode(CommandSource source, boolean enabled) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        
        JourneyDataAttachment data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
        if (data == null) {
            source.sendFailure(new StringTextComponent("Failed to access Journey Mode data"));
            return 0;
        }
        
        data.setEnabled(enabled);
        
        // Save to global JSON file in real-time
        com.aryangpt007.journeymode.data.GlobalDataHandler.savePlayerUnlocks(player, data);
        
        // Sync to client
        com.aryangpt007.journeymode.data.GlobalDataHandler.syncToClient(player, data);
        
        if (enabled) {
            source.sendSuccess(JourneyMode.translatable("command.enabled"), false);
        } else {
            source.sendSuccess(JourneyMode.translatable("command.disabled"), false);
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private static int queryJourneyMode(CommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        
        JourneyDataAttachment data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
        if (data == null) {
            source.sendFailure(new StringTextComponent("Failed to access Journey Mode data"));
            return 0;
        }
        
        boolean enabled = data.isEnabled();
        if (enabled) {
            source.sendSuccess(JourneyMode.translatable("command.status.enabled"), false);
        } else {
            source.sendSuccess(JourneyMode.translatable("command.status.disabled"), false);
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int resetJourneyMode(CommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        
        JourneyDataAttachment data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
        if (data == null) {
            source.sendFailure(new StringTextComponent("Failed to access Journey Mode data"));
            return 0;
        }
        
        data.reset();
        
        // Save to global JSON file in real-time
        com.aryangpt007.journeymode.data.GlobalDataHandler.savePlayerUnlocks(player, data);
        
        // Sync to client
        com.aryangpt007.journeymode.data.GlobalDataHandler.syncToClient(player, data);
        
        source.sendSuccess(new StringTextComponent("§aYour Journey Mode unlocks and progress have been successfully reset!"), false);
        return Command.SINGLE_SUCCESS;
    }
}
