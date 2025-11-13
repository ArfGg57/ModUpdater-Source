package com.ArfGg57.modupdater;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

/**
 * This class now serves as the Coremod entry point, allowing the update process
 * to run *before* the Minecraft window initializes, thus ensuring the GUI
 * appears first. We implement IFMLLoadingPlugin and execute the update in the
 * constructor, which is called by the Forge LaunchWrapper itself.
 */
@IFMLLoadingPlugin.Name("ModUpdaterCorePlugin")
@IFMLLoadingPlugin.MCVersion("1.7.10") // Assuming 1.7.10 based on the common use of IFMLLoadingPlugin
public class ModUpdater implements IFMLLoadingPlugin {

    /**
     * The constructor is the earliest and most guaranteed place to run
     * blocking code in a Coremod.
     */
    public ModUpdater() {
        System.out.println("ModUpdaterCorePlugin: Starting pre-load update check...");
        try {
            // This blocking call runs synchronously, preventing FML from
            // proceeding until the update or configuration check is finished.
            UpdaterCore updater = new UpdaterCore();
            updater.runUpdate();
        } catch (Throwable t) {
            // Catching all errors here is critical, as a failure in the Coremod
            // can prevent the entire game from starting.
            System.err.println("FATAL ERROR during ModUpdater Coremod execution:");
            t.printStackTrace();
        }
    }

    // --- Required IFMLLoadingPlugin methods (not used for simple launch) ---

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // Not using injected data
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}