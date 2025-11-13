package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "modupdater", name = "Mod Updater", version = "1.0")
public class ModUpdater {

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Run updater in a separate thread to prevent freezing Minecraft
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    UpdaterCore updater = new UpdaterCore();
                    updater.runUpdate();
                } catch (Throwable t) {
                    // Ensure any unexpected error is printed so you can debug in logs
                    t.printStackTrace();
                }
            }
        }, "ModUpdater-Thread").start();
    }
}
