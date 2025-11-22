package com.ArfGg57.modupdater.tweak;

import com.ArfGg57.modupdater.core.UpdaterCore;
import com.ArfGg57.modupdater.ui.ModConfirmationDialog;
import com.ArfGg57.modupdater.ui.ModConfirmationDialog.ModEntry;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/** Tweaker that shows ModConfirmationDialog before Minecraft launches. */
public class UpdaterTweaker implements ITweaker {

    private final UpdaterCore core;

    public UpdaterTweaker() { this.core = new UpdaterCore(); }

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) { }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        boolean userDeclined = false;
        try {
            final List<ModEntry> mods = new ArrayList<>();
            final List<String> files = new ArrayList<>();
            final List<String> deletes = new ArrayList<>();
            final ModConfirmationDialog[] dlgHolder = new ModConfirmationDialog[1];
            SwingUtilities.invokeAndWait(() -> dlgHolder[0] = new ModConfirmationDialog(core, mods, files, deletes));
            if (dlgHolder[0] != null && dlgHolder[0].hasAnyItems()) {
                SwingUtilities.invokeAndWait(dlgHolder[0]::showDialog);
                if (!dlgHolder[0].wasAgreed()) {
                    System.out.println("[ModUpdater] User declined update; deferring Forge crash until mod initialization.");
                    userDeclined = true;
                } else {
                    System.out.println("[ModUpdater] User agreed; running updater before launch.");
                    core.runUpdate();
                }
            } else {
                System.out.println("[ModUpdater] No updates detected; continuing launch.");
            }
        } catch (InvocationTargetException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("[ModUpdater] Dialog error; deferring crash.");
            userDeclined = true;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[ModUpdater] Unexpected error; deferring crash.");
            userDeclined = true;
        }
        if (userDeclined) {
            // Set system property for deferred crash mod to pick up in FML init.
            System.setProperty("modupdater.deferCrash", "confirmation_dialog_declined");
        }
    }

    @Override public String getLaunchTarget() { return "net.minecraft.client.main.Main"; }
    @Override public String[] getLaunchArguments() { return new String[0]; }
}