package com.ArfGg57.modupdater.tweak;

import com.ArfGg57.modupdater.ModConfirmationDialog;
import com.ArfGg57.modupdater.ModConfirmationDialog.ModEntry;
import com.ArfGg57.modupdater.UpdaterCore;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Tweaker that shows ModConfirmationDialog before Minecraft launches.
 * Works with the provided ModConfirmationDialog and UpdaterCore implementations.
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
        boolean shouldExit = false;
        int exitCode = 0;

        try {
            // populate the lists (may perform network I/O)
            core.populateConfirmationLists();

            final List<ModEntry> mods = core.getModsForUI();
            final List<String> files = core.getFilesForUI();
            final List<String> deletes = core.getDeletesForUI();

            System.out.println("DEBUG: mods size = " + (mods == null ? "null" : mods.size()));
            System.out.println("DEBUG: files size = " + (files == null ? "null" : files.size()));
            System.out.println("DEBUG: deletes size = " + (deletes == null ? "null" : deletes.size()));
            if (mods != null && !mods.isEmpty()) {
                System.out.println("DEBUG: first mod: " + mods.get(0).displayName + " / " + mods.get(0).downloadUrl);
            }

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
                    core.runUpdateSelected(mods);
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
