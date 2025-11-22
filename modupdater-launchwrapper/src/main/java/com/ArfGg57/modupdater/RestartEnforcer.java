package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.restart.CrashUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * DEPRECATED: This class is no longer used. Restart enforcement now uses Forge crash reports.
 * 
 * Legacy utility class to handle restart enforcement logic using System.exit.
 * Replaced by tick-based crash logic in ModUpdaterMod that produces proper Forge crash reports.
 * 
 * @deprecated Use ModUpdaterMod's tick-based crash logic instead. This class is kept for
 *             reference only and will be removed in a future version.
 */
@Deprecated
public final class RestartEnforcer {
    private RestartEnforcer() {}
    
    /**
     * @deprecated No longer used. Restart enforcement now handled by ModUpdaterMod tick handler.
     */
    @Deprecated
    public static void tryProcessRestartFlag(boolean allowUI) {
        // This method is deprecated and no longer called.
        // Restart enforcement is now handled by ModUpdaterMod's tick-based crash logic
        // which produces proper Forge crash reports instead of calling System.exit.
        System.err.println("[ModUpdater] WARNING: RestartEnforcer.tryProcessRestartFlag is deprecated and should not be called.");
        System.err.println("[ModUpdater] Restart enforcement is now handled by ModUpdaterMod tick handler.");
    }
}
