package com.aryangpt007.journeymode.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Section 1 Shared Team Catalogs - per-world persistence and lifecycle operations.
 *
 * ARCHITECTURE NOTE (see JM_Project.md / JOURNEY_MODE_CHECKLIST.md sec 1): personal progress
 * (GlobalDataHandler) is stored at the game-instance/server-install root (the process working
 * directory on 1.12.2 - see GlobalDataHandler.getGlobalFilePath), not the individual world save.
 * That's fine for personal data (and already effectively per-server for a dedicated server), but
 * teams are scoped PER-WORLD by design decision, so "Team Alpha" in one singleplayer world must
 * never be visible from a different singleplayer world under the same installation. This file
 * therefore lives inside the WORLD SAVE directory instead - a deliberate asymmetry from personal
 * data, not an oversight.
 *
 * API VERIFICATION: 1.12.2 has no FMLPaths/LevelResource. The per-world save directory is
 * obtained via server.getWorld(0).getSaveHandler().getWorldDirectory() - verified by reading
 * net/minecraft/world/World.java#getSaveHandler() and net/minecraft/world/storage/ISaveHandler
 * #getWorldDirectory() from the decompiled MCP sources shipped with this Gradle project
 * (build/rfg/minecraft-src). getWorld(0) is the overworld, which always exists and is the
 * natural "the save" anchor for a per-world file (matches how vanilla's own per-world data,
 * e.g. scoreboard.dat/idcounts.dat, is anchored to the same directory).
 */
public class TeamDataHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-teams");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "journeymode_teams.json";
    private static final int CURRENT_SCHEMA_VERSION = 1;

    // In-memory cache for the currently running world/server. Reloaded on server start so a
    // client that loads a different singleplayer world never sees a previous world's teams.
    private static final Map<String, TeamData> teams = new HashMap<String, TeamData>();
    private static final Map<UUID, String> playerTeam = new HashMap<UUID, String>(); // uuid -> team id

    private TeamDataHandler() {}

    private static Path getTeamsFilePath(MinecraftServer server) {
        File worldDir = server.getWorld(0).getSaveHandler().getWorldDirectory();
        return worldDir.toPath().resolve(FILE_NAME);
    }

    /** Call once per world/server session (FMLServerStartingEvent). Clears any prior world's cache. */
    public static synchronized void load(MinecraftServer server) {
        teams.clear();
        playerTeam.clear();

        Path path = getTeamsFilePath(server);
        if (!Files.exists(path)) return;

        try {
            Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            try {
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                // Only one schema version has ever existed; this branch point is where a future
                // migration would go, following the same never-break-old-JSON discipline as
                // GlobalDataHandler's schema_version 2 migration.
                if (root.has("teams")) {
                    JsonObject teamsJson = root.getAsJsonObject("teams");
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : teamsJson.entrySet()) {
                        String teamId = entry.getKey();
                        TeamData team = TeamData.fromJson(teamId, entry.getValue().getAsJsonObject());
                        teams.put(teamId, team);
                        for (UUID member : team.getMembers()) {
                            playerTeam.put(member, teamId);
                        }
                    }
                }
                // Re-evaluate each team's partial progress against current thresholds now that
                // the world (and its recipe/crafting manager) is available - same never-re-lock-
                // only-instant-unlock semantics as GlobalDataHandler does for personal data.
                for (TeamData team : teams.values()) {
                    team.checkPendingUnlocks();
                }

                LOGGER.info("Loaded {} Journey Mode team(s) for this world", teams.size());
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load " + FILE_NAME + " - starting with no teams rather than crashing the server.", e);
        }
    }

    private static synchronized void save(MinecraftServer server) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", CURRENT_SCHEMA_VERSION);
        JsonObject teamsJson = new JsonObject();
        for (Map.Entry<String, TeamData> entry : teams.entrySet()) {
            teamsJson.add(entry.getKey(), entry.getValue().toJson());
        }
        root.add("teams", teamsJson);

        Path path = getTeamsFilePath(server);
        Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
            try {
                GSON.toJson(root, writer);
            } finally {
                writer.close();
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to write " + FILE_NAME + " - in-memory team changes were NOT persisted this time.", e);
        }
    }

    public static synchronized TeamData getTeamForPlayer(UUID playerUuid) {
        String teamId = playerTeam.get(playerUuid);
        return teamId == null ? null : teams.get(teamId);
    }

    public static synchronized TeamData getTeamById(String id) {
        return teams.get(id.toLowerCase());
    }

    /** @return null on success, or a human-readable error message. */
    public static synchronized String createTeam(MinecraftServer server, EntityPlayerMP owner, String name) {
        String id = name.toLowerCase();
        if (teams.containsKey(id)) {
            return "A team named '" + name + "' already exists.";
        }
        if (playerTeam.containsKey(owner.getUniqueID())) {
            return "You're already in a team - leave it first.";
        }

        TeamData team = new TeamData(id, name, owner.getUniqueID());
        teams.put(id, team);
        playerTeam.put(owner.getUniqueID(), id);
        setPlayerTeamId(owner, id);
        save(server);
        return null; // null = success
    }

    public static synchronized String joinTeam(MinecraftServer server, EntityPlayerMP player, String name) {
        String id = name.toLowerCase();
        TeamData team = teams.get(id);
        if (team == null) {
            return "No team named '" + name + "' exists.";
        }
        if (playerTeam.containsKey(player.getUniqueID())) {
            return "You're already in a team - leave it first.";
        }

        team.getMembers().add(player.getUniqueID());
        playerTeam.put(player.getUniqueID(), id);
        setPlayerTeamId(player, id);
        save(server);
        return null;
    }

    /**
     * Leaving snapshots the team's current unlocks into the player's own personal unlocked set
     * (avoids rage-quit grief) but does NOT copy collected_counts; future deposits resume against
     * their personal counters, independent of the team.
     */
    public static synchronized String leaveTeam(MinecraftServer server, EntityPlayerMP player) {
        String id = playerTeam.get(player.getUniqueID());
        if (id == null) {
            return "You're not in a team.";
        }
        TeamData team = teams.get(id);

        IJourneyData personal = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
        if (personal != null && team != null) {
            for (String key : team.getUnlockedItems()) {
                personal.grant(key); // grant() stamps its own timestamp; snapshot semantics only need the unlock itself
            }
        }

        if (team != null) {
            team.getMembers().remove(player.getUniqueID());
        }
        playerTeam.remove(player.getUniqueID());
        setPlayerTeamId(player, null);

        if (personal != null) {
            GlobalDataHandler.savePlayerUnlocks(player, personal);
            GlobalDataHandler.syncToClient(player, personal);
        }
        save(server);
        return null;
    }

    public static synchronized String kickPlayer(MinecraftServer server, EntityPlayerMP requester, String targetName) {
        String id = playerTeam.get(requester.getUniqueID());
        if (id == null) return "You're not in a team.";
        TeamData team = teams.get(id);
        if (team == null || !team.getOwnerUuid().equals(requester.getUniqueID())) {
            return "Only the team owner can kick members.";
        }

        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetName);
        UUID targetUuid = target != null ? target.getUniqueID() : null;
        if (targetUuid == null) {
            // Kick requires the target to be online - matches most vanilla admin commands and
            // avoids resolving a name to the wrong offline UUID via the profile cache.
            return "Player '" + targetName + "' must be online to be kicked.";
        }
        if (!team.getMembers().contains(targetUuid)) {
            return targetName + " is not in your team.";
        }
        if (targetUuid.equals(requester.getUniqueID())) {
            return "Use /journeymode team leave to leave your own team.";
        }

        team.getMembers().remove(targetUuid);
        playerTeam.remove(targetUuid);
        setPlayerTeamId(target, null);
        save(server);
        return null;
    }

    public static synchronized String transferOwnership(MinecraftServer server, EntityPlayerMP requester, String targetName) {
        String id = playerTeam.get(requester.getUniqueID());
        if (id == null) return "You're not in a team.";
        TeamData team = teams.get(id);
        if (team == null || !team.getOwnerUuid().equals(requester.getUniqueID())) {
            return "Only the team owner can transfer ownership.";
        }

        EntityPlayerMP target = server.getPlayerList().getPlayerByUsername(targetName);
        if (target == null || !team.getMembers().contains(target.getUniqueID())) {
            return targetName + " must be an online member of your team.";
        }

        team.setOwnerUuid(target.getUniqueID());
        save(server);
        return null;
    }

    private static void setPlayerTeamId(EntityPlayerMP player, String teamId) {
        IJourneyData data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
        if (data != null) {
            data.setTeamId(teamId);
            GlobalDataHandler.savePlayerUnlocks(player, data);
        }
    }

    /** Persist a team's own progress after a team deposit (separate from personal saves). */
    public static synchronized void saveAfterDeposit(MinecraftServer server) {
        save(server);
    }
}
