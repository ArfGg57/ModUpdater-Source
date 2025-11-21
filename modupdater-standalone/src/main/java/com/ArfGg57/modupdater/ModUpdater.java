package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.core.UpdaterCore;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "modupdater", name = "Mod Updater", version = "1.1")
public class ModUpdater {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            System.out.println("[ModUpdater] Running update in preInit");
            UpdaterCore core = new UpdaterCore();
            core.runUpdate();
        } catch (Exception e) {
            // Only catch Exception, not Error - let Errors (like AssertionError) propagate to crash the game
            System.err.println("ERROR during ModUpdater preInit:");
            e.printStackTrace();
        }
    }
}
