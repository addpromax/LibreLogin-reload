/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.scheduler;

/**
 * Factory for creating the appropriate scheduler adapter based on the server platform.
 * Automatically detects whether the server is running Folia or traditional Bukkit/Paper.
 */
public class SchedulerFactory {
    
    private static Boolean isFolia = null;
    
    /**
     * Detects if the server is running Folia by checking for Folia-specific classes.
     *
     * @return true if running on Folia, false otherwise
     */
    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                // Try to load Folia-specific class
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }
    
    /**
     * Creates the appropriate scheduler adapter for the current server platform.
     *
     * @return a BukkitSchedulerAdapter for Bukkit/Paper, or FoliaSchedulerAdapter for Folia
     */
    public static SchedulerAdapter createScheduler() {
        if (isFolia()) {
            return new FoliaSchedulerAdapter();
        } else {
            return new BukkitSchedulerAdapter();
        }
    }
}
