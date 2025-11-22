package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;

/**
 * Performs a proper Forge crash (ReportedException) if the tweaker set a deferred crash flag.
 * Ensures CrashReport is generated only after Forge/Minecraft classes are initialized.
 * For restart required, the crash is deferred until the main menu screen is reached.
 */
@Mod(modid = "modupdaterdeferredcrash", name = "ModUpdater Deferred Crash", version = "1.0", acceptableRemoteVersions = "*", dependencies = "after:modupdater")
public class ModUpdaterDeferredCrash {

    private volatile boolean shouldCrashOnMenu = false;
    private volatile String crashMessage = "";
    private volatile boolean menuDetected = false;

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
        
        // Handle deferred crash for restart required - wait until main menu
        if ("true".equals(restartRequired)) {
            System.out.println("[ModUpdaterDeferredCrash] Restart required - registering event listeners for menu crash");
            shouldCrashOnMenu = true;
            crashMessage = "ModUpdater deferred crash trigger. Restart required due to locked files.";
            
            // Register this instance as an event listener for both GUI and tick events
            MinecraftForge.EVENT_BUS.register(this);
            System.out.println("[ModUpdaterDeferredCrash] Event listeners registered, will crash when main menu opens");
        } else {
            System.out.println("[ModUpdaterDeferredCrash] No crash needed - continuing normal initialization");
        }
    }
    
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui != null && shouldCrashOnMenu && event.gui instanceof GuiMainMenu) {
            System.out.println("[ModUpdaterDeferredCrash] Main menu detected in GuiOpenEvent - setting flag for crash");
            menuDetected = true;
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        // Only check on the end of a tick to ensure we're in a safe context
        if (event.phase == TickEvent.Phase.END) {
            // Check both flags atomically by reading them into local variables
            boolean shouldCrash = shouldCrashOnMenu;
            boolean menuShown = menuDetected;
            
            if (shouldCrash && menuShown) {
                System.out.println("[ModUpdaterDeferredCrash] Triggering deferred crash from tick event");
                shouldCrashOnMenu = false; // Prevent multiple crashes
                menuDetected = false;
                
                // Unregister the event listener to prevent any further events
                MinecraftForge.EVENT_BUS.unregister(this);
                
                RuntimeException cause = new RuntimeException(crashMessage);
                CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash");
                
                // Include locked files list contents in crash report detail section if present
                try {
                    String listPath = System.getProperty("modupdater.lockedFilesListFile", "");
                    if (!listPath.isEmpty()) {
                        java.nio.file.Path p = java.nio.file.Paths.get(listPath);
                        if (java.nio.file.Files.exists(p)) {
                            java.util.List<String> lines = java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);
                            report.getCategory().addCrashSection("ModUpdater Locked Files", String.join("\n", lines));
                        }
                    }
                } catch (Throwable t) {
                    // ignore report enrichment errors
                }
                
                System.out.println("[ModUpdaterDeferredCrash] About to throw ReportedException for restart required");
                throw new ReportedException(report);
            }
        }
    }
}
