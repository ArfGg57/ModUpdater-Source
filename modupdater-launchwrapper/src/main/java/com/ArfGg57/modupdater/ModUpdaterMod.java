package com.ArfGg57.modupdater;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

/**
 * Forge mod container for ModUpdater.
 * Provides lifecycle hooks to process restart requirements.
 */
@Mod(modid = ModUpdaterMod.MODID, name = ModUpdaterMod.NAME, version = ModUpdaterMod.VERSION, acceptedMinecraftVersions = "*")
public class ModUpdaterMod {
    
    public static final String MODID = "modupdater";
    public static final String NAME = "ModUpdater";
    public static final String VERSION = "2.20";
    
    /**
     * FML Initialization event.
     * Attempts to clean up locked files without forcing an exit.
     */
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("[ModUpdater] FMLInitializationEvent: Checking for restart requirements...");
        RestartEnforcer.tryProcessRestartFlag(false);
    }
    
    /**
     * FML Post-Initialization event.
     * If restart is still required, schedules a delayed exit to enforce restart.
     */
    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        System.out.println("[ModUpdater] FMLPostInitializationEvent: Final restart check...");
        RestartEnforcer.tryProcessRestartFlag(true);
    }
}
