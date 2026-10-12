/*
 * Copyright (C) 2021-2025 GummerCraft <http://gummercraft.fun>
 */

package com.gummer.launcher.install;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.install.InstallTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import java.io.File;

@RequiredArgsConstructor
@Log
public class FilePermissions implements InstallTask {
    private final File target;

    @Override
    public void execute(Launcher launcher) throws Exception {
        log.info("Setting executable flag on " + target.getName());

        target.setExecutable(true);
    }

    @Override
    public double getProgress() {
        return -1;
    }

    @Override
    public String getStatus() {
        return "Permissions " + target.getName();
    }
}

