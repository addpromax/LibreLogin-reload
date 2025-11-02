/*
 * This file is part of LibreLogin.
 *
 * LibreLogin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreLogin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreLogin.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.kyngs.librelogin.paper.dialogs;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

/**
 * PacketEvents监听器，用于监听虚拟地图的丢出操作
 * 解决虚拟地图物品无法触发Bukkit事件的问题
 */
public class VirtualMapDropListener extends PacketListenerAbstract {

    private final PaperLibreLogin plugin;
    private final DialogManager dialogManager;

    public VirtualMapDropListener(PaperLibreLogin plugin, DialogManager dialogManager) {
        super();
        this.plugin = plugin;
        this.dialogManager = dialogManager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        // 只处理PLAYER_DIGGING包
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_DIGGING) {
            return;
        }

        Player player = (Player) event.getPlayer();
        if (player == null) {
            return;
        }

        // 解析丢出动作
        WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
        DiggingAction action = digging.getAction();

        // 🔧 监听Q键丢出动作
        if (action == DiggingAction.DROP_ITEM || action == DiggingAction.DROP_ITEM_STACK) {
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("=== PACKET DROP LISTENER DEBUG ===");
                plugin.getLogger().debug("Player: " + player.getName() + " sent " + action + " packet");
                plugin.getLogger().debug("Has pending 2FA: " + dialogManager.hasPendingTwoFactorSetup(player));
            }

            // 检查是否有pending 2FA setup
            if (dialogManager.hasPendingTwoFactorSetup(player)) {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("✅ Virtual map drop detected via packet - triggering 2FA dialog");
                    plugin.getLogger().debug("Action: " + action + " for player: " + player.getName());
                }

                // 获取pending setup数据
                DialogManager.PendingTwoFactorSetup pending = dialogManager.getPendingTwoFactorSetup(player);
                if (pending != null) {
                    // 取消数据包以防止实际丢出
                    event.setCancelled(true);
                    
                    // 清理虚拟地图状态
                    plugin.getVirtualMapProjector().cleanupVirtualMap(player);
                    plugin.getInventoryManager().restoreInventory(player);
                    
                    // 移除pending状态
                    dialogManager.cancelPendingTwoFactorSetup(player);

                    // 延迟打开dialog
                    plugin.delay(() -> {
                        dialogManager.showTwoFactorSetupDialog(player, pending.user, pending.totpData);
                        
                        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                            plugin.getLogger().debug("🎯 2FA setup dialog opened for player: " + player.getName());
                        }
                    }, 50);
                }
            }
        }
    }
}
