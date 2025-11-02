/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.inventory;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts inventory packets to hide player inventory contents from unauthenticated players.
 * Based on AuthMe's InventoryPacketAdapter but adapted for PacketEvents.
 * 
 * This approach is safer than manipulating the server-side inventory because:
 * - Server inventory remains unchanged, no risk of item loss
 * - All inventory update packets are intercepted automatically
 * - Client sees empty inventory until authentication
 * 
 * @author LibreLogin Contributors
 */
public class InventoryPacketAdapter extends PacketListenerAbstract {

    private static final int PLAYER_INVENTORY_WINDOW_ID = 0;
    
    // Inventory slot counts (http://wiki.vg/Inventory)
    // 0-4: Crafting slots (including result)
    // 5-8: Armor slots
    // 9-35: Main inventory
    // 36-44: Hotbar
    // 45: Offhand
    private static final int TOTAL_SLOTS = 46;

    private final PaperLibreLogin plugin;
    private final Set<UUID> hiddenInventoryPlayers = ConcurrentHashMap.newKeySet();
    // 保存玩家原始背包内容和虚拟地图物品
    private final Map<UUID, org.bukkit.inventory.ItemStack[]> savedInventories = new ConcurrentHashMap<>();
    // 跟踪哪些玩家有虚拟地图在主手
    private final Set<UUID> playersWithVirtualMap = ConcurrentHashMap.newKeySet();
    // 防止数据包循环的临时跳过集合
    private final Set<UUID> skipNextPacket = ConcurrentHashMap.newKeySet();

    public InventoryPacketAdapter(PaperLibreLogin plugin) {
        super(PacketListenerPriority.HIGH);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) {
            return;
        }

        User user = event.getUser();
        if (user == null) {
            return;
        }

        UUID playerUuid = user.getUUID();
        if (playerUuid == null || !hiddenInventoryPlayers.contains(playerUuid)) {
            return;
        }

        // 防循环检查：如果这个玩家被标记为跳过下一个包，则跳过处理
        if (skipNextPacket.remove(playerUuid)) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Skipped packet processing to prevent loop for player: " + ((Player) event.getPlayer()).getName());
            }
            return;
        }

        // Intercept window items packet (full inventory update)
        if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems windowItems = new WrapperPlayServerWindowItems(event);
            
            // Only intercept player inventory window (ID 0)
            if (windowItems.getWindowId() == PLAYER_INVENTORY_WINDOW_ID) {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    Player player = (Player) event.getPlayer();
                    if (player != null) {
                        plugin.getLogger().debug("Intercepted WINDOW_ITEMS packet for player: " + player.getName());
                    }
                }
                
                // Cancel the original packet
                event.setCancelled(true);
                
                // 获取原始物品并处理虚拟地图显示
                List<ItemStack> originalItems = windowItems.getItems();
                List<ItemStack> modifiedItems = new ArrayList<>(TOTAL_SLOTS);
                
                for (int i = 0; i < TOTAL_SLOTS; i++) {
                    if (i == 36 && playersWithVirtualMap.contains(playerUuid) && i < originalItems.size()) {
                        // 如果有虚拟地图且是主手槽位，保留物品显示
                        modifiedItems.add(originalItems.get(i));
                    } else {
                        // 隐藏所有其他槽位
                        modifiedItems.add(ItemStack.EMPTY);
                    }
                }
                
                WrapperPlayServerWindowItems newPacket = new WrapperPlayServerWindowItems(
                    PLAYER_INVENTORY_WINDOW_ID,
                    windowItems.getStateId(), // Keep same state ID
                    modifiedItems,
                    ItemStack.EMPTY // Cursor item
                );
                
                // 标记跳过下一个数据包以防止循环
                skipNextPacket.add(playerUuid);
                
                // Use writePacket instead of sendPacket to bypass event system and avoid infinite loop
                user.writePacket(newPacket);
                
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    String displayMode = playersWithVirtualMap.contains(playerUuid) ? "with virtual map in slot 36" : "all slots hidden";
                    plugin.getLogger().debug("Replaced WINDOW_ITEMS packet, " + displayMode);
                }
            }
        }
        // Intercept set slot packet (single slot update)
        else if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot setSlot = new WrapperPlayServerSetSlot(event);
            
            // Only intercept player inventory window (ID 0)
            if (setSlot.getWindowId() == PLAYER_INVENTORY_WINDOW_ID) {
                int slot = setSlot.getSlot();
                
                // 检查是否是虚拟地图物品更新（由VirtualMapProjector发送）
                if (slot == 36 && isVirtualMapUpdate(playerUuid)) {
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        Player player = (Player) event.getPlayer();
                        if (player != null) {
                            plugin.getLogger().debug("Allowing virtual map SET_SLOT for slot 36 for player: " + player.getName());
                        }
                    }
                    // Let virtual map packet through
                    return;
                }
                
                // Block all other slot updates
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    Player player = (Player) event.getPlayer();
                    if (player != null) {
                        plugin.getLogger().debug("Blocked SET_SLOT for slot " + slot + " for player: " + player.getName());
                    }
                }
                event.setCancelled(true);
            }
        }
    }

    /**
     * 检查是否是虚拟地图的SET_SLOT更新
     * 
     * @param playerUuid 玩家UUID
     * @return 如果玩家有虚拟地图则返回true
     */
    private boolean isVirtualMapUpdate(UUID playerUuid) {
        return playersWithVirtualMap.contains(playerUuid);
    }

    /**
     * 标记玩家有虚拟地图物品
     * 
     * @param player 玩家
     */
    public void markVirtualMapActive(Player player) {
        playersWithVirtualMap.add(player.getUniqueId());
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Marked virtual map as active for player: " + player.getName());
        }
    }

    /**
     * 清除玩家的虚拟地图标记
     * 
     * @param player 玩家
     */
    public void clearVirtualMapActive(Player player) {
        playersWithVirtualMap.remove(player.getUniqueId());
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Cleared virtual map active status for player: " + player.getName());
        }
    }

    /**
     * Hides the inventory for a player by sending a blank inventory packet
     * and registering them for packet interception.
     * 
     * 保存玩家的原始背包内容，以便登录后恢复。
     *
     * @param player the player whose inventory should be hidden
     */
    public void hideInventory(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (hiddenInventoryPlayers.contains(uuid)) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Inventory already hidden for player: " + player.getName());
            }
            return;
        }

        // 保存玩家的完整原始背包内容
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
        org.bukkit.inventory.ItemStack[] contents = inventory.getContents();
        
        // 深拷贝背包内容
        org.bukkit.inventory.ItemStack[] savedContents = new org.bukkit.inventory.ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                savedContents[i] = contents[i].clone();
            }
        }
        savedInventories.put(uuid, savedContents);

        // Add to tracking set
        hiddenInventoryPlayers.add(uuid);

        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Saved and hiding inventory for player: " + player.getName());
        }

        // Send blank inventory packet to client
        sendBlankInventoryPacket(player);
    }


    /**
     * Restores the inventory visibility for a player.
     * The real inventory will be sent automatically by the server.
     *
     * @param player the player whose inventory should be restored
     */
    public void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (!hiddenInventoryPlayers.remove(uuid)) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("No hidden inventory found for player: " + player.getName());
            }
            return;
        }

        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Restoring inventory for player: " + player.getName());
        }

        // 恢复玩家的原始背包内容
        org.bukkit.inventory.ItemStack[] savedContents = savedInventories.remove(uuid);
        if (savedContents != null) {
            // 恢复服务器端背包内容
            org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
            inventory.setContents(savedContents);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Restored original inventory contents from saved data for player: " + player.getName());
            }
        }
        
        // 清理虚拟地图标记
        playersWithVirtualMap.remove(uuid);
        
        // 强制客户端更新背包显示
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user != null) {
            // 延迟更新确保客户端正确接收
            plugin.delay(() -> {
                player.updateInventory();
            }, 50);
        }
    }

    /**
     * Checks if a player's inventory is currently hidden.
     *
     * @param player the player to check
     * @return true if the inventory is hidden
     */
    public boolean isInventoryHidden(Player player) {
        return hiddenInventoryPlayers.contains(player.getUniqueId());
    }

    /**
     * Cleans up tracking data for a player (e.g., when they disconnect).
     *
     * @param player the player
     */
    public void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        boolean removed = hiddenInventoryPlayers.remove(uuid);
        
        // 清理保存的背包数据
        savedInventories.remove(uuid);
        
        // 清理虚拟地图标记
        playersWithVirtualMap.remove(uuid);
        
        // 清理防循环标记
        skipNextPacket.remove(uuid);
        
        if (removed && plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Cleaned up inventory hiding and saved data for player: " + player.getName());
        }
    }

    /**
     * Gets the number of players with hidden inventories.
     *
     * @return the count
     */
    public int getHiddenInventoryCount() {
        return hiddenInventoryPlayers.size();
    }

    /**
     * Sends a blank inventory packet to the player's client.
     * This makes the client display an empty inventory.
     *
     * @param player the player
     */
    private void sendBlankInventoryPacket(Player player) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().warn("PacketEvents user is null for player: " + player.getName());
            }
            return;
        }

        // Create empty inventory
        List<ItemStack> items = new ArrayList<>(TOTAL_SLOTS);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            items.add(ItemStack.EMPTY);
        }

        WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(
                PLAYER_INVENTORY_WINDOW_ID,
                0, // State ID
                items,
                ItemStack.EMPTY
        );

        try {
            // Use writePacket to bypass event system
            user.writePacket(packet);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Sent blank inventory packet to player: " + player.getName());
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error sending blank inventory packet to " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Unregisters this packet listener from PacketEvents.
     */
    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        hiddenInventoryPlayers.clear();
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("InventoryPacketAdapter unregistered");
        }
    }
}

