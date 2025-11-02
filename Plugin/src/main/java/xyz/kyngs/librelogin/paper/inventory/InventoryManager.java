/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.inventory;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

/**
 * Manages player inventory visibility during authentication.
 * Uses packet interception to hide inventory from unauthenticated players.
 * 
 * This implementation is based on AuthMe's approach:
 * - Intercepts outgoing inventory packets to prevent real inventory from being sent
 * - Sends blank inventory packets to client
 * - Does NOT modify server-side inventory (safer, no item loss risk)
 * - Automatically handles all inventory updates
 *
 * @author LibreLogin Contributors
 */
public class InventoryManager {

    private final PaperLibreLogin plugin;
    private final InventoryPacketAdapter packetAdapter;

    public InventoryManager(PaperLibreLogin plugin) {
        this.plugin = plugin;
        this.packetAdapter = new InventoryPacketAdapter(plugin);
    }

    /**
     * Initializes the inventory manager by registering the packet listener.
     * Must be called after PacketEvents is initialized.
     */
    public void enable() {
        PacketEvents.getAPI().getEventManager().registerListener(packetAdapter);
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("InventoryManager packet listener registered");
        }
    }

    /**
     * Disables the inventory manager by unregistering the packet listener.
     */
    public void disable() {
        packetAdapter.unregister();
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("InventoryManager packet listener unregistered");
        }
    }

    /**
     * Hides the player's inventory by intercepting inventory packets
     * and sending a blank inventory to the client.
     * Server-side inventory remains unchanged (safer approach).
     *
     * @param player the player whose inventory should be hidden
     */
    public void hideInventory(Player player) {
        packetAdapter.hideInventory(player);
    }


    /**
     * Restores the player's inventory visibility.
     * The server will automatically send the real inventory data.
     *
     * @param player the player whose inventory should be restored
     */
    public void restoreInventory(Player player) {
        packetAdapter.restoreInventory(player);
    }

    /**
     * Checks if a player's inventory is currently hidden.
     *
     * @param player the player to check
     * @return true if the inventory is hidden
     */
    public boolean isInventoryHidden(Player player) {
        return packetAdapter.isInventoryHidden(player);
    }

    /**
     * Cleans up inventory hiding data for a player.
     * Used when player disconnects.
     *
     * @param player the player
     */
    public void cleanup(Player player) {
        packetAdapter.cleanup(player);
    }

    /**
     * Gets the number of players with hidden inventories.
     *
     * @return the count
     */
    public int getHiddenInventoryCount() {
        return packetAdapter.getHiddenInventoryCount();
    }

    /**
     * 标记玩家有虚拟地图物品
     *
     * @param player the player
     */
    public void markVirtualMapActive(Player player) {
        packetAdapter.markVirtualMapActive(player);
    }

    /**
     * 清除玩家的虚拟地图标记
     *
     * @param player the player
     */
    public void clearVirtualMapActive(Player player) {
        packetAdapter.clearVirtualMapActive(player);
    }
}

