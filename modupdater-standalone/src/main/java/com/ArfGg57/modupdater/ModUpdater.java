package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.core.UpdaterCore;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;

@Mod(modid = "modupdater", name = "ModUpdater", version = "2.20")
public class ModUpdater {

    // Internal flag tracking if restart is required (set once, never cleared until crash)
    private volatile boolean restartRequiredFlag = false;
    
    // Crash scheduling state
    private volatile boolean crashScheduled = false;
    private volatile boolean crashExecuted = false;
    private volatile int crashDelayTicks = 0;
    
    // Configured delay before crash (in ticks) for GUI stability
    // 3 ticks ≈ 150ms at 20 TPS, ensures menu is fully initialized before crash
    private static final int CRASH_DELAY_TICKS = 3;
    
    // Crash message
    private volatile String crashMessage = "";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Check if tweaker already ran (skip update to avoid duplicate processing)
        String tweakerRan = System.getProperty("modupdater.tweakerRan");
        if ("true".equals(tweakerRan)) {
            System.out.println("[ModUpdater] Tweaker already processed updates; skipping preInit update (backward compatibility mode inactive)");
            return;
        }
        
        // Tweaker didn't run - this is standalone mode (backward compatibility)
        System.out.println("[ModUpdater] Tweaker not detected; running update in preInit (standalone/backward compatibility mode)");
        try {
            UpdaterCore core = new UpdaterCore();
            core.runUpdate();
        } catch (Exception e) {
            // Only catch Exception, not Error - let Errors (like AssertionError) propagate to crash the game
            System.err.println("ERROR during ModUpdater preInit:");
            e.printStackTrace();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent evt) {
        // Log that we entered the init handler
        System.out.println("[ModUpdater] Init event handler called");
        
        String declineReason = System.getProperty("modupdater.deferCrash");
        String restartRequired = System.getProperty("modupdater.restartRequired");
        
        // Log the property values for debugging
        System.out.println("[ModUpdater] modupdater.deferCrash = " + declineReason);
        System.out.println("[ModUpdater] modupdater.restartRequired = " + restartRequired);
        
        // Handle immediate crash for decline reason
        if (declineReason != null && !declineReason.trim().isEmpty()) {
            System.out.println("[ModUpdater] User declined update - triggering immediate Forge crash");
            // Sanitize declineReason to prevent any potential log injection or control characters
            String sanitized = declineReason.replaceAll("[\\p{C}]", " ").trim();
            StringBuilder crashMsg = new StringBuilder("ModUpdater deferred crash trigger. ");
            crashMsg.append("User declined update (").append(sanitized).append("). ");
            RuntimeException cause = new RuntimeException(crashMsg.toString().trim());
            CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
            System.out.println("[ModUpdater] About to throw ReportedException for declined update");
            throw new ReportedException(report);
        }
        
        // Check if restart is required now (early detection)
        if ("true".equals(restartRequired)) {
            System.out.println("[ModUpdater] Restart required detected at init time");
            restartRequiredFlag = true;
            crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
        }
        
        // ALWAYS register tick listener for robust monitoring
        // This allows detection even if property is set late or GuiOpenEvent is missed
        System.out.println("[ModUpdater] Registering tick event listener for continuous monitoring");
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("[ModUpdater] Tick loop active - will monitor for main menu and property changes");
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
        // This conservative approach minimizes risk of incorrect detection
        if (hasMain && hasMenu) {
            System.out.println("[ModUpdater] Detected custom main menu via heuristics: " + screen.getClass().getName());
            return true;
        }
        
        return false;
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (crashExecuted) return;

        if (!restartRequiredFlag) {
            String restartRequired = System.getProperty("modupdater.restartRequired");
            if ("true".equals(restartRequired)) {
                System.out.println("[ModUpdater] Restart required property detected late (after init)");
                restartRequiredFlag = true;
                crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
            }
        }
        if (!restartRequiredFlag) return;

        GuiScreen currentScreen = null;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) currentScreen = mc.currentScreen;
        } catch (Exception e) {
            System.err.println("[ModUpdater] Warning: Error accessing current screen: " + e.getMessage());
            return;
        }

        if (crashScheduled) {
            if (crashDelayTicks > 0) {
                if (crashDelayTicks == CRASH_DELAY_TICKS) {
                    System.out.println("[ModUpdater] Crash scheduled, waiting " + crashDelayTicks + " tick(s) for GUI stability");
                }
                crashDelayTicks--;
                if (crashDelayTicks == 0) {
                    System.out.println("[ModUpdater] Delay complete - executing crash now");
                    performCrash(currentScreen);
                }
            }
            return;
        }

        if (isMainMenuScreen(currentScreen)) {
            System.out.println("[ModUpdater] Main menu detected: " + (currentScreen != null ? currentScreen.getClass().getName() : "null"));
            System.out.println("[ModUpdater] Scheduling crash with " + CRASH_DELAY_TICKS + " tick delay for GUI stability");
            crashScheduled = true;
            crashDelayTicks = CRASH_DELAY_TICKS;
        }
    }

    private void performCrash(final GuiScreen currentScreen) {
        crashExecuted = true;
        try { MinecraftForge.EVENT_BUS.unregister(this); } catch (Exception ignored) {}

        System.out.println("[ModUpdater] ========================================");
        System.out.println("[ModUpdater] EXECUTING DEFERRED CRASH (direct)");
        System.out.println("[ModUpdater] ========================================");

        RuntimeException cause = new RuntimeException(crashMessage);
        CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
        try {
            report.getCategory().addCrashSection("ModUpdater Deferred Crash Details", "");
            String restartProp = System.getProperty("modupdater.restartRequired", "null");
            report.getCategory().addCrashSection("RestartRequiredProperty", restartProp);
            String menuClass = (currentScreen != null) ? currentScreen.getClass().getName() : "null";
            report.getCategory().addCrashSection("MenuClass", menuClass);
            report.getCategory().addCrashSection("DelayTicksUsed", String.valueOf(CRASH_DELAY_TICKS));
            report.getCategory().addCrashSection("CrashTimestamp", new java.util.Date().toString());
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
            System.err.println("[ModUpdater] Warning: Failed to enrich crash report: " + t.getMessage());
        }
        System.out.println("[ModUpdater] Throwing ReportedException now");
        throw new ReportedException(report);
    }
}
