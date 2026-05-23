package com.aryangpt007.journeymode.commands;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class JourneyModeCommand extends CommandBase {

    @Override
    public String getName() {
        return "journeymode";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/journeymode [on|off|reset]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // Accessible to all players by default
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("This command can only be used by players"));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;
        IJourneyData data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
        if (data == null) {
            sender.sendMessage(new TextComponentString("Failed to access Journey Mode data"));
            return;
        }

        if (args.length == 0) {
            // Query status
            boolean enabled = data.isEnabled();
            if (enabled) {
                player.sendMessage(JourneyMode.translatable("command.status.enabled"));
            } else {
                player.sendMessage(JourneyMode.translatable("command.status.disabled"));
            }
        } else {
            String subCommand = args[0].toLowerCase();
            if ("on".equals(subCommand)) {
                data.setEnabled(true);
                GlobalDataHandler.savePlayerUnlocks(player, data);
                GlobalDataHandler.syncToClient(player, data);
                player.sendMessage(JourneyMode.translatable("command.enabled"));
            } else if ("off".equals(subCommand)) {
                data.setEnabled(false);
                GlobalDataHandler.savePlayerUnlocks(player, data);
                GlobalDataHandler.syncToClient(player, data);
                player.sendMessage(JourneyMode.translatable("command.disabled"));
            } else if ("reset".equals(subCommand)) {
                data.reset();
                GlobalDataHandler.savePlayerUnlocks(player, data);
                GlobalDataHandler.syncToClient(player, data);
                player.sendMessage(new TextComponentString("§aYour Journey Mode unlocks and progress have been successfully reset!"));
            } else {
                player.sendMessage(new TextComponentString("§cInvalid arguments. Usage: " + getUsage(sender)));
            }
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "reset");
        }
        return Collections.emptyList();
    }
}
