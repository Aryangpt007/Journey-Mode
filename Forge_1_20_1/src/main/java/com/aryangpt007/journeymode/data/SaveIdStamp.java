package com.aryangpt007.journeymode.data;

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
import java.util.UUID;

/**
 * Stamps a stable identifier into the world directory so a per-save catalog can be keyed on
 * something that survives a folder rename.
 *
 * <p>Keying on the folder name instead would need no file, but renaming a world would orphan its
 * catalog: the data would still be in the JSON while the player opened the world to an empty
 * catalog - indistinguishable from data loss. A stamped id survives renames, and a copied world
 * inherits the catalog, which is the intuitively correct outcome.
 *
 * <p>If the world directory cannot be written (read-only pack, permissions), this falls back to
 * the folder name for the session and says so loudly, rather than silently starting a fresh
 * catalog.
 */
public final class SaveIdStamp {

    private static final Logger LOGGER = LogManager.getLogger("journeymode");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "journeymode_save.json";

    private SaveIdStamp() {}

    /** Read or create the id for this world, and publish it plus a readable label. */
    public static void begin(File worldDir) {
        String label = worldDir == null ? "unknown" : worldDir.getName();
        String id = null;

        if (worldDir != null) {
            File file = new File(worldDir, FILE_NAME);
            try {
                if (file.exists()) {
                    Reader reader = new FileReader(file);
                    try {
                        JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                        if (json.has("save_id")) id = json.get("save_id").getAsString();
                        if (json.has("label")) label = json.get("label").getAsString();
                    } finally {
                        reader.close();
                    }
                }
                if (id == null || id.isEmpty()) {
                    id = UUID.randomUUID().toString();
                    JsonObject json = new JsonObject();
                    json.addProperty("save_id", id);
                    json.addProperty("label", label);
                    Writer writer = new FileWriter(file);
                    try {
                        GSON.toJson(json, writer);
                    } finally {
                        writer.close();
                    }
                    LOGGER.info("Stamped Journey Mode save id {} into {}", id, worldDir.getName());
                }
            } catch (Exception e) {
                LOGGER.error("Could not read or write {} in the world directory - falling back to the "
                    + "folder name for this session. A rename WILL orphan this world's catalog until "
                    + "the file can be written.", FILE_NAME, e);
                id = null;
            }
        }

        if (id == null || id.isEmpty()) {
            id = "folder:" + label;
        }

        GlobalDataHandler.setCurrentSaveLabel(label);
        GlobalDataHandler.setCurrentSaveId(id);
    }
}
