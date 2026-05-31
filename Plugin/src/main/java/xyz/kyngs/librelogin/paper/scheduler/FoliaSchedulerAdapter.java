/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import xyz.kyngs.librelogin.common.util.CancellableTask;

import java.util.concurrent.TimeUnit;

/**
 * Folia scheduler adapter implementation.
 * Uses Folia's region-based threading model with async, global, and entity schedulers.
 */
public class FoliaSchedulerAdapter implements SchedulerAdapter {
    
    @Override
    public CancellableTask runTaskLaterAsynchronously(Plugin plugin, Runnable runnable, long delayTicks) {
        // Convert ticks to milliseconds (1 tick = 50ms)
        long delayMs = delayTicks * 50;
        var task = Bukkit.getAsyncScheduler().runDelayed(plugin, (t) -> runnable.run(), delayMs, TimeUnit.MILLISECONDS);
        return task::cancel;
    }
    
    @Override
    public CancellableTask runTaskTimerAsynchronously(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        // Convert ticks to milliseconds
        long delayMs = delayTicks * 50;
        long periodMs = periodTicks * 50;
        var task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (t) -> runnable.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        return task::cancel;
    }
    
    @Override
    public void runTask(Plugin plugin, Runnable runnable) {
        // Use global region scheduler for non-entity-specific tasks
        Bukkit.getGlobalRegionScheduler().run(plugin, (t) -> runnable.run());
    }
    
    @Override
    public void runTaskLater(Plugin plugin, Runnable runnable, long delayTicks) {
        // Use global region scheduler with delay
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> runnable.run(), delayTicks);
    }
    
    @Override
    public void runEntityTask(Plugin plugin, Entity entity, Runnable runnable) {
        // Use entity scheduler for entity-specific operations
        // This ensures the task runs on the correct region thread for the entity
        entity.getScheduler().run(plugin, (t) -> runnable.run(), null);
    }
    
    @Override
    public boolean isPrimaryThread() {
        // In Folia, there's no single "primary thread"
        // Check if we're on a region thread (ticking thread)
        // Use a location-based check instead of null parameter
        try {
            // Try to check if we're on a region thread by using the overworld spawn location
            var world = Bukkit.getWorlds().get(0); // Get the first world (usually overworld)
            if (world != null) {
                var spawnLocation = world.getSpawnLocation();
                return Bukkit.isOwnedByCurrentRegion(spawnLocation);
            }
        } catch (Exception e) {
            // Fallback: if we can't determine region ownership, assume we're not on a region thread
        }
        return false;
    }
    
    @Override
    public String getSchedulerType() {
        return "Folia";
    }
}
