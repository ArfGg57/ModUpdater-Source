package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;

/**
 * Performs a proper Forge crash (ReportedException) if the tweaker set a deferred crash flag.
 * Ensures CrashReport is generated only after Forge/Minecraft classes are initialized.
 */
@Mod(modid = "modupdaterdeferredcrash", name = "ModUpdater Deferred Crash", version = "1.0", acceptableRemoteVersions = "*")
public class ModUpdaterDeferredCrash {

    @Mod.EventHandler
    public void init(FMLInitializationEvent evt) {
        String declineReason = System.getProperty("modupdater.deferCrash");
        String restartRequired = System.getProperty("modupdater.restartRequired");
        if ((declineReason != null && !declineReason.isEmpty()) || (restartRequired != null && restartRequired.equals("true"))) {
            StringBuilder crashMsg = new StringBuilder("ModUpdater deferred crash trigger. ");
            if (declineReason != null) crashMsg.append("User declined update (" + declineReason + "). ");
            if (restartRequired != null && restartRequired.equals("true")) crashMsg.append("Restart required due to locked files. ");
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
            throw new ReportedException(report);
        }
    }
}
