package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;

/**
 * Performs a proper Forge crash (ReportedException) if the tweaker set a deferred crash flag.
 * Ensures CrashReport is generated only after Forge/Minecraft classes are initialized.
 * For restart required, the crash is deferred until the main menu screen is reached.
 * 
 * ROBUSTNESS IMPROVEMENTS:
 * - Persistent tick-based monitoring instead of relying on GuiOpenEvent
 * - Detects custom main menus via heuristics
 * - Supports late system property setting
 * - Adds GUI stability delay before crash
 * - Comprehensive diagnostic logging
 */
@Mod(modid = "modupdaterdeferredcrash", name = "ModUpdater Deferred Crash", version = "1.0", acceptableRemoteVersions = "*", dependencies = "after:modupdater")
public class ModUpdaterDeferredCrash {

    // Internal flag tracking if restart is required (set once, never cleared until crash)
    private volatile boolean restartRequiredFlag = false;
    
    // Crash scheduling state
    private volatile boolean crashScheduled = false;
    private volatile boolean crashExecuted = false;
    private volatile int crashDelayTicks = 0;
    
    // Configured delay before crash (in ticks) for GUI stability
    private static final int CRASH_DELAY_TICKS = 3;
    
    // Crash message
    private volatile String crashMessage = "";

    @Mod.EventHandler
    public void init(FMLInitializationEvent evt) {
        // Log that we entered the init handler
        System.out.println("[ModUpdaterDeferredCrash] Init event handler called");
        
        String declineReason = System.getProperty("modupdater.deferCrash");
        String restartRequired = System.getProperty("modupdater.restartRequired");
        
        // Log the property values for debugging
        System.out.println("[ModUpdaterDeferredCrash] modupdater.deferCrash = " + declineReason);
        System.out.println("[ModUpdaterDeferredCrash] modupdater.restartRequired = " + restartRequired);
        
        // Handle immediate crash for decline reason
        if (declineReason != null && !declineReason.trim().isEmpty()) {
            System.out.println("[ModUpdaterDeferredCrash] User declined update - triggering immediate Forge crash");
            // Sanitize declineReason to prevent any potential log injection or control characters
            String sanitized = declineReason.replaceAll("[\\p{C}]", " ").trim();
            StringBuilder crashMsg = new StringBuilder("ModUpdater deferred crash trigger. ");
            crashMsg.append("User declined update (").append(sanitized).append("). ");
            RuntimeException cause = new RuntimeException(crashMsg.toString().trim());
            CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
            System.out.println("[ModUpdaterDeferredCrash] About to throw ReportedException for declined update");
            throw new ReportedException(report);
        }
        
        // Check if restart is required now (early detection)
        if ("true".equals(restartRequired)) {
            System.out.println("[ModUpdaterDeferredCrash] Restart required detected at init time");
            restartRequiredFlag = true;
            crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
        }
        
        // ALWAYS register tick listener for robust monitoring
        // This allows detection even if property is set late or GuiOpenEvent is missed
        System.out.println("[ModUpdaterDeferredCrash] Registering tick event listener for continuous monitoring");
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("[ModUpdaterDeferredCrash] Tick loop active - will monitor for main menu and property changes");
    }
    
    /**
     * Detects if the given screen is a main menu (vanilla or custom replacement).
     * Uses multiple detection strategies:
     * 1. instanceof GuiMainMenu (vanilla detection)
     * 2. Class name heuristics (contains both "main" and "menu" case-insensitive)
     * 
     * @param screen The GUI screen to check
     * @return true if this appears to be a main menu screen
     */
    private boolean isMainMenuScreen(GuiScreen screen) {
        if (screen == null) {
            return false;
        }
        
        // Strategy 1: Direct instanceof check for vanilla main menu
        if (screen instanceof GuiMainMenu) {
            return true;
        }
        
        // Strategy 2: Heuristic - check class name for "main" and "menu"
        // This catches custom main menu replacements like CustomMainMenu, BetterMainMenu, etc.
        String className = screen.getClass().getName().toLowerCase();
        boolean hasMain = className.contains("main");
        boolean hasMenu = className.contains("menu");
        
        // Both keywords must be present to avoid false positives on other GUIs
        if (hasMain && hasMenu) {
            System.out.println("[ModUpdaterDeferredCrash] Detected custom main menu via heuristics: " + screen.getClass().getName());
            return true;
        }
        
        return false;
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Only check on the END phase to ensure we're in a safe context
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // If crash already executed, nothing to do
        if (crashExecuted) {
            return;
        }
        
        // Check if property is now set (handles late setting after init)
        if (!restartRequiredFlag) {
            String restartRequired = System.getProperty("modupdater.restartRequired");
            if ("true".equals(restartRequired)) {
                System.out.println("[ModUpdaterDeferredCrash] Restart required property detected late (after init)");
                restartRequiredFlag = true;
                crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
            }
        }
        
        // If restart is not required, nothing to do - just continue monitoring silently
        if (!restartRequiredFlag) {
            return;
        }
        
        // Get current screen
        GuiScreen currentScreen = null;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                currentScreen = mc.currentScreen;
            }
        } catch (Exception e) {
            // Ignore errors getting current screen
            return;
        }
        
        // Check if crash is already scheduled
        if (crashScheduled) {
            // Countdown delay ticks
            if (crashDelayTicks > 0) {
                crashDelayTicks--;
                if (crashDelayTicks == 0) {
                    System.out.println("[ModUpdaterDeferredCrash] Delay complete - executing crash now");
                } else {
                    System.out.println("[ModUpdaterDeferredCrash] Crash countdown: " + crashDelayTicks + " tick(s) remaining");
                }
                return;
            }
            
            // Delay is complete - execute crash
            if (!crashExecuted) {
                executeCrash(currentScreen);
            }
            return;
        }
        
        // Not yet scheduled - check if we should schedule
        if (isMainMenuScreen(currentScreen)) {
            System.out.println("[ModUpdaterDeferredCrash] Main menu detected: " + 
                (currentScreen != null ? currentScreen.getClass().getName() : "null"));
            System.out.println("[ModUpdaterDeferredCrash] Scheduling crash with " + CRASH_DELAY_TICKS + " tick delay for GUI stability");
            crashScheduled = true;
            crashDelayTicks = CRASH_DELAY_TICKS;
        }
    }
    
    /**
     * Executes the deferred crash with enriched crash report.
     * @param currentScreen The current GUI screen (may be null)
     */
    private void executeCrash(GuiScreen currentScreen) {
        crashExecuted = true;
        
        System.out.println("[ModUpdaterDeferredCrash] ========================================");
        System.out.println("[ModUpdaterDeferredCrash] EXECUTING DEFERRED CRASH");
        System.out.println("[ModUpdaterDeferredCrash] ========================================");
        
        // Unregister the event listener to prevent any further events
        try {
            MinecraftForge.EVENT_BUS.unregister(this);
            System.out.println("[ModUpdaterDeferredCrash] Event listener unregistered");
        } catch (Exception e) {
            // Ignore unregister errors
        }
        
        RuntimeException cause = new RuntimeException(crashMessage);
        CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
        
        // Add enriched details section
        try {
            report.getCategory().addCrashSection("ModUpdater Deferred Crash Details", "");
            
            // Property value at crash time
            String restartProp = System.getProperty("modupdater.restartRequired", "null");
            report.getCategory().addCrashSection("RestartRequiredProperty", restartProp);
            
            // Menu class name
            String menuClass = (currentScreen != null) ? currentScreen.getClass().getName() : "null";
            report.getCategory().addCrashSection("MenuClass", menuClass);
            
            // Delay ticks used
            report.getCategory().addCrashSection("DelayTicksUsed", String.valueOf(CRASH_DELAY_TICKS));
            
            // Timestamp
            report.getCategory().addCrashSection("CrashTimestamp", new java.util.Date().toString());
            
            // Include locked files list if present
            String listPath = System.getProperty("modupdater.lockedFilesListFile", "");
            boolean lockedFilesPresent = false;
            if (!listPath.isEmpty()) {
                java.nio.file.Path p = java.nio.file.Paths.get(listPath);
                if (java.nio.file.Files.exists(p)) {
                    lockedFilesPresent = true;
                    java.util.List<String> lines = java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);
                    report.getCategory().addCrashSection("ModUpdater Locked Files", String.join("\n", lines));
                }
            }
            report.getCategory().addCrashSection("LockedFilesPresent", String.valueOf(lockedFilesPresent));
            
        } catch (Throwable t) {
            // ignore report enrichment errors
            System.err.println("[ModUpdaterDeferredCrash] Warning: Failed to enrich crash report: " + t.getMessage());
        }
        
        System.out.println("[ModUpdaterDeferredCrash] About to throw ReportedException for restart required");
        throw new ReportedException(report);
    }
}
