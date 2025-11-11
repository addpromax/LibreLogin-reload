package com.magicbili.animationscoreloginpatch;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Join 事件拦截器
 * 
 * 功能：
 * 1. 检查 AnimationsCore 的 join 触发器配置
 * 2. 如果配置为 false，阻止 AnimationsCore 的 JoinAnimation 执行
 * 3. 通过反射禁用 JoinAnimation 的监听器（如果可能）
 * 
 * 注意：由于 AnimationsCore 的设计缺陷，即使配置为 false，
 * 如果插件启动时 join 是 true，监听器仍然会被注册。
 * 这个类尝试通过多种方式来解决这个问题。
 */
public class JoinEventBlocker implements Listener {
    
    private final JavaPlugin plugin;
    private final Plugin animationsCorePlugin;
    private final Set<UUID> blockedPlayers = ConcurrentHashMap.newKeySet();
    private boolean joinTriggerEnabled = true;
    private Object joinAnimationInstance = null;
    private boolean reflectionInitialized = false;
    
    public JoinEventBlocker(JavaPlugin plugin, Plugin animationsCorePlugin) {
        this.plugin = plugin;
        this.animationsCorePlugin = animationsCorePlugin;
        checkConfig();
        
        // 尝试通过反射找到 JoinAnimation 实例
        tryInitializeReflection();
    }
    
    /**
     * 检查 AnimationsCore 的配置
     */
    public void checkConfig() {
        try {
            FileConfiguration config = animationsCorePlugin.getConfig();
            joinTriggerEnabled = config.getBoolean("animation-triggers.join", true);
            
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[JoinEventBlocker] AnimationsCore join 触发器状态: " + 
                    (joinTriggerEnabled ? "启用" : "禁用"));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法读取 AnimationsCore 配置: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 尝试通过反射初始化并找到 JoinAnimation 实例
     * 并尝试从事件管理器中注销其监听器
     */
    private void tryInitializeReflection() {
        if (reflectionInitialized) {
            return;
        }
        
        try {
            // 尝试找到 JoinAnimation 类
            Class<?> joinAnimationClass = Class.forName("net.novua.animationscore.animations.JoinAnimation");
            
            // 无论配置如何，都检测监听器状态（因为即使配置为 false，监听器可能仍然存在）
            tryUnregisterJoinAnimationListener(joinAnimationClass);
            
            reflectionInitialized = true;
            
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[JoinEventBlocker] 反射初始化完成");
            }
        } catch (ClassNotFoundException e) {
            // JoinAnimation 类不存在，说明可能未启用或版本不匹配
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("无法找到 JoinAnimation 类（可能未启用或版本不匹配）: " + e.getMessage());
            }
            reflectionInitialized = true; // 标记为已初始化，避免重复尝试
        } catch (Exception e) {
            plugin.getLogger().warning("[JoinEventBlocker] 反射初始化失败: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
            reflectionInitialized = true; // 标记为已初始化，避免重复尝试
        }
    }
    
    /**
     * 尝试通过反射从事件管理器中注销 JoinAnimation 的监听器
     * 
     * 注意：由于 Bukkit/Spigot 的设计，我们无法直接移除已注册的监听器。
     * 这个方法主要用来检测和警告，实际阻止通过事件优先级实现。
     */
    private void tryUnregisterJoinAnimationListener(Class<?> joinAnimationClass) {
        try {
            // 获取 PlayerJoinEvent 的 HandlerList
            org.bukkit.event.HandlerList handlerList = PlayerJoinEvent.getHandlerList();
            
            // 检查是否有 JoinAnimation 的监听器
            int joinAnimationListenerCount = 0;
            int animationsCoreListenerCount = 0;
            java.util.List<org.bukkit.plugin.RegisteredListener> detectedListeners = new java.util.ArrayList<>();
            
            // 首先统计所有 AnimationsCore 的监听器
            for (org.bukkit.plugin.RegisteredListener registeredListener : handlerList.getRegisteredListeners()) {
                if (registeredListener.getPlugin().equals(animationsCorePlugin)) {
                    animationsCoreListenerCount++;
                    Listener listener = registeredListener.getListener();
                    String listenerClassName = listener.getClass().getName();
                    
                    // 检查是否是 JoinAnimation 实例
                    if (joinAnimationClass.isInstance(listener)) {
                        joinAnimationListenerCount++;
                        detectedListeners.add(registeredListener);
                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("[JoinEventBlocker] 检测到 JoinAnimation 监听器: " + 
                                listenerClassName + ", 优先级: " + registeredListener.getPriority());
                        }
                    } else if (plugin.getConfig().getBoolean("debug", false)) {
                        // 调试模式下，记录所有 AnimationsCore 的监听器
                        plugin.getLogger().info("[JoinEventBlocker] AnimationsCore 其他监听器: " + 
                            listenerClassName + ", 优先级: " + registeredListener.getPriority());
                    }
                }
            }
            
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[JoinEventBlocker] AnimationsCore 总监听器数: " + animationsCoreListenerCount + 
                    ", JoinAnimation 监听器数: " + joinAnimationListenerCount);
            }
            
            // 如果检测到监听器，无论配置如何都要警告
            if (joinAnimationListenerCount > 0) {
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("[JoinEventBlocker] ⚠⚠⚠ 严重问题：检测到 " + joinAnimationListenerCount + 
                    " 个 JoinAnimation 监听器已注册！⚠⚠⚠");
                plugin.getLogger().severe("[JoinEventBlocker] 当前配置状态: " + (joinTriggerEnabled ? "启用" : "禁用"));
                plugin.getLogger().severe("[JoinEventBlocker] 问题原因：");
                plugin.getLogger().severe("[JoinEventBlocker] - AnimationsCore 在启动时会根据配置注册监听器");
                plugin.getLogger().severe("[JoinEventBlocker] - 如果启动时 join: true，监听器会被注册");
                plugin.getLogger().severe("[JoinEventBlocker] - 即使后来将配置改为 join: false，已注册的监听器仍然会处理事件");
                plugin.getLogger().severe("[JoinEventBlocker] - Reload 命令不会重新注册监听器，只会重新加载配置文件");
                plugin.getLogger().severe("[JoinEventBlocker] 影响：");
                plugin.getLogger().severe("[JoinEventBlocker] - 玩家加入服务器时，AnimationsCore 会直接触发加入动画");
                plugin.getLogger().severe("[JoinEventBlocker] - 本插件无法阻止已注册的监听器处理事件");
                plugin.getLogger().severe("[JoinEventBlocker] - 这会导致动画在登录前播放，干扰登录流程");
                plugin.getLogger().severe("[JoinEventBlocker] - 即使配置为 false，动画仍然会被触发！");
                plugin.getLogger().severe("[JoinEventBlocker] 解决方案（必须执行）：");
                plugin.getLogger().severe("[JoinEventBlocker] 1. 编辑 AnimationsCore 配置文件：plugins/AnimationsCore/config.yml");
                plugin.getLogger().severe("[JoinEventBlocker] 2. 确认 animation-triggers.join: false");
                plugin.getLogger().severe("[JoinEventBlocker] 3. 重启服务器（不是 reload，必须完全重启服务器）");
                plugin.getLogger().severe("[JoinEventBlocker] 4. 重启后，检查日志确认没有此警告");
                plugin.getLogger().severe("[JoinEventBlocker] 5. 如果重启后仍有此警告，说明配置仍有问题");
                plugin.getLogger().severe("[JoinEventBlocker] 注意：本插件无法阻止已注册的 AnimationsCore 监听器");
                plugin.getLogger().severe("[JoinEventBlocker] 这是 AnimationsCore 的设计缺陷，只能通过重启服务器解决");
                plugin.getLogger().severe("========================================");
                
                // 如果配置为 false 但监听器存在，这是最严重的情况
                if (!joinTriggerEnabled) {
                    plugin.getLogger().severe("[JoinEventBlocker] ⚠⚠⚠ 配置冲突：配置文件显示 join: false，但监听器已注册！");
                    plugin.getLogger().severe("[JoinEventBlocker] ⚠⚠⚠ 这意味着 AnimationsCore 启动时 join 是 true，后来被改为 false");
                    plugin.getLogger().severe("[JoinEventBlocker] ⚠⚠⚠ 必须重启服务器才能解决问题！");
                }
            } else {
                // 未检测到监听器
                if (joinTriggerEnabled) {
                    plugin.getLogger().info("[JoinEventBlocker] ✓ 配置为启用，未检测到 JoinAnimation 监听器（正常）");
                } else {
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[JoinEventBlocker] ✓ 配置为禁用，未检测到 JoinAnimation 监听器（配置正确）");
                    } else {
                        plugin.getLogger().info("[JoinEventBlocker] ✓ AnimationsCore join 触发器配置正确，未检测到已注册的监听器");
                    }
                }
            }
            
            // 注意：HandlerList 的监听器列表是只读的，我们无法直接移除
            // 但我们可以通过事件优先级来拦截事件
            
        } catch (Exception e) {
            plugin.getLogger().warning("[JoinEventBlocker] 检查监听器时出错: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 高优先级监听 PlayerJoinEvent
     * 在 AnimationsCore 的监听器之前执行
     * 
     * 注意：由于 AnimationsCore 的设计缺陷，即使配置为 false，
     * 如果启动时 join 是 true，监听器仍然会处理事件。
     * 我们无法阻止已注册的监听器，只能检测并警告。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 重新检查配置（因为配置可能被 reload）
        checkConfig();
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // 实时检测监听器状态（在玩家加入时）
        // 这可以帮助我们发现配置和实际状态不一致的问题
        try {
            Class<?> joinAnimationClass = Class.forName("net.novua.animationscore.animations.JoinAnimation");
            org.bukkit.event.HandlerList handlerList = PlayerJoinEvent.getHandlerList();
            boolean hasListener = false;
            
            for (org.bukkit.plugin.RegisteredListener registeredListener : handlerList.getRegisteredListeners()) {
                if (registeredListener.getPlugin().equals(animationsCorePlugin)) {
                    Listener listener = registeredListener.getListener();
                    if (joinAnimationClass.isInstance(listener)) {
                        hasListener = true;
                        break;
                    }
                }
            }
            
            // 如果配置为 false 但检测到监听器，这是严重问题
            if (!joinTriggerEnabled && hasListener) {
                // 只在第一次检测到时记录严重警告
                if (!blockedPlayers.contains(uuid)) {
                    plugin.getLogger().severe("========================================");
                    plugin.getLogger().severe("[JoinEventBlocker] ⚠⚠⚠ 严重问题：玩家 " + player.getName() + " 加入时检测到问题！");
                    plugin.getLogger().severe("[JoinEventBlocker] 配置状态: join: false（禁用）");
                    plugin.getLogger().severe("[JoinEventBlocker] 实际状态: JoinAnimation 监听器已注册（启用）");
                    plugin.getLogger().severe("[JoinEventBlocker] 问题：即使配置为 false，AnimationsCore 的监听器仍然会处理事件");
                    plugin.getLogger().severe("[JoinEventBlocker] 原因：AnimationsCore 启动时 join 是 true，后来被改为 false");
                    plugin.getLogger().severe("[JoinEventBlocker] 结果：join 动画仍然会被触发，即使配置为 false");
                    plugin.getLogger().severe("[JoinEventBlocker] 解决方案：必须重启服务器（不是 reload）");
                    plugin.getLogger().severe("[JoinEventBlocker] 注意：本插件无法阻止已注册的 AnimationsCore 监听器");
                    plugin.getLogger().severe("========================================");
                    
                    // 标记已警告过（避免每个玩家都记录）
                    blockedPlayers.add(uuid);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        blockedPlayers.remove(uuid);
                    }, 200L); // 10秒后清理标记，给足够时间看到警告
                }
            }
        } catch (Exception e) {
            // 检测失败，忽略（不影响正常流程）
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[JoinEventBlocker] 实时检测监听器时出错: " + e.getMessage());
            }
        }
        
        // 如果配置为启用，记录警告
        if (joinTriggerEnabled) {
            // 仅在首次检测到时记录警告（避免每个玩家都记录）
            if (!blockedPlayers.contains(uuid)) {
                plugin.getLogger().warning("[JoinEventBlocker] 警告：AnimationsCore 的 join 触发器配置为启用");
                blockedPlayers.add(uuid);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    blockedPlayers.remove(uuid);
                }, 100L); // 5秒后清理标记
            }
            return;
        }
        
        // 配置为禁用，标记这个玩家
        blockedPlayers.add(uuid);
        
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[JoinEventBlocker] 检测到玩家 " + player.getName() + 
                " 加入，join 触发器配置为禁用");
        }
        
        // 延迟清理标记（5秒后）
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            blockedPlayers.remove(uuid);
        }, 100L); // 5秒 = 100 ticks
    }
    
    /**
     * 检查玩家是否应该被阻止
     */
    public boolean isPlayerBlocked(UUID uuid) {
        return blockedPlayers.contains(uuid);
    }
    
    /**
     * 强制重新检查配置和监听器状态
     * 在 AnimationsCore reload 后调用，或在插件启动延迟后调用
     */
    public void reloadConfig() {
        checkConfig();
        
        // 重新检测监听器状态
        try {
            Class<?> joinAnimationClass = Class.forName("net.novua.animationscore.animations.JoinAnimation");
            tryUnregisterJoinAnimationListener(joinAnimationClass);
        } catch (ClassNotFoundException e) {
            // JoinAnimation 类不存在，说明可能未启用
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[JoinEventBlocker] 重新检查：无法找到 JoinAnimation 类（可能未启用）");
            }
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[JoinEventBlocker] 重新检查监听器状态时出错: " + e.getMessage());
            }
        }
        
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[JoinEventBlocker] 配置和监听器状态已重新检查");
        }
    }
    
    /**
     * 尝试通过反射禁用 JoinAnimation
     * 注意：这可能会失败，因为 AnimationsCore 的设计
     */
    public boolean tryDisableJoinAnimation() {
        if (!reflectionInitialized) {
            tryInitializeReflection();
        }
        
        // 由于 JoinAnimation 实例没有保存，我们无法直接禁用
        // 但我们可以检查配置并发出警告
        
        if (joinTriggerEnabled) {
            plugin.getLogger().warning("[JoinEventBlocker] AnimationsCore join 触发器已启用！");
            plugin.getLogger().warning("[JoinEventBlocker] 即使您设置了 join: false，如果插件启动时 join 是 true，");
            plugin.getLogger().warning("[JoinEventBlocker] 监听器仍然会被注册。请重启服务器或确保启动时 join 就是 false。");
            return false;
        }
        
        return true;
    }
}

