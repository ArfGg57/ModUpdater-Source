package com.ArfGg57.modupdater.tweak;

import com.ArfGg57.modupdater.core.ModUpdaterLifecycle;
import com.ArfGg57.modupdater.ui.ModConfirmationDialog;
import com.ArfGg57.modupdater.ui.ModConfirmationDialog.ModEntry;
import com.ArfGg57.modupdater.core.UpdaterCore;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tweaker that shows ModConfirmationDialog before Minecraft launches.
 * Works with the provided ModConfirmationDialog and UpdaterCore implementations.
 * 
 * This tweaker runs after the coremod phase but before Minecraft main launch.
 */
public class UpdaterTweaker implements ITweaker {

    private final UpdaterCore core;

    public UpdaterTweaker() {
        this.core = new UpdaterCore();
    }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        // No-op for now; you can store gameDir/profile here if you ever need them.
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        // Check if early phase already ran
        if (ModUpdaterLifecycle.wasEarlyPhaseCompleted()) {
            System.out.println("[UpdaterTweaker] Early coremod phase already completed. Pending operations were processed.");
        }
        
        boolean shouldExit = false;
        int exitCode = 0;

        try {
            // Build empty lists; the dialog will enrich them using remote config and local state
            final List<ModEntry> mods = new ArrayList<>();
            final List<String> files = new ArrayList<>();
            final List<String> deletes = new ArrayList<>();

            // Build the dialog first so it can enrich lists (detect missing files) before deciding to show
            final ModConfirmationDialog[] dlgHolder = new ModConfirmationDialog[1];

            SwingUtilities.invokeAndWait(() -> {
                dlgHolder[0] = new ModConfirmationDialog(core, mods, files, deletes);
            });

            if (dlgHolder[0] != null && dlgHolder[0].hasAnyItems()) {
                // Show modal dialog on EDT
                SwingUtilities.invokeAndWait(() -> dlgHolder[0].showDialog());

                // After dialog closes: if user pressed Quit, stop the JVM before Minecraft launches
                if (!dlgHolder[0].wasAgreed()) {
                    System.out.println("ModUpdater: user chose Quit in confirmation dialog. Exiting.");
                    shouldExit = true;
                    exitCode = 0;
                } else {
                    // User agreed: run the updater synchronously to block game launch until finished
                    System.out.println("ModUpdater: user agreed. Running updater synchronously before launch...");
                    core.runUpdate();
                }
            } else {
                System.out.println("ModUpdater: No updates detected after enrichment. Skipping confirmation GUI.");
            }
        } catch (InvocationTargetException ite) {
            ite.printStackTrace();
            System.err.println("ModUpdater: fatal error in confirmation dialog (InvocationTargetException). Exiting.");
            shouldExit = true;
            exitCode = 1;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            ie.printStackTrace();
            System.err.println("ModUpdater: interrupted while showing confirmation dialog. Exiting.");
            shouldExit = true;
            exitCode = 1;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("ModUpdater: fatal error while preparing confirmation lists or dialog. Exiting.");
            shouldExit = true;
            exitCode = 1;
        }

        // Call System.exit OUTSIDE the try/catch so SecurityException is not swallowed
        if (shouldExit) {
            System.exit(exitCode);
        }
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
