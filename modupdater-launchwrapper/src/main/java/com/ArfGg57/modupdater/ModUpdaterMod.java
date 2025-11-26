package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.restart.CrashCoordinator;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
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
    
    // Flag to track if mod post-initialization is complete (similar to ding mod pattern)
    // Using volatile for thread-safe access from event handlers
    public static volatile boolean postInit = false;
    
    // Crash scheduling state (instance-specific, but coordinated via CrashCoordinator)
    private volatile boolean crashScheduled = false;
    private volatile int crashDelayTicks = 0;
    
    // Configured delay before crash (in ticks) for GUI stability
    // 3 ticks ≈ 150ms at 20 TPS, ensures menu is fully initialized before crash
    private static final int CRASH_DELAY_TICKS = 3;
    
    // Timeout fallback - if restart required but main menu not detected after this many ticks,
    // crash anyway. 600 ticks = 30 seconds at 20 TPS. This handles edge cases where main menu
    // detection fails completely.
    private static final int TIMEOUT_TICKS = 600;
    private volatile int ticksSinceRestartRequired = 0;
    
    // Crash message
    private volatile String crashMessage = "";
    
    /**
     * FML Pre-Initialization event.
     * Checks for decline reason (immediate crash) and restart required flag (deferred crash).
     * Registers event handlers for continuous monitoring using Forge 1.7.10 APIs.
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Log that we entered the init handler
        System.out.println("[ModUpdater-Tweaker] PreInit event handler called");
        
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
            System.out.println("[ModUpdater-Tweaker] Restart required detected at preInit time");
            restartRequiredFlag = true;
            crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
        }
        
        // ALWAYS register event listeners for robust monitoring using Forge 1.7.10 event system
        // This allows detection even if property is set late or GuiOpenEvent is missed
        System.out.println("[ModUpdater-Tweaker] Registering event listeners for continuous monitoring");
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("[ModUpdater-Tweaker] Event handlers active - will monitor for main menu and property changes");
    }
    
    /**
     * FML Post-Initialization event.
     * Marks initialization as complete (similar to ding mod pattern).
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        System.out.println("[ModUpdater-Tweaker] PostInit event handler called - initialization complete");
        postInit = true;
    }
    
    /**
     * GuiOpenEvent handler - more reliable way to detect main menu opening.
     * Uses LOWEST priority to ensure all other handlers run first (similar to ding mod pattern).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onGuiOpen(GuiOpenEvent event) {
        // Only process after post-init is complete
        if (!postInit) return;
        
        // If crash already executed by any mod instance, stop processing
        if (CrashCoordinator.isCrashExecuted()) return;
        
        // If restart not required, nothing to do
        if (!restartRequiredFlag) return;
        
        // Check if the GUI being opened is a main menu
        if (!isMainMenuScreen(event.gui)) return;
        
        System.out.println("[ModUpdater-Tweaker] Main menu opened (via GuiOpenEvent): " + 
            (event.gui != null ? event.gui.getClass().getName() : "null"));
        
        // Schedule crash (will be executed in tick handler)
        if (!crashScheduled) {
            System.out.println("[ModUpdater-Tweaker] Scheduling crash with " + CRASH_DELAY_TICKS + " tick delay for GUI stability");
            crashScheduled = true;
            crashDelayTicks = CRASH_DELAY_TICKS;
        }
    }
    
    /**
     * Detects if the given screen is a main menu (vanilla or custom replacement).
     * Uses multiple detection strategies compatible with Forge 1.7.10:
     * 1. instanceof GuiMainMenu (vanilla detection)
     * 2. Class name heuristics for common custom main menu patterns
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
        
        // Strategy 2: Heuristic - check class name for common main menu patterns
        // This catches custom main menu replacements
        String className = screen.getClass().getName().toLowerCase();
        
        // Pattern 1: Contains both "main" and "menu" (e.g., MainMenuScreen, GuiMainMenu)
        if (className.contains("main") && className.contains("menu")) {
            System.out.println("[ModUpdater-Tweaker] Detected main menu (main+menu pattern): " + screen.getClass().getName());
            return true;
        }
        
        // Pattern 2: Contains "custommenu" or "guicustom" (CustomMainMenu mod uses GuiCustomMenu)
        if (className.contains("custommenu") || className.contains("guicustom")) {
            System.out.println("[ModUpdater-Tweaker] Detected main menu (custom pattern): " + screen.getClass().getName());
            return true;
        }
        
        // Pattern 3: Contains "title" and "screen" or "menu" (e.g., TitleScreen, TitleMenu)
        if (className.contains("title") && (className.contains("screen") || className.contains("menu"))) {
            System.out.println("[ModUpdater-Tweaker] Detected main menu (title pattern): " + screen.getClass().getName());
            return true;
        }
        
        // Pattern 4: Ends with "mainmenu" or "titlemenu" or "titlescreen"
        if (className.endsWith("mainmenu") || className.endsWith("titlemenu") || className.endsWith("titlescreen")) {
            System.out.println("[ModUpdater-Tweaker] Detected main menu (suffix pattern): " + screen.getClass().getName());
            return true;
        }
        
        // Pattern 5: lumien's CustomMainMenu mod specifically uses these patterns
        if (className.contains("lumien") && className.contains("menu")) {
            System.out.println("[ModUpdater-Tweaker] Detected main menu (lumien pattern): " + screen.getClass().getName());
            return true;
        }
        
        return false;
    }
    
    /**
     * Client tick event handler for Forge 1.7.10.
     * Continuously monitors for restart requirement and main menu appearance.
     * When both conditions are met, schedules a crash with delay for GUI stability.
     * Also includes a timeout fallback to crash if main menu detection fails after 30 seconds.
     * 
     * @param event ClientTickEvent from Forge event bus
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Only process at END phase to ensure frame is complete
        if (event.phase != TickEvent.Phase.END) return;
        
        // Only process after post-init is complete (similar to ding mod pattern)
        if (!postInit) return;
        
        // If crash already executed by any mod instance, stop processing
        if (CrashCoordinator.isCrashExecuted()) return;

        // Poll for restart required property (handles late setting by UpdaterCore)
        if (!restartRequiredFlag) {
            String restartRequired = System.getProperty("modupdater.restartRequired");
            if ("true".equals(restartRequired)) {
                System.out.println("[ModUpdater-Tweaker] Restart required property detected late (after init)");
                restartRequiredFlag = true;
                crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
                ticksSinceRestartRequired = 0; // Start counting from detection
            }
        }
        
        // If restart not required, nothing to do
        if (!restartRequiredFlag) return;
        
        // Increment tick counter when restart is required
        ticksSinceRestartRequired++;

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
            return;
        }
        
        // Timeout fallback: If we've been waiting too long without detecting main menu,
        // crash anyway. This handles edge cases where main menu detection completely fails.
        if (ticksSinceRestartRequired >= TIMEOUT_TICKS && !crashScheduled) {
            System.out.println("[ModUpdater-Tweaker] WARNING: Main menu not detected after " + TIMEOUT_TICKS + " ticks");
            System.out.println("[ModUpdater-Tweaker] Current screen: " + (currentScreen != null ? currentScreen.getClass().getName() : "null"));
            System.out.println("[ModUpdater-Tweaker] Triggering timeout fallback crash - restart is required");
            crashMessage = "ModUpdater deferred crash trigger (timeout fallback). Restart required due to locked files.";
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
            // Security: Only read the file if it's in a temp directory or config directory
            String listPath = System.getProperty("modupdater.lockedFilesListFile", "");
            boolean lockedFilesPresent = false;
            if (!listPath.isEmpty()) {
                java.nio.file.Path p = java.nio.file.Paths.get(listPath);
                // Security validation: Only read files from temp directory or config directory
                String absolutePath = p.toAbsolutePath().toString().toLowerCase();
                boolean isSafeLocation = absolutePath.contains("temp") || 
                                          absolutePath.contains("tmp") || 
                                          absolutePath.contains("config") ||
                                          absolutePath.contains("modupdater");
                if (isSafeLocation && java.nio.file.Files.exists(p)) {
                    // Security: Limit file size to 100KB to prevent memory issues
                    long fileSize = java.nio.file.Files.size(p);
                    if (fileSize <= 100 * 1024) {
                        lockedFilesPresent = true;
                        java.util.List<String> lines = java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);
                        // Limit to first 100 lines
                        if (lines.size() > 100) {
                            lines = lines.subList(0, 100);
                            lines.add("... (truncated, " + (lines.size() - 100) + " more lines)");
                        }
                        report.getCategory().addCrashSection("ModUpdater Locked Files", String.join("\n", lines));
                    }
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
