package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class GlobalDataHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-data");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "journeymode_unlocks.json";

    private static File getGlobalFile() {
        return new File(FILE_NAME);
    }

    /**
     * Load player unlocks from global JSON file into the player's capability.
     */
    public static synchronized void loadPlayerUnlocks(EntityPlayerMP player) {
        try {
            IJourneyData data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY, null);
            if (data == null) return;

            File file = getGlobalFile();
            if (!file.exists()) {
                savePlayerUnlocks(player, data);
                return;
            }

            JsonObject root;
            FileReader reader = null;
            try {
                reader = new FileReader(file);
                root = new JsonParser().parse(reader).getAsJsonObject();
            } catch (Exception e) {
                root = new JsonObject();
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
            }

            String uuidStr = player.getUniqueID().toString();
            if (root.has(uuidStr)) {
                JsonObject playerDataJson = root.getAsJsonObject(uuidStr);
                String serialized = GSON.toJson(playerDataJson);
                
                JourneyData temp = JourneyData.fromJsonString(serialized);
                data.copyFrom(temp);
                LOGGER.info("Successfully loaded global Journey Mode unlocks for player {} ({})", player.getName(), uuidStr);
            } else {
                savePlayerUnlocks(player, data);
            }
            
            syncToClient(player, data);
        } catch (Exception e) {
            LOGGER.error("Failed to load global Journey Mode unlocks for player " + player.getName(), e);
        }
    }

    /**
     * Save the player's capability data to the global JSON file.
     */
    public static synchronized void savePlayerUnlocks(EntityPlayerMP player, IJourneyData data) {
        try {
            File file = getGlobalFile();
            JsonObject root;
            if (file.exists()) {
                FileReader reader = null;
                try {
                    reader = new FileReader(file);
                    root = new JsonParser().parse(reader).getAsJsonObject();
                } catch (Exception e) {
                    root = new JsonObject();
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                    }
                }
            } else {
                root = new JsonObject();
            }

            String uuidStr = player.getUniqueID().toString();
            String serialized = data.toJsonString();
            JsonObject playerDataJson = new JsonParser().parse(serialized).getAsJsonObject();
            root.add(uuidStr, playerDataJson);

            FileWriter writer = null;
            try {
                writer = new FileWriter(file);
                GSON.toJson(root, writer);
            } finally {
                if (writer != null) {
                    try { writer.close(); } catch (Exception ignored) {}
                }
            }
            LOGGER.info("Saved global Journey Mode unlocks for player {} ({})", player.getName(), uuidStr);
        } catch (Exception e) {
            LOGGER.error("Failed to save global Journey Mode unlocks for player " + player.getName(), e);
        }
    }

    /**
     * Helper to sync player capability data to client
     */
    public static void syncToClient(EntityPlayerMP player, IJourneyData data) {
        NetworkHandler.sendTo(new SyncJourneyDataPacket(
            data.getAllCollectedCounts(),
            data.getUnlockedItems(),
            data.getUnlockTimestamps()
        ), player);
    }
}
