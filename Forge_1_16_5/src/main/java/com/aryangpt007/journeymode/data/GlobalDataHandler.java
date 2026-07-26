package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class GlobalDataHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-data");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "journeymode_unlocks.json";

    /**
     * Schema history:
     *   (unversioned/legacy) - root was a flat map of {uuid: playerData}.
     *   2 - root is {"schema_version": 2, "players": {uuid: playerData}}. Reserves room for a
     *       future top-level "teams" key without another structural migration.
     * Old files must NEVER fail to load or lose data - bump this and add a migration step
     * below instead of changing the read/write shape in place.
     */
    private static final int CURRENT_SCHEMA_VERSION = 2;
    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_PLAYERS = "players";

    private static Path getGlobalFilePath() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        return gameDir.resolve(FILE_NAME);
    }

    /**
     * Read the root JSON object, migrating it in memory (not yet persisted) if it predates
     * schema_version. Returns an empty, current-schema root if the file doesn't exist or is
     * unreadable - callers still get a valid structure to populate.
     */
    private static synchronized JsonObject readRoot() {
        Path path = getGlobalFilePath();
        if (!Files.exists(path)) {
            return freshRoot();
        }

        JsonObject root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(reader).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.error("Failed to parse {} - treating as empty rather than discarding player data on a bad write. Original file left untouched on disk for manual recovery.", FILE_NAME, e);
            return freshRoot();
        }

        return migrateIfNeeded(root);
    }

    private static JsonObject freshRoot() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        root.add(KEY_PLAYERS, new JsonObject());
        return root;
    }

    /**
     * Legacy (pre-schema_version) files stored every player entry as a flat top-level
     * {uuid: playerData} map. Wrap those entries into "players" and stamp a version so this
     * only ever runs once per file. Every future schema bump adds another branch here instead
     * of touching the reader/writer shape - this is the hard "old JSON never breaks" gate.
     */
    private static JsonObject migrateIfNeeded(JsonObject root) {
        if (root.has(KEY_SCHEMA_VERSION)) {
            return root; // already current (or a future version this build doesn't need to touch)
        }

        JsonObject players = new JsonObject();
        int migrated = 0;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            players.add(entry.getKey(), entry.getValue());
            migrated++;
        }

        JsonObject upgraded = new JsonObject();
        upgraded.addProperty(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);
        upgraded.add(KEY_PLAYERS, players);

        LOGGER.info("Migrated {} from legacy (unversioned) schema to schema_version {} - {} player entries preserved.",
            FILE_NAME, CURRENT_SCHEMA_VERSION, migrated);
        return upgraded;
    }

    private static JsonObject getPlayersObject(JsonObject root) {
        if (!root.has(KEY_PLAYERS) || !root.get(KEY_PLAYERS).isJsonObject()) {
            root.add(KEY_PLAYERS, new JsonObject());
        }
        return root.getAsJsonObject(KEY_PLAYERS);
    }

    /**
     * Write the root object atomically: write to a temp file in the same directory, then
     * move-replace over the real file. A crash or power loss mid-write leaves the previous,
     * still-valid file in place rather than a half-written/corrupt one.
     */
    private static synchronized void writeRoot(JsonObject root) {
        Path path = getGlobalFilePath();
        Path tmp = path.resolveSibling(FILE_NAME + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.error("Failed to write {} - in-memory changes were NOT persisted this time.", FILE_NAME, e);
        }
    }

    /**
     * Load player unlocks from global JSON file into the player's capability.
     */
    public static synchronized void loadPlayerUnlocks(ServerPlayerEntity player) {
        try {
            JourneyDataAttachment data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
            if (data == null) return;

            JsonObject root = readRoot();
            JsonObject players = getPlayersObject(root);
            String uuidStr = player.getUUID().toString();

            if (players.has(uuidStr)) {
                JsonObject playerDataJson = players.getAsJsonObject(uuidStr);
                JourneyDataAttachment loadedData = JourneyDataAttachment.fromJsonString(GSON.toJson(playerDataJson));
                data.copyFrom(loadedData);
                data.checkPendingUnlocks(player.level.getRecipeManager());
                LOGGER.info("Successfully loaded global Journey Mode unlocks for player {} ({})", player.getName().getString(), uuidStr);
                // Persist immediately if this load also triggered a schema migration, so the
                // upgrade only has to happen once even if the server never calls save first.
                writeRoot(root);
            } else {
                savePlayerUnlocks(player, data);
            }

            syncToClient(player, data);
        } catch (Exception e) {
            LOGGER.error("Failed to load global Journey Mode unlocks for player " + player.getName().getString(), e);
        }
    }

    /**
     * Save the player's capability data to the global JSON file.
     */
    public static synchronized void savePlayerUnlocks(ServerPlayerEntity player, JourneyDataAttachment data) {
        try {
            JsonObject root = readRoot();
            JsonObject players = getPlayersObject(root);

            String uuidStr = player.getUUID().toString();
            JsonObject playerDataJson = new JsonParser().parse(data.toJsonString()).getAsJsonObject();
            players.add(uuidStr, playerDataJson);

            writeRoot(root);
            LOGGER.info("Saved global Journey Mode unlocks for player {} ({})", player.getName().getString(), uuidStr);
        } catch (Exception e) {
            LOGGER.error("Failed to save global Journey Mode unlocks for player " + player.getName().getString(), e);
        }
    }

    /**
     * Mutate an arbitrary player's entry by UUID, whether or not they are currently online.
     * Used by grant/revoke (works on offline players) - always goes through this single
     * synchronized read-modify-write path, never a second file handle.
     */
    public static synchronized void mutateOfflinePlayerData(UUID uuid, Consumer<JourneyDataAttachment> mutator) {
        JsonObject root = readRoot();
        JsonObject players = getPlayersObject(root);
        String uuidStr = uuid.toString();

        JourneyDataAttachment data = players.has(uuidStr)
            ? JourneyDataAttachment.fromJsonString(GSON.toJson(players.getAsJsonObject(uuidStr)))
            : new JourneyDataAttachment();

        mutator.accept(data);

        players.add(uuidStr, new JsonParser().parse(data.toJsonString()).getAsJsonObject());
        writeRoot(root);
    }

    /**
     * Helper to sync player capability data to client. §1 Shared Team Catalogs: if the player is
     * on a team, the TEAM's shared progress is sent instead of their personal progress - the
     * client never needs to know or care which source it came from, it just renders whatever
     * it's given. This is the only place that distinction has to be made server-side for
     * display purposes (deposit/fetch have their own team checks - see JourneyModeMenu /
     * RequestItemPacket).
     */
    public static void syncToClient(ServerPlayerEntity player, JourneyDataAttachment data) {
        Optional<TeamData> team = data.getTeamId() != null
            ? TeamDataHandler.getTeamForPlayer(player.getUUID())
            : Optional.empty();

        Map<String, Integer> counts = team.isPresent() ? team.get().getAllCollectedCounts() : data.getAllCollectedCounts();
        java.util.Set<String> unlocked = team.isPresent() ? team.get().getUnlockedItems() : data.getUnlockedItems();
        Map<String, Long> timestamps = team.isPresent() ? team.get().getUnlockTimestamps() : data.getUnlockTimestamps();
        String teamName = team.isPresent() ? team.get().getDisplayName() : null;

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncJourneyDataPacket(
            counts,
            unlocked,
            timestamps,
            data.isEnabled(),
            data.isShowTooltips(),
            teamName
        ));
    }
}
