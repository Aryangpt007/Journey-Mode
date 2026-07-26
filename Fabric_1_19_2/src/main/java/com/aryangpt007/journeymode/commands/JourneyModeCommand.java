package com.aryangpt007.journeymode.commands;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.UUID;

/**
 * All /journeymode subcommands: player enable/disable/reset (pre-existing), plus the OP-only
 * balance commands from the 1.8.0 roadmap (all, threshold, grant/revoke) and reloadconfig.
 * Ported to Fabric 1.19.2 - GameProfileArgument/ItemArgument/Brigadier tree are shared vanilla
 * code with Forge, so only the data-access layer (GlobalDataHandler instead of a capability)
 * and the config-sync push (FabricNetworkHandler instead of a Forge SimpleChannel) differ.
 */
public class JourneyModeCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(
            Commands.literal("journeymode")
                .then(Commands.literal("on")
                    .executes(context -> setJourneyMode(context.getSource(), true)))
                .then(Commands.literal("off")
                    .executes(context -> setJourneyMode(context.getSource(), false)))
                .then(Commands.literal("reset")
                    .executes(context -> resetJourneyMode(context.getSource())))
                .then(Commands.literal("tooltips")
                    .then(Commands.literal("on")
                        .executes(context -> setTooltips(context.getSource(), true)))
                    .then(Commands.literal("off")
                        .executes(context -> setTooltips(context.getSource(), false))))
                .then(Commands.literal("team")
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(context -> teamCreate(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")))))
                    .then(Commands.literal("join")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(context -> teamJoin(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name")))))
                    .then(Commands.literal("leave")
                        .executes(context -> teamLeave(context.getSource())))
                    .then(Commands.literal("info")
                        .executes(context -> teamInfo(context.getSource())))
                    .then(Commands.literal("kick")
                        .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(context -> teamKick(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player")))))
                    .then(Commands.literal("transfer")
                        .then(Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(context -> teamTransfer(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player"))))))
                .then(Commands.literal("reloadconfig")
                    .requires(source -> source.hasPermission(2))
                    .executes(JourneyModeCommand::reloadConfig))
                .then(Commands.literal("all")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("reset")
                        .executes(JourneyModeCommand::resetDefaultOverride))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(JourneyModeCommand::allWithoutConfirm)
                        .then(Commands.literal("confirm")
                            .executes(JourneyModeCommand::allConfirmed))))
                .then(Commands.literal("threshold")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("hand")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .executes(JourneyModeCommand::setThresholdHand)))
                    .then(Commands.argument("item", ItemArgument.item(buildContext))
                        .then(Commands.literal("remove")
                            .executes(JourneyModeCommand::removeThresholdItem))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .executes(JourneyModeCommand::setThresholdItem))))
                .then(Commands.literal("grant")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.literal("hand")
                            .executes(JourneyModeCommand::grantHand))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                            .executes(JourneyModeCommand::grantItem))))
                .then(Commands.literal("revoke")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.literal("hand")
                            .executes(JourneyModeCommand::revokeHand))
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                            .executes(JourneyModeCommand::revokeItem))))
                .executes(context -> queryJourneyMode(context.getSource()))
        );
    }

    // ---------------------------------------------------------------- pre-existing player commands

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
            source.sendSuccess(JourneyMode.translatable("command.enabled"), false);
        } else {
            source.sendSuccess(JourneyMode.translatable("command.disabled"), false);
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
            source.sendSuccess(JourneyMode.translatable("command.status.enabled"), false);
        } else {
            source.sendSuccess(JourneyMode.translatable("command.status.disabled"), false);
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

        source.sendSuccess(Component.literal("§aYour Journey Mode unlocks and progress have been successfully reset!"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setTooltips(CommandSourceStack source, boolean show) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
        data.setShowTooltips(show);

        // Save to global JSON file in real-time
        GlobalDataHandler.savePlayerUnlocks(player, data);

        // Sync to client
        GlobalDataHandler.syncToClient(player, data);

        source.sendSuccess(Component.literal("Journey Mode tooltips " + (show ? "enabled" : "disabled") + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §1 /journeymode team

    private static int teamCreate(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.createTeam(source.getServer(), player, name);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(Component.literal("Created team '" + name + "'. You're the owner."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamJoin(CommandSourceStack source, String name) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.joinTeam(source.getServer(), player, name);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(Component.literal("Joined team '" + name + "'. Your deposits and unlocks now pool with the team."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamLeave(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.leaveTeam(source.getServer(), player);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(Component.literal("Left your team. You've kept a snapshot of its unlocks; future deposits are personal again."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamInfo(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        var team = com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUUID());
        if (team.isEmpty()) {
            source.sendSuccess(Component.literal("You're not in a team."), false);
            return Command.SINGLE_SUCCESS;
        }
        var t = team.get();
        source.sendSuccess(Component.literal("Team '" + t.getDisplayName() + "' - " + t.getMembers().size() +
            " member(s), " + t.getUnlockedItems().size() + " item(s) unlocked."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamKick(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.kickPlayer(source.getServer(), player, targetName);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(Component.literal("Kicked " + targetName + " from the team."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamTransfer(CommandSourceStack source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.transferOwnership(source.getServer(), player, targetName);
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        source.sendSuccess(Component.literal("Transferred team ownership to " + targetName + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §2 reloadconfig

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        ConfigHandler.reloadAll();
        FabricNetworkHandler.pushConfigToAllPlayers(context.getSource().getServer());
        context.getSource().sendSuccess(Component.literal("Journey Mode config reloaded and re-synced to all online players."), true);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §4 /journeymode all

    private static int allWithoutConfirm(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        context.getSource().sendFailure(Component.literal(
            "This sets the fallback threshold override for EVERY item on the server (per-item overrides still win). " +
            "Re-run as: /journeymode all " + count + " confirm"));
        return 0;
    }

    private static int allConfirmed(CommandContext<CommandSourceStack> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        int cap = ConfigHandler.getMaxThresholdCap();
        if (count > cap) {
            context.getSource().sendFailure(Component.literal("Value " + count + " exceeds max_threshold_cap (" + cap + "). Raise the cap first if this is intentional."));
            return 0;
        }
        ConfigHandler.setDefaultOverride(count);
        FabricNetworkHandler.pushConfigToAllPlayers(context.getSource().getServer());
        context.getSource().sendSuccess(Component.literal(
            "Threshold override set to " + count + " for ALL items (per-item overrides still apply). " +
            "Run '/journeymode all reset' to undo."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetDefaultOverride(CommandContext<CommandSourceStack> context) {
        ConfigHandler.setDefaultOverride(null);
        FabricNetworkHandler.pushConfigToAllPlayers(context.getSource().getServer());
        context.getSource().sendSuccess(Component.literal("Global threshold override removed - back to per-item rules and the recipe calculator."), true);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §5 /journeymode threshold

    private static int setThresholdItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        int count = IntegerArgumentType.getInteger(context, "count");
        return applyThreshold(context.getSource(), itemId(itemInput.getItem()), count);
    }

    private static int setThresholdHand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("threshold hand can only be used by a player (holding the item to target)."));
            return 0;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You must hold an item to use 'threshold hand'."));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        return applyThreshold(source, JourneyDataAttachment.getItemKey(held), count);
    }

    private static int applyThreshold(CommandSourceStack source, String key, int count) {
        int cap = ConfigHandler.getMaxThresholdCap();
        if (count > cap) {
            source.sendFailure(Component.literal("Value " + count + " exceeds max_threshold_cap (" + cap + "). Raise the cap first if this is intentional."));
            return 0;
        }
        ConfigHandler.setExactThreshold(key, count);
        FabricNetworkHandler.pushConfigToAllPlayers(source.getServer());
        source.sendSuccess(Component.literal("Threshold for '" + key + "' set to " + count + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeThresholdItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        String key = itemId(itemInput.getItem());
        boolean removed = ConfigHandler.removeExactThreshold(key);
        FabricNetworkHandler.pushConfigToAllPlayers(context.getSource().getServer());
        if (removed) {
            context.getSource().sendSuccess(Component.literal("Removed threshold override for '" + key + "'."), true);
        } else {
            context.getSource().sendFailure(Component.literal("No threshold override was set for '" + key + "'."));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §6 /journeymode grant|revoke

    private static int grantItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        return applyGrantRevoke(context, itemId(itemInput.getItem()), true);
    }

    private static int grantHand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return handVariantGrantRevoke(context, true);
    }

    private static int revokeItem(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        return applyGrantRevoke(context, itemId(itemInput.getItem()), false);
    }

    private static int revokeHand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return handVariantGrantRevoke(context, false);
    }

    private static int handVariantGrantRevoke(CommandContext<CommandSourceStack> context, boolean grant) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer issuer)) {
            source.sendFailure(Component.literal((grant ? "grant" : "revoke") + " hand can only be used by a player (holding the item to target)."));
            return 0;
        }
        ItemStack held = issuer.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You must hold an item to use '" + (grant ? "grant" : "revoke") + " hand'."));
            return 0;
        }
        return applyGrantRevoke(context, JourneyDataAttachment.getItemKey(held), grant);
    }

    private static int applyGrantRevoke(CommandContext<CommandSourceStack> context, String key, boolean grant) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        MinecraftServer server = source.getServer();
        int affected = 0;

        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                JourneyDataAttachment data = GlobalDataHandler.getPlayerData(online);
                if (grant) data.grant(key); else data.revoke(key);
                GlobalDataHandler.savePlayerUnlocks(online, data);
                GlobalDataHandler.syncToClient(online, data);
                affected++;
            } else {
                GlobalDataHandler.mutateOfflinePlayerData(uuid, data -> {
                    if (grant) data.grant(key); else data.revoke(key);
                });
                affected++;
            }
        }

        String verb = grant ? "Granted" : "Revoked";
        source.sendSuccess(Component.literal(verb + " '" + key + "' for " + affected + " player(s)."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static String itemId(Item item) {
        return Registry.ITEM.getKey(item).toString();
    }
}
