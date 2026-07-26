package com.aryangpt007.journeymode.commands;

import net.minecraft.util.text.StringTextComponent;

import com.aryangpt007.journeymode.JourneyMode;
import com.aryangpt007.journeymode.config.ConfigHandler;
import com.aryangpt007.journeymode.data.GlobalDataHandler;
import com.aryangpt007.journeymode.data.JourneyDataAttachment;
import com.aryangpt007.journeymode.data.JourneyDataCapabilityProvider;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.command.arguments.ItemArgument;
import net.minecraft.command.arguments.ItemInput;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.UUID;

/**
 * All /journeymode subcommands: player enable/disable/reset (pre-existing), plus the OP-only
 * balance commands from the 1.8.0 roadmap (all, threshold, grant/revoke) and reloadconfig.
 *
 * 1.16.5 note: unlike 1.20.1's ItemArgument.item(CommandBuildContext), this version's
 * ItemArgument.item() takes no build-context argument (that parameter was added in a later
 * version) - so register() here has no CommandBuildContext parameter either.
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
                    .then(Commands.argument("item", ItemArgument.item())
                        .then(Commands.literal("remove")
                            .executes(JourneyModeCommand::removeThresholdItem))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .executes(JourneyModeCommand::setThresholdItem))))
                .then(Commands.literal("grant")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.literal("hand")
                            .executes(JourneyModeCommand::grantHand))
                        .then(Commands.argument("item", ItemArgument.item())
                            .executes(JourneyModeCommand::grantItem))))
                .then(Commands.literal("revoke")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.literal("hand")
                            .executes(JourneyModeCommand::revokeHand))
                        .then(Commands.argument("item", ItemArgument.item())
                            .executes(JourneyModeCommand::revokeItem))))
                .executes(context -> queryJourneyMode(context.getSource()))
        );
    }

    // ---------------------------------------------------------------- pre-existing player commands

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

    private static int setTooltips(CommandSource source, boolean show) {
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

        data.setShowTooltips(show);
        GlobalDataHandler.savePlayerUnlocks(player, data);
        GlobalDataHandler.syncToClient(player, data);
        source.sendSuccess(new StringTextComponent("Journey Mode tooltips " + (show ? "enabled" : "disabled") + "."), false);
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

        GlobalDataHandler.savePlayerUnlocks(player, data);
        GlobalDataHandler.syncToClient(player, data);

        source.sendSuccess(new StringTextComponent("§aYour Journey Mode unlocks and progress have been successfully reset!"), false);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §1 /journeymode team

    private static int teamCreate(CommandSource source, String name) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.createTeam(source.getServer(), player, name);
        if (error != null) {
            source.sendFailure(new StringTextComponent(error));
            return 0;
        }
        source.sendSuccess(new StringTextComponent("Created team '" + name + "'. You're the owner."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamJoin(CommandSource source, String name) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.joinTeam(source.getServer(), player, name);
        if (error != null) {
            source.sendFailure(new StringTextComponent(error));
            return 0;
        }
        source.sendSuccess(new StringTextComponent("Joined team '" + name + "'. Your deposits and unlocks now pool with the team."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamLeave(CommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.leaveTeam(source.getServer(), player);
        if (error != null) {
            source.sendFailure(new StringTextComponent(error));
            return 0;
        }
        source.sendSuccess(new StringTextComponent("Left your team. You've kept a snapshot of its unlocks; future deposits are personal again."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamInfo(CommandSource source) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        java.util.Optional<com.aryangpt007.journeymode.data.TeamData> team =
            com.aryangpt007.journeymode.data.TeamDataHandler.getTeamForPlayer(player.getUUID());
        if (!team.isPresent()) {
            source.sendSuccess(new StringTextComponent("You're not in a team."), false);
            return Command.SINGLE_SUCCESS;
        }
        com.aryangpt007.journeymode.data.TeamData t = team.get();
        source.sendSuccess(new StringTextComponent("Team '" + t.getDisplayName() + "' - " + t.getMembers().size() +
            " member(s), " + t.getUnlockedItems().size() + " item(s) unlocked."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamKick(CommandSource source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.kickPlayer(source.getServer(), player, targetName);
        if (error != null) {
            source.sendFailure(new StringTextComponent(error));
            return 0;
        }
        source.sendSuccess(new StringTextComponent("Kicked " + targetName + " from the team."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int teamTransfer(CommandSource source, String targetName) {
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("This command can only be used by players"));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        String error = com.aryangpt007.journeymode.data.TeamDataHandler.transferOwnership(source.getServer(), player, targetName);
        if (error != null) {
            source.sendFailure(new StringTextComponent(error));
            return 0;
        }
        source.sendSuccess(new StringTextComponent("Transferred team ownership to " + targetName + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §2 reloadconfig

    private static int reloadConfig(CommandContext<CommandSource> context) {
        ConfigHandler.reloadAll();
        com.aryangpt007.journeymode.network.ConfigSyncHelper.pushToAllPlayers(context.getSource().getServer());
        context.getSource().sendSuccess(new StringTextComponent("Journey Mode config reloaded and re-synced to all online players."), true);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §4 /journeymode all

    private static int allWithoutConfirm(CommandContext<CommandSource> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        context.getSource().sendFailure(new StringTextComponent(
            "This sets the fallback threshold override for EVERY item on the server (per-item overrides still win). " +
            "Re-run as: /journeymode all " + count + " confirm"));
        return 0;
    }

    private static int allConfirmed(CommandContext<CommandSource> context) {
        int count = IntegerArgumentType.getInteger(context, "count");
        int cap = ConfigHandler.getMaxThresholdCap();
        if (count > cap) {
            context.getSource().sendFailure(new StringTextComponent("Value " + count + " exceeds max_threshold_cap (" + cap + "). Raise the cap first if this is intentional."));
            return 0;
        }
        ConfigHandler.setDefaultOverride(count);
        com.aryangpt007.journeymode.network.ConfigSyncHelper.pushToAllPlayers(context.getSource().getServer());
        context.getSource().sendSuccess(new StringTextComponent(
            "Threshold override set to " + count + " for ALL items (per-item overrides still apply). " +
            "Run '/journeymode all reset' to undo."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetDefaultOverride(CommandContext<CommandSource> context) {
        ConfigHandler.setDefaultOverride(null);
        com.aryangpt007.journeymode.network.ConfigSyncHelper.pushToAllPlayers(context.getSource().getServer());
        context.getSource().sendSuccess(new StringTextComponent("Global threshold override removed - back to per-item rules and the recipe calculator."), true);
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §5 /journeymode threshold

    private static int setThresholdItem(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        int count = IntegerArgumentType.getInteger(context, "count");
        return applyThreshold(context.getSource(), itemId(itemInput.getItem()), count);
    }

    private static int setThresholdHand(CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent("threshold hand can only be used by a player (holding the item to target)."));
            return 0;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(new StringTextComponent("You must hold an item to use 'threshold hand'."));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        return applyThreshold(source, JourneyDataAttachment.getItemKey(held), count);
    }

    private static int applyThreshold(CommandSource source, String key, int count) {
        int cap = ConfigHandler.getMaxThresholdCap();
        if (count > cap) {
            source.sendFailure(new StringTextComponent("Value " + count + " exceeds max_threshold_cap (" + cap + "). Raise the cap first if this is intentional."));
            return 0;
        }
        ConfigHandler.setExactThreshold(key, count);
        com.aryangpt007.journeymode.network.ConfigSyncHelper.pushToAllPlayers(source.getServer());
        source.sendSuccess(new StringTextComponent("Threshold for '" + key + "' set to " + count + "."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int removeThresholdItem(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        String key = itemId(itemInput.getItem());
        boolean removed = ConfigHandler.removeExactThreshold(key);
        com.aryangpt007.journeymode.network.ConfigSyncHelper.pushToAllPlayers(context.getSource().getServer());
        if (removed) {
            context.getSource().sendSuccess(new StringTextComponent("Removed threshold override for '" + key + "'."), true);
        } else {
            context.getSource().sendFailure(new StringTextComponent("No threshold override was set for '" + key + "'."));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- §6 /journeymode grant|revoke

    private static int grantItem(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        return applyGrantRevoke(context, itemId(itemInput.getItem()), true);
    }

    private static int grantHand(CommandContext<CommandSource> context) throws CommandSyntaxException {
        return handVariantGrantRevoke(context, true);
    }

    private static int revokeItem(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        return applyGrantRevoke(context, itemId(itemInput.getItem()), false);
    }

    private static int revokeHand(CommandContext<CommandSource> context) throws CommandSyntaxException {
        return handVariantGrantRevoke(context, false);
    }

    private static int handVariantGrantRevoke(CommandContext<CommandSource> context, boolean grant) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayerEntity)) {
            source.sendFailure(new StringTextComponent((grant ? "grant" : "revoke") + " hand can only be used by a player (holding the item to target)."));
            return 0;
        }
        ServerPlayerEntity issuer = (ServerPlayerEntity) source.getEntity();
        ItemStack held = issuer.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(new StringTextComponent("You must hold an item to use '" + (grant ? "grant" : "revoke") + " hand'."));
            return 0;
        }
        return applyGrantRevoke(context, JourneyDataAttachment.getItemKey(held), grant);
    }

    private static int applyGrantRevoke(CommandContext<CommandSource> context, String key, boolean grant) throws CommandSyntaxException {
        CommandSource source = context.getSource();
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        MinecraftServer server = source.getServer();
        int affected = 0;

        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            ServerPlayerEntity online = server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                JourneyDataAttachment data = online.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
                if (data != null) {
                    if (grant) data.grant(key); else data.revoke(key);
                    GlobalDataHandler.savePlayerUnlocks(online, data);
                    GlobalDataHandler.syncToClient(online, data);
                    affected++;
                }
            } else {
                GlobalDataHandler.mutateOfflinePlayerData(uuid, data -> {
                    if (grant) data.grant(key); else data.revoke(key);
                });
                affected++;
            }
        }

        int finalAffected = affected;
        String verb = grant ? "Granted" : "Revoked";
        source.sendSuccess(new StringTextComponent(verb + " '" + key + "' for " + finalAffected + " player(s)."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static String itemId(Item item) {
        return Registry.ITEM.getKey(item).toString();
    }
}
