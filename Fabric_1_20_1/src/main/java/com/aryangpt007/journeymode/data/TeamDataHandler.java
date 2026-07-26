package com.aryangpt007.journeymode.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * §1 Shared Team Catalogs - per-world persistence and lifecycle operations.
 *
 * ARCHITECTURE NOTE (see JM_Project.md / JOURNEY_MODE_CHECKLIST.md §1): personal progress
 * (GlobalDataHandler) is stored at FabricLoader.getInstance().getGameDir() - the game-instance/
 * server-install root, not the individual world save. That's fine for personal data (and already
 * effectively per-server for a dedicated server), but teams are scoped PER-WORLD by design
 * decision, so "Team Alpha" in one singleplayer world must never be visible from a different
 * singleplayer world under the same installation. This file therefore lives inside the WORLD
 * SAVE directory (server.getWorldPath(LevelResource.ROOT)), not the game dir - a deliberate
 * asymmetry from personal data, not an oversight.
 *
 * API note: LevelResource/getWorldPath are vanilla (net.minecraft.server.MinecraftServer /
 * net.minecraft.world.level.storage.LevelResource), not a Forge-only API - this loom project
 * uses official Mojang mappings (see build.gradle: loom.officialMojangMappings()), so the call
 * is identical to the Forge_1_20_1 reference rather than needing a Yarn-style WorldSavePath name.
 */
public class TeamDataHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-teams");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "journeymode_teams.json";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    // In-memory cache for the currently running world/server. Reloaded on server start so a
    // client that loads a different singleplayer world never sees a previous world's teams.
    private static final Map<String, TeamData> teams = new HashMap<>();
    private static final Map<UUID, String> playerTeam = new HashMap<>(); // uuid -> team id

    private TeamDataHandler() {}

    private static Path getTeamsFilePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    /** Call once per world/server session (e.g. ServerLifecycleEvents.SERVER_STARTING). Clears any prior world's cache. */
    public static synchronized void load(MinecraftServer server) {
        teams.clear();
        playerTeam.clear();

        Path path = getTeamsFilePath(server);
        if (!Files.exists(path)) return;

        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            // Only one schema version has ever existed; this branch point is where a future
            // migration would go, following the same never-break-old-JSON discipline as
            // GlobalDataHandler's schema_version 2 migration.
            if (root.has("teams")) {
                JsonObject teamsJson = root.getAsJsonObject("teams");
                for (String teamId : teamsJson.keySet()) {
                    TeamData team = TeamData.fromJson(teamId, teamsJson.getAsJsonObject(teamId));
                    teams.put(teamId, team);
                    for (UUID member : team.getMembers()) {
                        playerTeam.put(member, teamId);
                    }
                }
            }
            // Re-evaluate each team's partial progress against current thresholds now that the
            // world (and its recipe manager) is available - same never-re-lock-only-instant-
            // unlock semantics as GlobalDataHandler does for personal data on player join.
            for (TeamData team : teams.values()) {
                team.checkPendingUnlocks(server.overworld().getRecipeManager(), server.overworld().registryAccess());
            }

            LOGGER.info("Loaded {} Journey Mode team(s) for this world", teams.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load {} - starting with no teams rather than crashing the server.", FILE_NAME, e);
        }
    }

    private static synchronized void save(MinecraftServer server) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CURRENT_SCHEMA_VERSION);
        JsonObject teamsJson = new JsonObject();
        teams.forEach((id, team) -> teamsJson.add(id, team.toJson()));
        root.add("teams", teamsJson);

        Path path = getTeamsFilePath(server);
        Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to write {} - in-memory team changes were NOT persisted this time.", FILE_NAME, e);
        }
    }

    public static synchronized Optional<TeamData> getTeamForPlayer(UUID playerUuid) {
        String teamId = playerTeam.get(playerUuid);
        return teamId == null ? Optional.empty() : Optional.ofNullable(teams.get(teamId));
    }

    public static synchronized Optional<TeamData> getTeamById(String id) {
        return Optional.ofNullable(teams.get(id.toLowerCase()));
    }

    public static synchronized String createTeam(MinecraftServer server, ServerPlayer owner, String name) {
        String id = name.toLowerCase();
        if (teams.containsKey(id)) {
            return "A team named '" + name + "' already exists.";
        }
        if (playerTeam.containsKey(owner.getUUID())) {
            return "You're already in a team - leave it first.";
        }

        TeamData team = new TeamData(id, name, owner.getUUID());
        team.initializeCalculator(owner.level().getRecipeManager(), owner.level().registryAccess());
        teams.put(id, team);
        playerTeam.put(owner.getUUID(), id);
        setPlayerTeamId(owner, id);
        save(server);
        return null; // null = success
    }

    public static synchronized String joinTeam(MinecraftServer server, ServerPlayer player, String name) {
        String id = name.toLowerCase();
        TeamData team = teams.get(id);
        if (team == null) {
            return "No team named '" + name + "' exists.";
        }
        if (playerTeam.containsKey(player.getUUID())) {
            return "You're already in a team - leave it first.";
        }

        team.getMembers().add(player.getUUID());
        playerTeam.put(player.getUUID(), id);
        team.initializeCalculator(player.level().getRecipeManager(), player.level().registryAccess());
        setPlayerTeamId(player, id);
        save(server);
        return null;
    }

    /**
     * Leaving snapshots the team's current unlocks into the player's own personal unlocked set
     * (Terraria-like generosity - avoids rage-quit grief) but does NOT copy collected_counts;
     * future deposits resume against their personal counters, independent of the team.
     */
    public static synchronized String leaveTeam(MinecraftServer server, ServerPlayer player) {
        String id = playerTeam.get(player.getUUID());
        if (id == null) {
            return "You're not in a team.";
        }
        TeamData team = teams.get(id);

        JourneyDataAttachment personal = GlobalDataHandler.getPlayerData(player);
        if (team != null) {
            for (String key : team.getUnlockedItems()) {
                personal.grant(key); // grant() stamps its own timestamp; snapshot semantics only need the unlock itself
            }
        }

        if (team != null) {
            team.getMembers().remove(player.getUUID());
        }
        playerTeam.remove(player.getUUID());
        setPlayerTeamId(player, null);

        GlobalDataHandler.savePlayerUnlocks(player, personal);
        GlobalDataHandler.syncToClient(player, personal);
        save(server);
        return null;
    }

    public static synchronized String kickPlayer(MinecraftServer server, ServerPlayer requester, String targetName) {
        String id = playerTeam.get(requester.getUUID());
        if (id == null) return "You're not in a team.";
        TeamData team = teams.get(id);
        if (team == null || !team.getOwnerUuid().equals(requester.getUUID())) {
            return "Only the team owner can kick members.";
        }

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        UUID targetUuid = target != null ? target.getUUID() : null;
        if (targetUuid == null) {
            // Fall back to matching an offline member by cached name is out of scope for v1 -
            // kick requires the target to be online, same as most vanilla admin commands do.
            return "Player '" + targetName + "' must be online to be kicked.";
        }
        if (!team.getMembers().contains(targetUuid)) {
            return targetName + " is not in your team.";
        }
        if (targetUuid.equals(requester.getUUID())) {
            return "Use /journeymode team leave to leave your own team.";
        }

        team.getMembers().remove(targetUuid);
        playerTeam.remove(targetUuid);
        setPlayerTeamId(target, null);
        save(server);
        return null;
    }

    public static synchronized String transferOwnership(MinecraftServer server, ServerPlayer requester, String targetName) {
        String id = playerTeam.get(requester.getUUID());
        if (id == null) return "You're not in a team.";
        TeamData team = teams.get(id);
        if (team == null || !team.getOwnerUuid().equals(requester.getUUID())) {
            return "Only the team owner can transfer ownership.";
        }

        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null || !team.getMembers().contains(target.getUUID())) {
            return targetName + " must be an online member of your team.";
        }

        team.setOwnerUuid(target.getUUID());
        save(server);
        return null;
    }

    private static void setPlayerTeamId(ServerPlayer player, String teamId) {
        JourneyDataAttachment data = GlobalDataHandler.getPlayerData(player);
        data.setTeamId(teamId);
        GlobalDataHandler.savePlayerUnlocks(player, data);
    }

    /** Persist a team's own progress after a team deposit (separate from personal saves). */
    public static synchronized void saveAfterDeposit(MinecraftServer server) {
        save(server);
    }
}
