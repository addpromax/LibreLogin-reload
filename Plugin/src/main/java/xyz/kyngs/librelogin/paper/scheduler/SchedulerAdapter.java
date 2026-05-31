/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.scheduler;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import xyz.kyngs.librelogin.common.util.CancellableTask;

/**
 * Scheduler adapter interface for supporting both Bukkit and Folia schedulers.
 * This abstraction allows LibreLogin to work on both traditional Paper/Spigot servers
 * and Folia servers with their region-based threading model.
 */
public interface SchedulerAdapter {
    
    /**
     * Runs a task asynchronously after a delay.
     *
     * @param plugin the plugin instance
     * @param runnable the task to run
     * @param delayTicks the delay in ticks (20 ticks = 1 second)
     * @return a cancellable task
     */
    CancellableTask runTaskLaterAsynchronously(Plugin plugin, Runnable runnable, long delayTicks);
    
    /**
     * Runs a task asynchronously at a fixed rate.
     *
     * @param plugin the plugin instance
     * @param runnable the task to run
     * @param delayTicks the initial delay in ticks
     * @param periodTicks the period between executions in ticks
     * @return a cancellable task
     */
    CancellableTask runTaskTimerAsynchronously(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks);
    
    /**
     * Runs a task on the main thread (or appropriate region thread for Folia).
     *
     * @param plugin the plugin instance
     * @param runnable the task to run
     */
    void runTask(Plugin plugin, Runnable runnable);
    
    /**
     * Runs a task on the main thread (or appropriate region thread for Folia) after a delay.
     *
     * @param plugin the plugin instance
     * @param runnable the task to run
     * @param delayTicks the delay in ticks
     */
    void runTaskLater(Plugin plugin, Runnable runnable, long delayTicks);
    
    /**
     * Runs a task on the entity's thread (for Folia) or main thread (for Bukkit).
     * This is used for entity-specific operations that must be thread-safe.
     *
     * @param plugin the plugin instance
     * @param entity the entity to run the task for
     * @param runnable the task to run
     */
    void runEntityTask(Plugin plugin, Entity entity, Runnable runnable);
    
    /**
     * Checks if the current thread is the main thread (Bukkit) or a valid region thread (Folia).
     *
     * @return true if on the primary/region thread
     */
    boolean isPrimaryThread();
    
    /**
     * Gets the scheduler type name for debugging purposes.
     *
     * @return "Bukkit" or "Folia"
     */
    String getSchedulerType();
}
