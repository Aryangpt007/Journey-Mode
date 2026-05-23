package com.aryangpt007.journeymode.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlobalDataHandler {
    private static final Logger LOGGER = LogManager.getLogger("journeymode-data");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "journeymode_unlocks.json";

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

    private static File getGlobalFile() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        return gameDir.resolve(FILE_NAME).toFile();
    }

    public static synchronized void loadPlayerUnlocks(ServerPlayer player) {
        try {
            JourneyDataAttachment data = getPlayerData(player);

            File file = getGlobalFile();
            if (!file.exists()) {
                savePlayerUnlocks(player, data);
                return;
            }

            JsonObject root;
            try (FileReader reader = new FileReader(file)) {
                root = new JsonParser().parse(reader).getAsJsonObject();
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

    public static synchronized void savePlayerUnlocks(ServerPlayer player, JourneyDataAttachment data) {
        try {
            File file = getGlobalFile();
            JsonObject root;
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    root = new JsonParser().parse(reader).getAsJsonObject();
                } catch (Exception e) {
                    root = new JsonObject();
                }
            } else {
                root = new JsonObject();
            }

            String uuidStr = player.getUUID().toString();
            String serialized = data.toJsonString();
            JsonObject playerDataJson = new JsonParser().parse(serialized).getAsJsonObject();
            root.add(uuidStr, playerDataJson);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
            LOGGER.info("Saved global Journey Mode unlocks for player {} ({})", player.getName().getString(), uuidStr);
        } catch (Exception e) {
            LOGGER.error("Failed to save global Journey Mode unlocks for player " + player.getName().getString(), e);
        }
    }

    public static void syncToClient(ServerPlayer player, JourneyDataAttachment data) {
        com.aryangpt007.journeymode.network.FabricNetworkHandler.syncToPlayer(player, data);
    }
}
