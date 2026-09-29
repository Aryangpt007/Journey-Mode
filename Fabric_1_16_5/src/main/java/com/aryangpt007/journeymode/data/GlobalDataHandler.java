package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.network.FabricNetworkHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
     *   3 - two changes, deliberately in ONE bump rather than two:
     *       (a) catalog keys are re-canonicalised for the denylist normalization (roadmap item 1
     *           Phase B) - the NBT half of a hybrid key is now recursively key-sorted;
     *       (b) an additive top-level "saves" key for per-save catalogs (roadmap item 3). Nothing
     *           reads it unless unlock_scope is per_save, so it is inert on upgrade.
     *       Player data itself is never restructured; only key spellings change, and two old keys
     *       that canonicalise to the same new key are MERGED, never dropped.
     * Old files must NEVER fail to load or lose data - bump this and add a migration step
     * below instead of changing the read/write shape in place.
     */
    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static final String KEY_SAVES = "saves";
    private static final String KEY_SCHEMA_VERSION = "schema_version";
    private static final String KEY_PLAYERS = "players";

    private static final Map<UUID, JourneyDataAttachment> serverPlayersData = new HashMap<>();
    private static final JourneyDataAttachment clientPlayerData = new JourneyDataAttachment();

    public static JourneyDataAttachment getPlayerData(Player player) {
        if (player.level.isClientSide) {
            return clientPlayerData;
        }
        return serverPlayersData.computeIfAbsent(player.getUUID(), uuid -> new JourneyDataAttachment());
    }

    public static void clearPlayerData(UUID uuid) {
        serverPlayersData.remove(uuid);
    }

    private static Path getGlobalFilePath() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        return gameDir.resolve(FILE_NAME);
    }

    /**
     * Read the root JSON object, migrating it in memory (not yet persisted) if it predates
     * schema_version. Returns an empty, current-schema root if the file doesn't exist or is
     * unreadable - callers still get a valid structure to populate.
     */
    /**
     * The parsed root is the authority for as long as the server is up.
     *
     * <p>Before this existed, every single deposit called readRoot(), which opened and parsed the
     * <em>entire</em> multi-player unlocks file from disk - so the cost of one player banking one
     * item scaled with the total number of players who had ever played on the server, not with
     * what actually changed. That ran synchronously on the server thread, inside a static
     * synchronized method, so every depositing player queued behind it while it held the lock
     * across disk I/O.
     *
     * <p>Reading once and keeping the parsed object removes the read half entirely. Writes still
     * go through the same single atomic writer, so the data-safety hard rule is untouched.
     */
    /**
     * Writes are debounced rather than issued once per deposit.
     *
     * <p>Every deposit used to serialize and rewrite the whole multi-player file synchronously on
     * the server thread. The atomic-writer hard rule governs *how* a write happens (temp file then
     * move-replace), not how often, so batching them does not weaken it: a crash still leaves the
     * previous complete file intact, and the most that can be lost is whatever changed inside the
     * debounce window. flush() is called on logout and on server stop, so an orderly shutdown
     * never loses anything at all.
     */
    private static final long WRITE_DEBOUNCE_MS = 5000L;
    private static boolean dirty = false;
    private static long lastWriteMs = 0L;

    private static synchronized void markDirty() {
        dirty = true;
        if (System.currentTimeMillis() - lastWriteMs >= WRITE_DEBOUNCE_MS) {
            flush();
        }
    }

    /** Persist pending changes now. Safe to call when nothing is pending. */
    public static synchronized void flush() {
        if (!dirty || cachedRoot == null) {
            dirty = false;
            return;
        }
        writeRoot(cachedRoot);
        dirty = false;
        lastWriteMs = System.currentTimeMillis();
    }

    private static JsonObject cachedRoot = null;

    private static synchronized JsonObject readRoot() {
        if (cachedRoot == null) {
            cachedRoot = readRootFromDisk();
        }
        return cachedRoot;
    }

    /** Drops the in-memory copy so the next read re-parses from disk (server stop / world swap). */
    public static synchronized void invalidateCachedRoot() {
        cachedRoot = null;
    }

    private static synchronized JsonObject readRootFromDisk() {
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
        int version = root.has(KEY_SCHEMA_VERSION) ? root.get(KEY_SCHEMA_VERSION).getAsInt() : 0;

        if (version == 0) {
            root = migrateLegacyToV2(root);
            version = 2;
        }
        if (version == 2) {
            root = migrateV2ToV3(root);
            version = 3;
        }
        // A file from a newer schema than this build understands passes through untouched. The
        // writer re-serializes the very object the reader parsed, so unknown top-level keys
        // survive a read-modify-write cycle - that is what makes downgrading non-destructive.
        return root;
    }

    private static JsonObject migrateLegacyToV2(JsonObject root) {
        JsonObject players = new JsonObject();
        int migrated = 0;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            players.add(entry.getKey(), entry.getValue());
            migrated++;
        }

        JsonObject upgraded = new JsonObject();
        upgraded.addProperty(KEY_SCHEMA_VERSION, 2);
        upgraded.add(KEY_PLAYERS, players);

        LOGGER.info("Migrated {} from legacy (unversioned) schema to schema_version 2 - {} player entries preserved.",
            FILE_NAME, migrated);
        return upgraded;
    }

    /**
     * 2 to 3. Re-canonicalises every catalog key and adds the (empty) per-save block.
     *
     * <p>Writes a one-time {@code .bak-schema2} sidecar first. This is the only migration that
     * rewrites key spellings rather than only moving structure, so a hand-recoverable copy of the
     * pre-migration state is cheap insurance.
     *
     * <p><b>Nothing is ever dropped.</b> Where two old keys canonicalise to the same new key they
     * are merged: the higher collected count wins, the earlier unlock timestamp wins, and unlocked
     * status is a union. Entries whose NBT 1.8.1 had already destroyed cannot be reconstructed -
     * that information is gone from the file - so they stay as base-item entries and keep working.
     */
    private static JsonObject migrateV2ToV3(JsonObject root) {
        backupOnce("bak-schema2");

        JsonObject players = globalPlayers(root);
        int playersTouched = 0;
        int keysRewritten = 0;
        int keysMerged = 0;

        for (Map.Entry<String, JsonElement> playerEntry : players.entrySet()) {
            if (!playerEntry.getValue().isJsonObject()) continue;
            int[] stats = recanonicaliseCatalog(playerEntry.getValue().getAsJsonObject());
            keysRewritten += stats[0];
            keysMerged += stats[1];
            playersTouched++;
        }

        if (!root.has(KEY_SAVES)) {
            root.add(KEY_SAVES, new JsonObject());
        }
        root.addProperty(KEY_SCHEMA_VERSION, 3);

        LOGGER.info("Migrated {} to schema_version 3: {} player(s), {} key(s) re-canonicalised, {} merged. No entries dropped.",
            FILE_NAME, playersTouched, keysRewritten, keysMerged);
        return root;
    }

    /** @return two counters: {rewritten, merged} */
    private static int[] recanonicaliseCatalog(JsonObject data) {
        int rewritten = 0;
        int merged = 0;

        if (data.has("collected_counts") && data.get("collected_counts").isJsonObject()) {
            JsonObject src = data.getAsJsonObject("collected_counts");
            JsonObject rebuilt = new JsonObject();
            for (Map.Entry<String, JsonElement> e : src.entrySet()) {
                String key = JourneyDataAttachment.canonicaliseKey(e.getKey());
                if (!key.equals(e.getKey())) rewritten++;
                if (rebuilt.has(key)) {
                    merged++;
                    rebuilt.addProperty(key, Math.max(rebuilt.get(key).getAsInt(), e.getValue().getAsInt()));
                } else {
                    rebuilt.add(key, e.getValue());
                }
            }
            data.add("collected_counts", rebuilt);
        }

        if (data.has("unlock_timestamps") && data.get("unlock_timestamps").isJsonObject()) {
            JsonObject src = data.getAsJsonObject("unlock_timestamps");
            JsonObject rebuilt = new JsonObject();
            for (Map.Entry<String, JsonElement> e : src.entrySet()) {
                String key = JourneyDataAttachment.canonicaliseKey(e.getKey());
                if (rebuilt.has(key)) {
                    // Earliest wins: the newest-first grid should show when it was FIRST earned.
                    rebuilt.addProperty(key, Math.min(rebuilt.get(key).getAsLong(), e.getValue().getAsLong()));
                } else {
                    rebuilt.add(key, e.getValue());
                }
            }
            data.add("unlock_timestamps", rebuilt);
        }

        if (data.has("unlocked_items") && data.get("unlocked_items").isJsonArray()) {
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<String>();
            for (JsonElement e : data.getAsJsonArray("unlocked_items")) {
                seen.add(JourneyDataAttachment.canonicaliseKey(e.getAsString()));
            }
            JsonArray rebuilt = new JsonArray();
            for (String key : seen) rebuilt.add(key);
            data.add("unlocked_items", rebuilt);
        }

        return new int[] { rewritten, merged };
    }

    /** One-time sidecar copy of the current file, for hand recovery. No-op if it already exists. */
    private static void backupOnce(String suffix) {
        try {
            Path path = getGlobalFilePath();
            if (!Files.exists(path)) return;
            Path backup = path.resolveSibling(FILE_NAME + "." + suffix);
            if (Files.exists(backup)) return;
            Files.copy(path, backup);
            LOGGER.info("Wrote {} before migrating - keep it until the upgrade is confirmed good.", backup.getFileName());
        } catch (Exception e) {
            // A failed backup must not block the migration; it is insurance, not a precondition.
            LOGGER.warn("Could not write a pre-migration backup of {}", FILE_NAME, e);
        }
    }

    // ---------------------------------------------------------------- per-save catalogs (item 3)

    /**
     * Identifier of the world currently loaded, or null outside a session.
     *
     * <p>Keyed on a UUID stamped into a small file in the world directory rather than on the world
     * folder name. A rename would otherwise orphan that world's catalog - the data would still be
     * in the JSON, but the player would open the world to an empty catalog, which is
     * indistinguishable from data loss from where they are sitting. A stamped id survives renames,
     * and a *copied* world inherits the catalog, which is the intuitively correct outcome.
     */
    private static String currentSaveId = null;

    public static synchronized void setCurrentSaveId(String saveId) {
        currentSaveId = saveId;
    }

    public static synchronized String getCurrentSaveId() {
        return currentSaveId;
    }

    /**
     * The always-global player block. Used by the migration and as the seed source - never routed
     * through the per-save resolution below, or migrating would depend on which world was open.
     */
    private static JsonObject globalPlayers(JsonObject root) {
        if (!root.has(KEY_PLAYERS) || !root.get(KEY_PLAYERS).isJsonObject()) {
            root.add(KEY_PLAYERS, new JsonObject());
        }
        return root.getAsJsonObject(KEY_PLAYERS);
    }

    /**
     * Resolve which player block reads and writes should use.
     *
     * <p>Under {@code global} scope this is byte-for-byte the old behaviour. Under {@code per_save}
     * it is {@code saves.<id>.players}, seeded on first sight from the global profile unless the
     * pack asked for empty starts.
     *
     * <p><b>Under per-save scope the global block is frozen: read as a seed, never written to.</b>
     * That is what makes the switch reversible - setting unlock_scope back to global restores the
     * pre-switch state exactly, because nothing overwrote it. The documented tradeoff is that a
     * world first seen long after the switch inherits that frozen snapshot rather than any progress
     * made since, which is precisely why per_save_seed=empty exists.
     */
    private static JsonObject getPlayersObject(JsonObject root) {
        if (!SaveScopeConfig.isPerSave() || currentSaveId == null) {
            return globalPlayers(root);
        }

        if (!root.has(KEY_SAVES) || !root.get(KEY_SAVES).isJsonObject()) {
            root.add(KEY_SAVES, new JsonObject());
        }
        JsonObject saves = root.getAsJsonObject(KEY_SAVES);

        if (!saves.has(currentSaveId) || !saves.get(currentSaveId).isJsonObject()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("label", currentSaveLabel == null ? currentSaveId : currentSaveLabel);

            JsonObject seeded = new JsonObject();
            if (SaveScopeConfig.seedFromGlobalSnapshot()) {
                // COPY, never move. The global profile must be left completely intact.
                JsonObject source = globalPlayers(root);
                for (Map.Entry<String, JsonElement> e : source.entrySet()) {
                    // Re-serialize rather than deepCopy(): that method is package-private in the
                    // Gson shipped with the older loaders, and this must be a genuine deep copy -
                    // sharing a subtree with the global block would let a per-save write mutate the
                    // very profile that is supposed to stay frozen.
                    seeded.add(e.getKey(), GSON.fromJson(GSON.toJson(e.getValue()), JsonElement.class));
                }
                LOGGER.info("Seeded per-save catalog for '{}' from the global profile ({} player entry/entries copied).",
                    currentSaveId, seeded.size());
            } else {
                LOGGER.info("Started per-save catalog for '{}' empty (per_save_seed=empty).", currentSaveId);
            }
            entry.add(KEY_PLAYERS, seeded);
            saves.add(currentSaveId, entry);
            markDirty();
        }

        JsonObject entry = saves.getAsJsonObject(currentSaveId);
        if (!entry.has(KEY_PLAYERS) || !entry.get(KEY_PLAYERS).isJsonObject()) {
            entry.add(KEY_PLAYERS, new JsonObject());
        }
        return entry.getAsJsonObject(KEY_PLAYERS);
    }

    /** Human-readable world name, recorded alongside the id purely for recovery/debugging. */
    private static String currentSaveLabel = null;

    public static synchronized void setCurrentSaveLabel(String label) {
        currentSaveLabel = label;
    }

    /** Wipe THIS save's catalog only. The global archive is untouched. */
    public static synchronized boolean resetCurrentSave() {
        if (!SaveScopeConfig.isPerSave() || currentSaveId == null) return false;
        JsonObject root = readRoot();
        if (!root.has(KEY_SAVES) || !root.get(KEY_SAVES).isJsonObject()) return false;
        JsonObject saves = root.getAsJsonObject(KEY_SAVES);
        if (!saves.has(currentSaveId)) return false;
        saves.remove(currentSaveId);
        markDirty();
        flush();
        return true;
    }

    /**
     * Write the root object atomically: write to a temp file in the same directory, then
     * move-replace over the real file. A crash or power loss mid-write leaves the previous,
     * still-valid file in place rather than a half-written/corrupt one.
     */
    private static synchronized void writeRoot(JsonObject root) {
        cachedRoot = root;
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

    public static synchronized void loadPlayerUnlocks(ServerPlayer player) {
        try {
            JourneyDataAttachment data = getPlayerData(player);

            JsonObject root = readRoot();
            JsonObject players = getPlayersObject(root);
            String uuidStr = player.getUUID().toString();

            if (players.has(uuidStr)) {
                JsonObject playerDataJson = players.getAsJsonObject(uuidStr);
                String serialized = GSON.toJson(playerDataJson);
                JourneyDataAttachment loadedData = JourneyDataAttachment.fromJsonString(serialized);
                data.copyFrom(loadedData);
                data.checkPendingUnlocks(player.level.getRecipeManager(), player.level.registryAccess());
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

    public static synchronized void savePlayerUnlocks(ServerPlayer player, JourneyDataAttachment data) {
        try {
            JsonObject root = readRoot();
            JsonObject players = getPlayersObject(root);

            String uuidStr = player.getUUID().toString();
            players.add(uuidStr, data.toJsonObject());

            markDirty();
            LOGGER.debug("Saved global Journey Mode unlocks for player {} ({})", player.getName().getString(), uuidStr);
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

        players.add(uuidStr, data.toJsonObject());
        markDirty();
    }

    /**
     * Helper to sync player capability data to client. §1 Shared Team Catalogs: if the player is
     * on a team, the TEAM's shared progress is sent instead of their personal progress - the
     * client never needs to know or care which source it came from, it just renders whatever
     * it's given. This is the only place that distinction has to be made server-side for
     * display purposes (deposit/fetch have their own team checks - see JourneyModeMenu /
     * FabricNetworkHandler's REQUEST_ITEM receiver).
     */
    /**
     * Send only the entries named in {@code changedKeys}, for the client to merge into what it
     * already has.
     *
     * <p>A deposit changes one entry. The full-sync path re-serialises and re-sends the entire
     * catalog to communicate that, which on a large catalog means thousands of keys crossing the
     * wire to say one counter moved by one - and three defensive map copies to build it.
     *
     * <p>Callers that <em>remove</em> anything (purge, revoke, reset, team join/leave) must keep
     * using {@link #syncToClient}: a delta cannot express a removal, because a key absent from it
     * means "unchanged", not "deleted". See JourneyDataAttachment.applyDelta.
     */
    public static void syncDeltaToClient(ServerPlayer player, JourneyDataAttachment data, java.util.Set<String> changedKeys) {
        if (changedKeys == null || changedKeys.isEmpty()) return;

        java.util.Optional<TeamData> team = data.getTeamId() != null
            ? TeamDataHandler.getTeamForPlayer(player.getUUID())
            : java.util.Optional.empty();
        String teamName = team.map(TeamData::getDisplayName).orElse(null);

        java.util.Map<String, Integer> allCounts = team.map(TeamData::getAllCollectedCounts).orElseGet(data::getAllCollectedCounts);
        java.util.Set<String> allUnlocked = team.map(TeamData::getUnlockedItems).orElseGet(data::getUnlockedItems);
        java.util.Map<String, Long> allTimestamps = team.map(TeamData::getUnlockTimestamps).orElseGet(data::getUnlockTimestamps);

        java.util.Map<String, Integer> counts = new java.util.HashMap<String, Integer>();
        java.util.Set<String> unlocked = new java.util.HashSet<String>();
        java.util.Map<String, Long> timestamps = new java.util.HashMap<String, Long>();
        for (String key : changedKeys) {
            Integer c = allCounts.get(key);
            if (c != null) counts.put(key, c);
            if (allUnlocked.contains(key)) unlocked.add(key);
            Long t = allTimestamps.get(key);
            if (t != null) timestamps.put(key, t);
        }

        FabricNetworkHandler.syncToPlayer(player, counts, unlocked, timestamps,
            data.isEnabled(), data.isShowTooltips(), teamName, true);
    }

    public static void syncToClient(ServerPlayer player, JourneyDataAttachment data) {
        Optional<TeamData> team = data.getTeamId() != null
            ? TeamDataHandler.getTeamForPlayer(player.getUUID())
            : Optional.empty();

        Map<String, Integer> counts = team.isPresent() ? team.get().getAllCollectedCounts() : data.getAllCollectedCounts();
        Set<String> unlocked = team.isPresent() ? team.get().getUnlockedItems() : data.getUnlockedItems();
        Map<String, Long> timestamps = team.isPresent() ? team.get().getUnlockTimestamps() : data.getUnlockTimestamps();
        String teamName = team.isPresent() ? team.get().getDisplayName() : null;
        // Shed optional payload before this packet can cross vanilla's 1 MiB custom-payload
        // ceiling - past it the client is kicked on the read, on every join. See
        // JourneyDataAttachment.MAX_SYNC_PAYLOAD_BYTES for why these two go first.
        if (JourneyDataAttachment.estimateSyncBytes(counts, unlocked, timestamps) > JourneyDataAttachment.MAX_SYNC_PAYLOAD_BYTES) {
            timestamps = java.util.Collections.<String, Long>emptyMap();
            if (JourneyDataAttachment.estimateSyncBytes(counts, unlocked, timestamps) > JourneyDataAttachment.MAX_SYNC_PAYLOAD_BYTES) {
                counts = java.util.Collections.<String, Integer>emptyMap();
            }
        }

        FabricNetworkHandler.syncToPlayer(player, counts, unlocked, timestamps, data.isEnabled(), data.isShowTooltips(), teamName);
    }
}
