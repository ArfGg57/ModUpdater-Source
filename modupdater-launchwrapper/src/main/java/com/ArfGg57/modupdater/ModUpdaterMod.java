package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.restart.CrashCoordinator;
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
 * Forge mod container for ModUpdater Tweaker.
 * Provides lifecycle hooks to process restart requirements and enforce crashes when needed.
 * Uses Forge 1.7.10 compatible APIs for deferred crash logic.
 */
@Mod(modid = ModUpdaterMod.MODID, name = ModUpdaterMod.NAME, version = ModUpdaterMod.VERSION, acceptedMinecraftVersions = "*")
public class ModUpdaterMod {
    
    public static final String MODID = "modupdater-tweaker";
    public static final String NAME = "ModUpdater Tweaker";
    public static final String VERSION = "2.20";
    
    // Internal flag tracking if restart is required (set once, never cleared until crash)
    private volatile boolean restartRequiredFlag = false;
    
    // Crash scheduling state (instance-specific, but coordinated via CrashCoordinator)
    private volatile boolean crashScheduled = false;
    private volatile int crashDelayTicks = 0;
    
    // Configured delay before crash (in ticks) for GUI stability
    // 3 ticks ≈ 150ms at 20 TPS, ensures menu is fully initialized before crash
    private static final int CRASH_DELAY_TICKS = 3;
    
    // Crash message
    private volatile String crashMessage = "";
    
    /**
     * FML Initialization event.
     * Checks for decline reason (immediate crash) and restart required flag (deferred crash).
     * Registers tick event handler for continuous monitoring using Forge 1.7.10 APIs.
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Log that we entered the init handler
        System.out.println("[ModUpdater-Tweaker] Init event handler called");
        
        String declineReason = System.getProperty("modupdater.deferCrash");
        String restartRequired = System.getProperty("modupdater.restartRequired");
        
        // Log the property values for debugging
        System.out.println("[ModUpdater-Tweaker] modupdater.deferCrash = " + declineReason);
        System.out.println("[ModUpdater-Tweaker] modupdater.restartRequired = " + restartRequired);
        
        // Handle immediate crash for decline reason
        if (declineReason != null && !declineReason.trim().isEmpty()) {
            System.out.println("[ModUpdater-Tweaker] User declined update - triggering immediate Forge crash");
            // Sanitize declineReason to prevent any potential log injection or control characters
            String sanitized = declineReason.replaceAll("[\\p{C}]", " ").trim();
            StringBuilder crashMsg = new StringBuilder("ModUpdater deferred crash trigger. ");
            crashMsg.append("User declined update (").append(sanitized).append("). ");
            RuntimeException cause = new RuntimeException(crashMsg.toString().trim());
            CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
            System.out.println("[ModUpdater-Tweaker] About to throw ReportedException for declined update");
            throw new ReportedException(report);
        }
        
        // Check if restart is required now (early detection)
        if ("true".equals(restartRequired)) {
            System.out.println("[ModUpdater-Tweaker] Restart required detected at init time");
            restartRequiredFlag = true;
            crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
        }
        
        // ALWAYS register tick listener for robust monitoring using Forge 1.7.10 event system
        // This allows detection even if property is set late or GuiOpenEvent is missed
        System.out.println("[ModUpdater-Tweaker] Registering tick event listener for continuous monitoring");
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("[ModUpdater-Tweaker] Tick loop active - will monitor for main menu and property changes");
    }
    
    /**
     * Detects if the given screen is a main menu (vanilla or custom replacement).
     * Uses multiple detection strategies compatible with Forge 1.7.10:
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
        // This conservative approach minimizes risk of incorrect detection
        if (hasMain && hasMenu) {
            System.out.println("[ModUpdater-Tweaker] Detected custom main menu via heuristics: " + screen.getClass().getName());
            return true;
        }
        
        return false;
    }
    
    /**
     * Client tick event handler for Forge 1.7.10.
     * Continuously monitors for restart requirement and main menu appearance.
     * When both conditions are met, schedules a crash with delay for GUI stability.
     * 
     * @param event ClientTickEvent from Forge event bus
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Only process at END phase to ensure frame is complete
        if (event.phase != TickEvent.Phase.END) return;
        
        // If crash already executed by any mod instance, stop processing
        if (CrashCoordinator.isCrashExecuted()) return;

        // Poll for restart required property (handles late setting by UpdaterCore)
        if (!restartRequiredFlag) {
            String restartRequired = System.getProperty("modupdater.restartRequired");
            if ("true".equals(restartRequired)) {
                System.out.println("[ModUpdater-Tweaker] Restart required property detected late (after init)");
                restartRequiredFlag = true;
                crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
            }
        }
        
        // If restart not required, nothing to do
        if (!restartRequiredFlag) return;

        // Safely access Minecraft and current screen
        GuiScreen currentScreen = null;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) currentScreen = mc.currentScreen;
        } catch (Exception e) {
            System.err.println("[ModUpdater-Tweaker] Warning: Error accessing current screen: " + e.getMessage());
            return;
        }

        // Handle crash countdown if already scheduled
        if (crashScheduled) {
            if (crashDelayTicks > 0) {
                if (crashDelayTicks == CRASH_DELAY_TICKS) {
                    System.out.println("[ModUpdater-Tweaker] Crash scheduled, waiting " + crashDelayTicks + " tick(s) for GUI stability");
                }
                crashDelayTicks--;
                if (crashDelayTicks == 0) {
                    System.out.println("[ModUpdater-Tweaker] Delay complete - executing crash now");
                    performCrash(currentScreen);
                }
            }
            return;
        }

        // Check if we're at main menu and schedule crash
        if (isMainMenuScreen(currentScreen)) {
            System.out.println("[ModUpdater-Tweaker] Main menu detected: " + (currentScreen != null ? currentScreen.getClass().getName() : "null"));
            System.out.println("[ModUpdater-Tweaker] Scheduling crash with " + CRASH_DELAY_TICKS + " tick delay for GUI stability");
            crashScheduled = true;
            crashDelayTicks = CRASH_DELAY_TICKS;
        }
    }

    /**
     * Performs the actual crash by creating a Forge CrashReport and throwing ReportedException.
     * Enriches the crash report with ModUpdater-specific details for debugging.
     * Uses only Forge 1.7.10 compatible APIs.
     * 
     * @param currentScreen The current GUI screen at time of crash
     */
    private void performCrash(final GuiScreen currentScreen) {
        // Try to claim the crash execution (thread-safe, prevents duplicate crashes)
        if (!CrashCoordinator.tryClaim()) {
            System.out.println("[ModUpdater-Tweaker] Another mod instance already claimed crash execution, skipping");
            return;
        }
        
        // Unregister event handler to prevent further ticks
        try { 
            MinecraftForge.EVENT_BUS.unregister(this); 
        } catch (Exception ignored) {}

        System.out.println("[ModUpdater-Tweaker] ========================================");
        System.out.println("[ModUpdater-Tweaker] EXECUTING DEFERRED CRASH (direct)");
        System.out.println("[ModUpdater-Tweaker] ========================================");

        // Create crash report with enriched details
        RuntimeException cause = new RuntimeException(crashMessage);
        CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
        
        try {
            // Add custom crash report sections with debugging information
            report.getCategory().addCrashSection("ModUpdater Deferred Crash Details", "");
            
            String restartProp = System.getProperty("modupdater.restartRequired", "null");
            report.getCategory().addCrashSection("RestartRequiredProperty", restartProp);
            
            String menuClass = (currentScreen != null) ? currentScreen.getClass().getName() : "null";
            report.getCategory().addCrashSection("MenuClass", menuClass);
            
            report.getCategory().addCrashSection("DelayTicksUsed", String.valueOf(CRASH_DELAY_TICKS));
            
            report.getCategory().addCrashSection("CrashTimestamp", new java.util.Date().toString());
            
            // Try to read locked files list if available
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
            System.err.println("[ModUpdater-Tweaker] Warning: Failed to enrich crash report: " + t.getMessage());
        }
        
        System.out.println("[ModUpdater-Tweaker] Throwing ReportedException now");
        throw new ReportedException(report);
    }
}
