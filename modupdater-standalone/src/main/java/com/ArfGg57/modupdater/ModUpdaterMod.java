package com.ArfGg57.modupdater;

import com.ArfGg57.modupdater.restart.CrashCoordinator;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ReportedException;

@Mod(modid = ModUpdaterMod.MODID, name = ModUpdaterMod.NAME, version = ModUpdaterMod.VERSION, acceptedMinecraftVersions = "*")
public class ModUpdaterMod {
    public static final String MODID = "modupdater"; // unified mod id
    public static final String NAME = "ModUpdater";
    public static final String VERSION = "2.20";

    private volatile boolean restartRequiredFlag = false;
    private volatile boolean crashScheduled = false;
    private volatile int crashDelayTicks = 0;
    private static final int CRASH_DELAY_TICKS = 3;
    private volatile String crashMessage = "";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("[ModUpdater] Init event handler called");
        String declineReason = System.getProperty("modupdater.deferCrash");
        String restartRequired = System.getProperty("modupdater.restartRequired");
        System.out.println("[ModUpdater] modupdater.deferCrash = " + declineReason);
        System.out.println("[ModUpdater] modupdater.restartRequired = " + restartRequired);

        if (declineReason != null && !declineReason.trim().isEmpty()) {
            System.out.println("[ModUpdater] User declined update - triggering immediate Forge crash");
            String sanitized = declineReason.replaceAll("[\\p{C}]", " ").trim();
            RuntimeException cause = new RuntimeException("ModUpdater crash: user declined update (" + sanitized + ")");
            CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash (decline)");
            throw new ReportedException(report);
        }

        if ("true".equals(restartRequired)) {
            System.out.println("[ModUpdater] Restart required detected at init time");
            restartRequiredFlag = true;
            crashMessage = "ModUpdater: restart required due to locked files.";
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (CrashCoordinator.isCrashExecuted()) return;

        if (!restartRequiredFlag) {
            String restartRequired = System.getProperty("modupdater.restartRequired");
            if ("true".equals(restartRequired)) {
                System.out.println("[ModUpdater] Restart required property detected late");
                restartRequiredFlag = true;
                crashMessage = "ModUpdater: restart required due to locked files.";
            }
        }
        if (!restartRequiredFlag) return;

        GuiScreen current = null;
        try { Minecraft mc = Minecraft.getMinecraft(); if (mc != null) current = mc.currentScreen; } catch (Exception ignored) {}
        if (crashScheduled) {
            if (crashDelayTicks > 0) { crashDelayTicks--; if (crashDelayTicks == 0) performCrash(current); }
            return;
        }
        if (isMainMenuScreen(current)) {
            crashScheduled = true;
            crashDelayTicks = CRASH_DELAY_TICKS;
        }
    }

    private boolean isMainMenuScreen(GuiScreen screen) {
        if (screen == null) return false;
        if (screen instanceof GuiMainMenu) return true;
        String name = screen.getClass().getName().toLowerCase();
        return name.contains("main") && name.contains("menu");
    }

    private void performCrash(GuiScreen currentScreen) {
        if (!CrashCoordinator.tryClaim()) return;
        try { MinecraftForge.EVENT_BUS.unregister(this); } catch (Exception ignored) {}
        RuntimeException cause = new RuntimeException(crashMessage);
        CrashReport report = CrashReport.makeCrashReport(cause, "ModUpdater forced Forge crash (restart required)");
        try {
            report.getCategory().addCrashSection("RestartRequiredProperty", System.getProperty("modupdater.restartRequired", "null"));
            report.getCategory().addCrashSection("MenuClass", currentScreen == null ? "null" : currentScreen.getClass().getName());
            String listPath = System.getProperty("modupdater.lockedFilesListFile", "");
            if (!listPath.isEmpty()) {
                java.nio.file.Path p = java.nio.file.Paths.get(listPath);
                if (java.nio.file.Files.exists(p)) {
                    java.util.List<String> lines = java.nio.file.Files.readAllLines(p, java.nio.charset.StandardCharsets.UTF_8);
                    report.getCategory().addCrashSection("LockedFiles", String.join("\n", lines));
                }
            }
        } catch (Throwable ignored) {}
        throw new ReportedException(report);
    }
}

