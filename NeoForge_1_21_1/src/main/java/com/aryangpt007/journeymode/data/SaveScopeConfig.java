package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.config.ConfigHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

/**
 * Config for per-save unlock catalogs (roadmap item 3).
 *
 * <p>Deliberately loader-independent - plain File and Gson only, no Forge/Fabric types - so this
 * class is byte-identical across all nine environments and cannot drift between them. It sits in
 * the same {@code config/Journey Mode/} directory as the blacklist and threshold files.
 *
 * <p><b>Both defaults are the ones that cannot lose anything.</b> {@code unlock_scope} defaults to
 * {@code global}, which is byte-for-byte the pre-1.8.2 behaviour, so upgrading changes nothing
 * until someone opts in. {@code per_save_seed} defaults to {@code global_snapshot}, so the first
 * time a world is seen under per-save scope it inherits the existing profile rather than opening
 * to an empty catalog - which, from where the player is sitting, is indistinguishable from having
 * lost their progress.
 */
public final class SaveScopeConfig {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "save_scope.json";

    public static final String SCOPE_GLOBAL = "global";
    public static final String SCOPE_PER_SAVE = "per_save";
    public static final String SEED_GLOBAL_SNAPSHOT = "global_snapshot";
    public static final String SEED_EMPTY = "empty";

    private static String unlockScope = SCOPE_GLOBAL;
    private static String perSaveSeed = SEED_GLOBAL_SNAPSHOT;
    private static boolean loaded = false;

    private SaveScopeConfig() {}

    public static synchronized void load() {
        loaded = true;
        try {
            File dir = ConfigHandler.getConfigDir();
            if (!dir.exists() && !dir.mkdirs()) {
                LOGGER.warn("Could not create {} - using per-save defaults.", dir);
                return;
            }
            File file = new File(dir, FILE_NAME);
            if (!file.exists()) {
                writeDefaults(file);
                return;
            }
            Reader reader = new FileReader(file);
            try {
                JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                if (json.has("unlock_scope")) unlockScope = json.get("unlock_scope").getAsString();
                if (json.has("per_save_seed")) perSaveSeed = json.get("per_save_seed").getAsString();
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            // A broken file must never break the mod - fall back to the lossless defaults.
            LOGGER.error("Failed to read {} - falling back to unlock_scope=global.", FILE_NAME, e);
            unlockScope = SCOPE_GLOBAL;
            perSaveSeed = SEED_GLOBAL_SNAPSHOT;
        }
        LOGGER.info("Journey Mode unlock scope: {} (per_save_seed={}).", unlockScope, perSaveSeed);
    }

    private static void writeDefaults(File file) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("_comment",
                "unlock_scope: 'global' (default) pools unlocks across every world in this "
                + "installation - the pre-1.8.2 behaviour. 'per_save' gives each world its own "
                + "catalog. per_save_seed: 'global_snapshot' (default) copies the existing global "
                + "profile the first time a world is seen, so nobody opens an existing world to an "
                + "empty catalog; 'empty' starts each new world from nothing.");
            json.addProperty("unlock_scope", SCOPE_GLOBAL);
            json.addProperty("per_save_seed", SEED_GLOBAL_SNAPSHOT);
            Writer writer = new FileWriter(file);
            try {
                GSON.toJson(json, writer);
            } finally {
                writer.close();
            }
            LOGGER.info("Created default {}", FILE_NAME);
        } catch (Exception e) {
            LOGGER.error("Failed to write default {}", FILE_NAME, e);
        }
    }

    public static synchronized boolean isPerSave() {
        if (!loaded) load();
        return SCOPE_PER_SAVE.equalsIgnoreCase(unlockScope);
    }

    public static synchronized boolean seedFromGlobalSnapshot() {
        if (!loaded) load();
        return !SEED_EMPTY.equalsIgnoreCase(perSaveSeed);
    }

    public static synchronized String describe() {
        if (!loaded) load();
        return unlockScope + " (seed: " + perSaveSeed + ")";
    }
}
