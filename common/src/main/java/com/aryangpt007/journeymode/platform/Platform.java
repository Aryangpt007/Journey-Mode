package com.aryangpt007.journeymode.platform;

import java.util.ServiceLoader;

/**
 * Service loader for platform-specific implementations.
 * Automatically detects and loads the correct platform implementation at runtime.
 */
public class Platform {
    
    private static PlatformHelper INSTANCE;
    
    /**
     * Get the platform helper instance
     * @return The platform-specific implementation
     */
    public static PlatformHelper get() {
        if (INSTANCE == null) {
            INSTANCE = ServiceLoader.load(PlatformHelper.class)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                    "No platform implementation found! Make sure the platform module is included."
                ));
        }
        return INSTANCE;
    }
    
    /**
     * Get the platform name (for logging/debugging)
     * @return "neoforge", "forge", or "fabric"
     */
    public static String name() {
        return get().getPlatformName();
    }
    
    /**
     * Check if running on client
     * @return true if client
     */
    public static boolean isClient() {
        return get().isClient();
    }
    
    /**
     * Check if running on server
     * @return true if server
     */
    public static boolean isServer() {
        return get().isServer();
    }
}
