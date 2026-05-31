/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import xyz.kyngs.librelogin.common.util.CancellableTask;

/**
 * Bukkit/Spigot/Paper scheduler adapter implementation.
 * Uses the traditional Bukkit scheduler API.
 */
public class BukkitSchedulerAdapter implements SchedulerAdapter {
    
    @Override
    public CancellableTask runTaskLaterAsynchronously(Plugin plugin, Runnable runnable, long delayTicks) {
        var task = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
        return task::cancel;
    }
    
    @Override
    public CancellableTask runTaskTimerAsynchronously(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        var task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }
    
    @Override
    public void runTask(Plugin plugin, Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }
    
    @Override
    public void runTaskLater(Plugin plugin, Runnable runnable, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
    }
    
    @Override
    public void runEntityTask(Plugin plugin, Entity entity, Runnable runnable) {
        // For Bukkit, just run on main thread
        runTask(plugin, runnable);
    }
    
    @Override
    public boolean isPrimaryThread() {
        return Bukkit.isPrimaryThread();
    }
    
    @Override
    public String getSchedulerType() {
        return "Bukkit";
    }
}
