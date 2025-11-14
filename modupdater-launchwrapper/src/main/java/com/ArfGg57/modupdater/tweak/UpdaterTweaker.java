package com.ArfGg57.modupdater.tweak;

import com.ArfGg57.modupdater.UpdaterCore;
import net.minecraft.launchwrapper.ITweaker;

import java.io.File;
import java.util.List;

public class UpdaterTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        // set working gameDir if needed: UpdaterCore can read from current dir.
        // Run updater synchronously — must complete before returning.
        new UpdaterCore().runUpdate();
    }

    @Override
    public void injectIntoClassLoader(net.minecraft.launchwrapper.LaunchClassLoader classLoader) {}

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
