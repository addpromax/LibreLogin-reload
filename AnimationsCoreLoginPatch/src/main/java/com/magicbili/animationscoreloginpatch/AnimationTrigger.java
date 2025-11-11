package com.magicbili.animationscoreloginpatch;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动画触发器
 * 负责触发 AnimationsCore 的加入动画
 * 使用 AnimationsCore 2.5.0+ API
 */
public class AnimationTrigger {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final AnimationsCoreReflection animationsCoreReflection;
    private final LibreLoginPlugin<Player, World> libreLoginPlugin;
	private final TeleportManager teleportManager;
    private final Map<UUID, BukkitTask> pendingAnimations = new ConcurrentHashMap<>();
    
    public AnimationTrigger(JavaPlugin plugin, ConfigManager configManager, 
	                       AnimationsCoreReflection animationsCoreReflection,
	                       LibreLoginPlugin<Player, World> libreLoginPlugin,
	                       TeleportManager teleportManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.animationsCoreReflection = animationsCoreReflection;
        this.libreLoginPlugin = libreLoginPlugin;
		this.teleportManager = teleportManager;
    }
    
    /**
     * 延迟触发加入动画
     */
    public void triggerJoinAnimationWithDelay(Player player) {
        UUID uuid = player.getUniqueId();
        long delay = configManager.getAnimationDelayTicks();
        
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingAnimations.remove(uuid);
            if (player.isOnline() && libreLoginPlugin.getAuthorizationProvider().isAuthorized(player)) {
                triggerJoinAnimation(player);
            } else {
                if (configManager.isDebug()) {
                    plugin.getLogger().warning("玩家 " + player.getName() + " 不在线或未授权，无法触发动画");
                }
            }
        }, delay);
        
        pendingAnimations.put(uuid, task);
    }
    
    /**
     * 触发加入动画
     * 使用 AnimationsCore API
     */
    public void triggerJoinAnimation(Player player) {
		// 先尝试传送到分配的登录点位（全局轮转）
		boolean teleported = false;
		if (teleportManager != null) {
			try {
				teleported = teleportManager.teleportPlayerToAssignedPoint(player);
			} catch (Exception e) {
				if (configManager.isDebug()) {
					plugin.getLogger().warning("传送到登录点位失败: " + e.getMessage());
				}
			}
		}
		if (!teleported && configManager.isDebug()) {
			plugin.getLogger().info("未找到有效登录点位，使用玩家当前位置播放动画");
		}
		
        // 获取玩家的 join 动画配置
        String animation = animationsCoreReflection.getPlayerJoinAnimation(player);
        if (animation == null || animation.trim().isEmpty()) {
            if (configManager.isDebug()) {
                plugin.getLogger().info("玩家 " + player.getName() + " 没有 join 动画配置，跳过触发动画");
            }
            return;
        }
        
        // 使用 API 触发动画
        animationsCoreReflection.triggerJoinAnimation(player);
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("已为玩家 " + player.getName() + " 触发加入动画 (动画: " + animation + ")");
        }
    }
    
    /**
     * 获取玩家当前的动画配置
     */
    public String getCurrentAnimation(Player player) {
        return animationsCoreReflection.getPlayerJoinAnimation(player);
    }
    
    /**
     * 清理玩家的待处理任务
     */
    public void cleanup() {
        pendingAnimations.values().forEach(BukkitTask::cancel);
        pendingAnimations.clear();
    }
}
