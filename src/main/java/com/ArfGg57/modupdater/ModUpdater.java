package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "modupdater", name = "Mod Updater", version = "1.0")
public class ModUpdater {

    /**
     * The FMLPreInitializationEvent is the first major event fired by FML.
     * To ensure the mod update happens BEFORE any other mod starts its setup,
     * we must run the update synchronously (blocking) inside this handler.
     * FML cannot move on to the next mod's preInit until this method returns.
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        try {
            UpdaterCore updater = new UpdaterCore();
            // Call the update method directly. This is a BLOCKING call.
            // It ensures the mod environment is stabilized before other mods load.
            updater.runUpdate();
        } catch (Throwable t) {
            // Log any unexpected error
            System.err.println("FATAL ERROR during ModUpdater preInit:");
            t.printStackTrace();
        }
    }
}