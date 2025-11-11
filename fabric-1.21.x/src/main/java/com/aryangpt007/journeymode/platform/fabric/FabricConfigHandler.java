package com.aryangpt007.journeymode.platform.fabric;

import com.aryangpt007.journeymode.logic.ConfigHandler;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric-specific configuration initialization
 */
public class FabricConfigHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("journeymode");
    
    public static void initialize() {
        try {
            ConfigHandler.initialize(FabricLoader.getInstance().getConfigDir());
            LOGGER.info("Configuration loaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration", e);
        }
    }
}
