package com.aryangpt007.journeymode.platform.neoforge;

import com.aryangpt007.journeymode.logic.ConfigHandler;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Path;

/**
 * NeoForge-specific configuration handler wrapper.
 * Delegates to the common ConfigHandler.
 */
public class NeoForgeConfigHandler {
    
    // TOML config (minimal - actual data is in JSON files)
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    
    static {
        // Build a minimal TOML config
        SPEC = BUILDER.build();
    }
    
    /**
     * Register the config with the mod container
     */
    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
        
        // Initialize common config handler with the config directory path
        Path configDir = FMLPaths.CONFIGDIR.get().resolve("Journey Mode");
        ConfigHandler.initialize(configDir);
    }
    
    /**
     * Handle config loading and reloading events
     */
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            // Reload configs when config reloads
            ConfigHandler.reload();
        }
    }
}
