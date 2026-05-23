package com.aryangpt007.journeymode.data;

import com.aryangpt007.journeymode.network.NetworkHandler;
import com.aryangpt007.journeymode.network.packets.SyncJourneyDataPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class GlobalDataHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-data");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "journeymode_unlocks.json";

    private static File getGlobalFile() {
        Path gameDir = FMLPaths.GAMEDIR.get();
        return gameDir.resolve(FILE_NAME).toFile();
    }

    /**
     * Load player unlocks from global JSON file into the player's capability.
     */
    public static synchronized void loadPlayerUnlocks(ServerPlayer player) {
        try {
            JourneyDataAttachment data = player.getCapability(JourneyDataCapabilityProvider.JOURNEY_DATA_CAPABILITY).orElse(null);
            if (data == null) return;

            File file = getGlobalFile();
            if (!file.exists()) {
                savePlayerUnlocks(player, data);
                return;
            }

            JsonObject root;
            try (FileReader reader = new FileReader(file)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                root = new JsonObject();
            }

            String uuidStr = player.getUUID().toString();
            if (root.has(uuidStr)) {
                JsonObject playerDataJson = root.getAsJsonObject(uuidStr);
                String serialized = GSON.toJson(playerDataJson);
                JourneyDataAttachment loadedData = JourneyDataAttachment.fromJsonString(serialized);
                
                data.copyFrom(loadedData);
                LOGGER.info("Successfully loaded global Journey Mode unlocks for player {} ({})", player.getName().getString(), uuidStr);
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
    public static synchronized void savePlayerUnlocks(ServerPlayer player, JourneyDataAttachment data) {
        try {
            File file = getGlobalFile();
            JsonObject root;
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    root = JsonParser.parseReader(reader).getAsJsonObject();
                } catch (Exception e) {
                    root = new JsonObject();
                }
            } else {
                root = new JsonObject();
            }

            String uuidStr = player.getUUID().toString();
            String serialized = data.toJsonString();
            JsonObject playerDataJson = JsonParser.parseString(serialized).getAsJsonObject();
            root.add(uuidStr, playerDataJson);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
            LOGGER.info("Saved global Journey Mode unlocks for player {} ({})", player.getName().getString(), uuidStr);
        } catch (Exception e) {
            LOGGER.error("Failed to save global Journey Mode unlocks for player " + player.getName().getString(), e);
        }
    }

    /**
     * Helper to sync player capability data to client
     */
    public static void syncToClient(ServerPlayer player, JourneyDataAttachment data) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncJourneyDataPacket(
            data.getAllCollectedCounts(),
            data.getUnlockedItems(),
            data.getUnlockTimestamps()
        ));
    }
}
