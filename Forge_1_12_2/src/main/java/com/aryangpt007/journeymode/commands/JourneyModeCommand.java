package com.aryangpt007.journeymode.commands;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.IJourneyData;
import com.aryangpt007.journeymode.data.JourneyData;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.aryangpt007.journeymode.network.ConfigSyncHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * All /journeymode subcommands: player enable/disable/reset (pre-existing), plus the OP-only
 * balance commands from the 1.8.0 roadmap (all, threshold, grant/revoke) and reloadconfig.
 *
 * 1.12.2 predates Brigadier entirely - this uses the classic ICommand/CommandBase style with
 * manual String[] argument parsing, args[0] as the subcommand literal (matching this file's own
 * pre-existing on/off/reset convention), and plain string + registry-lookup item validation
 * (Item.REGISTRY.getObject) instead of a typed item-argument type, since 1.12.2 has no equivalent.
 */
public class JourneyModeCommand extends CommandBase {

    private static final int OP_PERMISSION_LEVEL = 2;

    @Override
    public String getName() {
        return "journeymode";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/journeymode [on|off|reset|tooltips <on|off>|team <create|join|leave|info|kick|transfer>|" +
            "reloadconfig|all <count> confirm|all reset|" +
            "threshold <item|hand> <count>|threshold <item> remove|grant <player> <item|hand>|revoke <player> <item|hand>]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        // Player-facing subcommands (on/off/reset, no-arg status) stay open to everyone - the
        // OP-only subcommands below check permission level individually via requireOp().
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            queryStatus(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "on":
                setEnabled(sender, true);
                break;
            case "off":
                setEnabled(sender, false);
                break;
            case "reset":
                resetPlayer(sender);
                break;
            case "tooltips":
                setTooltips(sender, args);
                break;
            case "team":
                handleTeam(server, sender, args);
                break;
            case "reloadconfig":
                requireOp(sender);
                reloadConfig(server, sender);
                break;
            case "all":
                requireOp(sender);
                handleAll(server, sender, args);
                break;
            case "threshold":
                requireOp(sender);
                handleThreshold(server, sender, args);
                break;
            case "grant":
                requireOp(sender);
                handleGrantRevoke(server, sender, args, true);
                break;
            case "revoke":
                requireOp(sender);
                handleGrantRevoke(server, sender, args, false);
                break;
            default:
                sender.sendMessage(new TextComponentString("§cInvalid arguments. Usage: " + getUsage(sender)));
        }
    }

    // ---------------------------------------------------------------- pre-existing player commands

    private static EntityPlayerMP asPlayer(ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new CommandException("This command can only be used by players");
        }
        return (EntityPlayerMP) sender;
    }

    private static void requireOp(ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(OP_PERMISSION_LEVEL, "journeymode")) {
            throw new CommandException("You need permission level " + OP_PERMISSION_LEVEL + " to use this subcommand.");
        }
    }

    private static IJourneyData getData(EntityPlayerMP player) throws CommandException {
        IJourneyData data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
        if (data == null) {
            throw new CommandException("Failed to access Journey Mode data");
        }
        return data;
    }

    private void queryStatus(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        IJourneyData data = getData(player);
        if (data.isEnabled()) {
            player.sendMessage(JourneyMode.translatable("command.status.enabled"));
        } else {
            player.sendMessage(JourneyMode.translatable("command.status.disabled"));
        }
    }

    private void setEnabled(ICommandSender sender, boolean enabled) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        IJourneyData data = getData(player);
        data.setEnabled(enabled);
        GlobalDataHandler.savePlayerUnlocks(player, data);
        GlobalDataHandler.syncToClient(player, data);
        player.sendMessage(enabled ? JourneyMode.translatable("command.enabled") : JourneyMode.translatable("command.disabled"));
    }

    private void resetPlayer(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        IJourneyData data = getData(player);
        data.reset();
        GlobalDataHandler.savePlayerUnlocks(player, data);
        GlobalDataHandler.syncToClient(player, data);
        player.sendMessage(new TextComponentString("§aYour Journey Mode unlocks and progress have been successfully reset!"));
    }

    /**
     * §3 /journeymode tooltips on|off - per-player display preference (IJourneyData#showTooltips),
     * same player-facing, permission-free pattern as on/off/reset above.
     */
    private void setTooltips(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || !("on".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1]))) {
            throw new WrongUsageException("/journeymode tooltips <on|off>");
        }
        boolean show = "on".equalsIgnoreCase(args[1]);

        EntityPlayerMP player = asPlayer(sender);
        IJourneyData data = getData(player);
        data.setShowTooltips(show);
        GlobalDataHandler.savePlayerUnlocks(player, data);
        GlobalDataHandler.syncToClient(player, data);
        player.sendMessage(new TextComponentString("Journey Mode tooltips " + (show ? "enabled" : "disabled") + "."));
    }

    // ---------------------------------------------------------------- §1 /journeymode team

    /**
     * No permission-level requirement on any team subcommand (matches the resolved design
     * decision) - unlike all/threshold/grant/revoke, this is a player-facing feature, not a
     * server-owner balance tool.
     */
    private void handleTeam(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/journeymode team <create|join|leave|info|kick|transfer> [name]");
        }
        String teamSub = args[1].toLowerCase();
        switch (teamSub) {
            case "create":
                if (args.length < 3) throw new WrongUsageException("/journeymode team create <name>");
                teamCreate(sender, args[2]);
                break;
            case "join":
                if (args.length < 3) throw new WrongUsageException("/journeymode team join <name>");
                teamJoin(sender, args[2]);
                break;
            case "leave":
                teamLeave(server, sender);
                break;
            case "info":
                teamInfo(sender);
                break;
            case "kick":
                if (args.length < 3) throw new WrongUsageException("/journeymode team kick <player>");
                teamKick(server, sender, args[2]);
                break;
            case "transfer":
                if (args.length < 3) throw new WrongUsageException("/journeymode team transfer <player>");
                teamTransfer(server, sender, args[2]);
                break;
            default:
                throw new WrongUsageException("/journeymode team <create|join|leave|info|kick|transfer> [name]");
        }
    }

    private void teamCreate(ICommandSender sender, String name) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.createTeam(player.getServer(), player, name);
        if (error != null) {
            throw new CommandException(error);
        }
        sender.sendMessage(new TextComponentString("Created team '" + name + "'. You're the owner."));
    }

    private void teamJoin(ICommandSender sender, String name) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.joinTeam(player.getServer(), player, name);
        if (error != null) {
            throw new CommandException(error);
        }
        sender.sendMessage(new TextComponentString("Joined team '" + name + "'. Your deposits and unlocks now pool with the team."));
    }

    private void teamLeave(MinecraftServer server, ICommandSender sender) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.leaveTeam(server, player);
        if (error != null) {
            throw new CommandException(error);
        }
        sender.sendMessage(new TextComponentString("Left your team. You've kept a snapshot of its unlocks; future deposits are personal again."));
    }

    private void teamInfo(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        com.aryangpt007.journeymode.data.TeamData team =
            com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUniqueID());
        if (team == null) {
            sender.sendMessage(new TextComponentString("You're not in a team."));
            return;
        }
        sender.sendMessage(new TextComponentString("Team '" + team.getDisplayName() + "' - " + team.getMembers().size() +
            " member(s), " + team.getUnlockedItems().size() + " item(s) unlocked."));
    }

    private void teamKick(MinecraftServer server, ICommandSender sender, String targetName) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.kickPlayer(server, player, targetName);
        if (error != null) {
            throw new CommandException(error);
        }
        sender.sendMessage(new TextComponentString("Kicked " + targetName + " from the team."));
    }

    private void teamTransfer(MinecraftServer server, ICommandSender sender, String targetName) throws CommandException {
        EntityPlayerMP player = asPlayer(sender);
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.transferOwnership(server, player, targetName);
        if (error != null) {
            throw new CommandException(error);
        }
        sender.sendMessage(new TextComponentString("Transferred team ownership to " + targetName + "."));
    }

    // ---------------------------------------------------------------- reloadconfig

    private void reloadConfig(MinecraftServer server, ICommandSender sender) {
        ConfigHandler.reloadAll();
        ConfigSyncHelper.pushToAllPlayers(server);
        sender.sendMessage(new TextComponentString("Journey Mode config reloaded and re-synced to all online players."));
    }

    // ---------------------------------------------------------------- /journeymode all

    private void handleAll(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/journeymode all <count> confirm | /journeymode all reset");
        }

        if ("reset".equalsIgnoreCase(args[1]) || "default".equalsIgnoreCase(args[1])) {
            ConfigHandler.setDefaultOverride(null);
            ConfigSyncHelper.pushToAllPlayers(server);
            sender.sendMessage(new TextComponentString("Global threshold override removed - back to per-item rules and the recipe calculator."));
            return;
        }

        int count = parseCount(args[1]);

        if (args.length < 3 || !"confirm".equalsIgnoreCase(args[2])) {
            sender.sendMessage(new TextComponentString(
                "§eThis sets the fallback threshold override for EVERY item on the server (per-item overrides still win). " +
                "Re-run as: /journeymode all " + count + " confirm"));
            return;
        }

        int cap = ConfigHandler.getMaxThresholdCap();
        if (count > cap) {
            throw new CommandException("Value " + count + " exceeds max_threshold_cap (" + cap + "). Raise the cap first if this is intentional.");
        }

        ConfigHandler.setDefaultOverride(count);
        ConfigSyncHelper.pushToAllPlayers(server);
        sender.sendMessage(new TextComponentString(
            "Threshold override set to " + count + " for ALL items (per-item overrides still apply). " +
            "Run '/journeymode all reset' to undo."));
    }

    // ---------------------------------------------------------------- /journeymode threshold

    private void handleThreshold(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/journeymode threshold <item> <count>|remove | /journeymode threshold hand <count>");
        }

        if ("hand".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                throw new WrongUsageException("/journeymode threshold hand <count>");
            }
            EntityPlayerMP player = asPlayer(sender);
            ItemStack held = player.getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new CommandException("You must hold an item to use 'threshold hand'.");
            }
            int count = parseCount(args[2]);
            applyThreshold(server, sender, JourneyData.getItemKey(held), count);
            return;
        }

        Item item = resolveItem(args[1]);
        String itemId = item.getRegistryName().toString();

        if (args.length >= 3 && "remove".equalsIgnoreCase(args[2])) {
            boolean removed = ConfigHandler.removeExactThreshold(itemId);
            ConfigSyncHelper.pushToAllPlayers(server);
            if (removed) {
                sender.sendMessage(new TextComponentString("Removed threshold override for '" + itemId + "'."));
            } else {
                sender.sendMessage(new TextComponentString("§cNo threshold override was set for '" + itemId + "'."));
            }
            return;
        }

        if (args.length < 3) {
            throw new WrongUsageException("/journeymode threshold <item> <count>|remove");
        }
        int count = parseCount(args[2]);
        applyThreshold(server, sender, itemId, count);
    }

    private void applyThreshold(MinecraftServer server, ICommandSender sender, String key, int count) throws CommandException {
        int cap = ConfigHandler.getMaxThresholdCap();
        if (count > cap) {
            throw new CommandException("Value " + count + " exceeds max_threshold_cap (" + cap + "). Raise the cap first if this is intentional.");
        }
        ConfigHandler.setExactThreshold(key, count);
        ConfigSyncHelper.pushToAllPlayers(server);
        sender.sendMessage(new TextComponentString("Threshold for '" + key + "' set to " + count + "."));
    }

    // ---------------------------------------------------------------- /journeymode grant|revoke

    private void handleGrantRevoke(MinecraftServer server, ICommandSender sender, String[] args, final boolean grant) throws CommandException {
        String verb = grant ? "grant" : "revoke";
        if (args.length < 3) {
            throw new WrongUsageException("/journeymode " + verb + " <player> <item>|hand");
        }

        String playerName = args[1];
        UUID uuid = resolvePlayerUuid(server, playerName);
        if (uuid == null) {
            throw new CommandException("Could not resolve a player profile for '" + playerName + "'.");
        }

        final String key;
        if ("hand".equalsIgnoreCase(args[2])) {
            if (!(sender instanceof EntityPlayerMP)) {
                throw new CommandException(verb + " hand can only be used by a player (holding the item to target).");
            }
            ItemStack held = ((EntityPlayerMP) sender).getHeldItemMainhand();
            if (held.isEmpty()) {
                throw new CommandException("You must hold an item to use '" + verb + " hand'.");
            }
            key = JourneyData.getItemKey(held);
        } else {
            Item item = resolveItem(args[2]);
            key = item.getRegistryName().toString();
        }

        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(uuid);
        if (online != null) {
            IJourneyData data = getData(online);
            if (grant) {
                data.grant(key);
            } else {
                data.revoke(key);
            }
            GlobalDataHandler.savePlayerUnlocks(online, data);
            GlobalDataHandler.syncToClient(online, data);
        } else {
            // Offline player: mutate their saved entry by UUID through the single synchronized
            // writer - never a second file handle (per the project's "one writer" rule).
            GlobalDataHandler.mutateOfflinePlayerData(uuid, new GlobalDataHandler.JourneyDataMutator() {
                @Override
                public void mutate(IJourneyData data) {
                    if (grant) {
                        data.grant(key);
                    } else {
                        data.revoke(key);
                    }
                }
            });
        }

        sender.sendMessage(new TextComponentString((grant ? "Granted" : "Revoked") + " '" + key + "' for " + playerName + "."));
    }

    // ---------------------------------------------------------------- helpers

    private static Item resolveItem(String itemArg) throws CommandException {
        Item item = null;
        try {
            item = Item.REGISTRY.getObject(new ResourceLocation(itemArg));
        } catch (Exception ignored) {
            // fall through to the null check below
        }
        if (item == null || item.getRegistryName() == null) {
            throw new CommandException("Unknown item id: " + itemArg);
        }
        return item;
    }

    private static int parseCount(String value) throws CommandException {
        int count;
        try {
            count = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new CommandException("'" + value + "' is not a valid number.");
        }
        if (count <= 0) {
            throw new CommandException("Count must be greater than 0.");
        }
        return count;
    }

    /** Resolves online OR offline players by username (works via PlayerProfileCache/usercache.json). */
    @Nullable
    private static UUID resolvePlayerUuid(MinecraftServer server, String playerName) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(playerName);
        if (online != null) {
            return online.getUniqueID();
        }
        GameProfile profile = server.getPlayerProfileCache().getGameProfileForUsername(playerName);
        return profile != null ? profile.getId() : null;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "reset", "tooltips", "team", "reloadconfig", "all", "threshold", "grant", "revoke");
        }
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ("all".equals(subCommand)) {
                return getListOfStringsMatchingLastWord(args, "reset");
            }
            if ("tooltips".equals(subCommand)) {
                return getListOfStringsMatchingLastWord(args, "on", "off");
            }
            if ("team".equals(subCommand)) {
                return getListOfStringsMatchingLastWord(args, "create", "join", "leave", "info", "kick", "transfer");
            }
            if ("threshold".equals(subCommand)) {
                return getListOfStringsMatchingLastWord(args, "hand");
            }
            if ("grant".equals(subCommand) || "revoke".equals(subCommand)) {
                return getListOfStringsMatchingLastWord(args, server.getPlayerList().getOnlinePlayerNames());
            }
        }
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if ("threshold".equals(subCommand) && !"hand".equalsIgnoreCase(args[1])) {
                return getListOfStringsMatchingLastWord(args, "remove");
            }
            if ("grant".equals(subCommand) || "revoke".equals(subCommand)) {
                return getListOfStringsMatchingLastWord(args, "hand");
            }
            if ("team".equals(subCommand) && ("kick".equalsIgnoreCase(args[1]) || "transfer".equalsIgnoreCase(args[1]))) {
                return getListOfStringsMatchingLastWord(args, server.getPlayerList().getOnlinePlayerNames());
            }
        }
        return Collections.emptyList();
    }
}
