package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;

/**
 * Performs a proper Forge crash (ReportedException) if the tweaker set a deferred crash flag.
 * Ensures CrashReport is generated only after Forge/Minecraft classes are initialized.
 */
@Mod(modid = "modupdaterdeferredcrash", name = "ModUpdater Deferred Crash", version = "1.0", acceptableRemoteVersions = "*", dependencies = "after:modupdater")
public class ModUpdaterDeferredCrash {

    @Mod.EventHandler
    public void init(FMLInitializationEvent evt) {
        // Log that we entered the init handler
        System.out.println("[ModUpdaterDeferredCrash] Init event handler called");
        
        String declineReason = System.getProperty("modupdater.deferCrash");
        String restartRequired = System.getProperty("modupdater.restartRequired");
        
        // Log the property values for debugging
        System.out.println("[ModUpdaterDeferredCrash] modupdater.deferCrash = " + declineReason);
        System.out.println("[ModUpdaterDeferredCrash] modupdater.restartRequired = " + restartRequired);
        
        if ((declineReason != null && !declineReason.trim().isEmpty()) || "true".equals(restartRequired)) {
            System.out.println("[ModUpdaterDeferredCrash] Crash condition met - triggering Forge crash");
            StringBuilder crashMsg = new StringBuilder("ModUpdater deferred crash trigger. ");
            if (declineReason != null && !declineReason.trim().isEmpty()) {
                // Sanitize declineReason to prevent any potential log injection or control characters
                String sanitized = declineReason.replaceAll("[\\p{C}]", " ").trim();
                crashMsg.append("User declined update (").append(sanitized).append("). ");
            }
            if ("true".equals(restartRequired)) crashMsg.append("Restart required due to locked files. ");
            RuntimeException cause = new RuntimeException(crashMsg.toString().trim());
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
            System.out.println("[ModUpdaterDeferredCrash] About to throw ReportedException");
            throw new ReportedException(report);
        } else {
            System.out.println("[ModUpdaterDeferredCrash] No crash needed - continuing normal initialization");
        }
    }
}
