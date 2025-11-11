package com.magicbili.animationscoreloginpatch;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * AnimationsCore API 访问类
 * 使用 AnimationsCore 2.5.0+ 的 API 来触发动画和获取配置
 */
public class AnimationsCoreReflection {
    
    private final Plugin animationsCorePlugin;
    private final JavaPlugin plugin;
    private boolean initialized = false;
    
    public AnimationsCoreReflection(Plugin animationsCorePlugin, JavaPlugin plugin) {
        this.animationsCorePlugin = animationsCorePlugin;
        this.plugin = plugin;
    }
    
    /**
     * 初始化 API 访问
     * 使用 AnimationsCore API 的 playAnimation 方法，避免创建 JoinAnimation 实例
     * 这样可以防止注册事件监听器
     */
    public boolean initialize(JavaPlugin plugin) {
        if (initialized) {
            return true;
        }
        
        try {
            // 使用 API 方式，避免创建 JoinAnimation 实例
            org.bukkit.plugin.ServicesManager servicesManager = Bukkit.getServicesManager();
            Class<?> apiClass = Class.forName("net.novua.animationscore.api.AnimationsCoreAPI");
            org.bukkit.plugin.RegisteredServiceProvider<?> registration = servicesManager.getRegistration(apiClass);
            if (registration != null) {
                Object apiInstance = registration.getProvider();
                if (apiInstance != null) {
                    // 验证 API 方法是否存在
                    // playAnimation(Player, Location, String) 返回 UUID
                    Method playAnimationMethod = apiClass.getMethod("playAnimation", Player.class, Location.class, String.class);
                    Method getPreferenceMethod = apiClass.getMethod("getPreference", UUID.class, String.class);
                    if (playAnimationMethod != null && getPreferenceMethod != null) {
                        plugin.getLogger().info("检测到 AnimationsCore 2.5.0+ API，将使用 API 方式触发动画");
                        plugin.getLogger().info("使用 playAnimation(Player, Location, String) 方法，不会注册事件监听器");
                        initialized = true;
                        return true;
                    }
                }
            }
            
            plugin.getLogger().severe("无法找到 AnimationsCore API！请确保使用 AnimationsCore 2.5.0+ 版本");
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("初始化 AnimationsCore 访问失败: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    /**
     * 触发加入动画
     * 使用 AnimationsCore API 的 playAnimation 方法
     * 不会创建 JoinAnimation 实例，因此不会注册事件监听器
     * 
     * 参考示例：
     * UUID mobId = animationsApi.playAnimation(player, location, "AnimationName");
     */
    public void triggerJoinAnimation(Player player) {
        try {
            // 获取玩家的 join 动画配置
            String animation = getPlayerJoinAnimation(player);
            if (animation == null || animation.trim().isEmpty()) {
                if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("玩家 " + player.getName() + " 没有 join 动画配置，跳过触发");
                }
                return;
            }
            
            // 计算动画播放位置：优先使用 AnimationsCore 的 spawn-point 配置
            Location animationLocation = null;
            try {
                org.bukkit.configuration.file.FileConfiguration acConfig = animationsCorePlugin.getConfig();
                boolean spawnEnabled = acConfig.getBoolean("spawn-point.enabled", false);
                if (spawnEnabled) {
                    String worldName = acConfig.getString("spawn-point.world", "");
                    double x = acConfig.getDouble("spawn-point.x", 0.0);
                    double y = acConfig.getDouble("spawn-point.y", 0.0);
                    double z = acConfig.getDouble("spawn-point.z", 0.0);
                    float yaw = (float) acConfig.getDouble("spawn-point.yaw", 0.0);
                    float pitch = (float) acConfig.getDouble("spawn-point.pitch", 0.0);
                    
                    org.bukkit.World world = (worldName == null || worldName.isEmpty()) ? player.getWorld() : org.bukkit.Bukkit.getWorld(worldName);
                    if (world != null) {
                        Location spawnLoc = new Location(world, x, y, z, yaw, pitch);
                        // 先把玩家传送到 spawn，以确保动画相机/实体位置正确
                        player.teleport(spawnLoc);
                        animationLocation = spawnLoc.clone().add(0, 0.5, 0);
                        
                        if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("已将玩家 " + player.getName() + " 传送至 AnimationsCore spawn-point: "
                                    + world.getName() + " (" + x + ", " + y + ", " + z + ", " + yaw + ", " + pitch + ")");
                        }
                    } else {
                        if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().warning("AnimationsCore spawn-point.world 无法找到对应世界: " + worldName + "，回退至玩家当前位置");
                        }
                    }
                }
            } catch (Exception ignored) {
                // 出现任何异常均回退到玩家当前位置
            }
            
            // 若未能从配置确定位置，则使用玩家当前位置（上方 0.5 格）
            if (animationLocation == null) {
                animationLocation = player.getLocation().add(0, 0.5, 0);
            }
            
            // 使用 API 的 playAnimation 方法
            org.bukkit.plugin.ServicesManager servicesManager = Bukkit.getServicesManager();
            Class<?> apiClass = Class.forName("net.novua.animationscore.api.AnimationsCoreAPI");
            org.bukkit.plugin.RegisteredServiceProvider<?> registration = servicesManager.getRegistration(apiClass);
            if (registration != null) {
                Object apiInstance = registration.getProvider();
                if (apiInstance != null) {
                    // 使用 playAnimation(Player, Location, String) 方法
                    // 返回 UUID（可用于后续清理动画）
                    Method playAnimationMethod = apiClass.getMethod("playAnimation", Player.class, Location.class, String.class);
                    UUID mobId = (UUID) playAnimationMethod.invoke(apiInstance, player, animationLocation, animation);
                    
                    if (mobId != null) {
                        if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().info("已通过 API 为玩家 " + player.getName() + " 触发加入动画 (动画: " + animation + ", mobId: " + mobId + ")");
                            plugin.getLogger().info("使用 playAnimation(Player, Location, String) 方法，不会注册事件监听器");
                        }
                        // 注意：mobId 可以用于后续清理动画，但目前不需要
                    } else {
                        if (plugin != null && plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().warning("通过 API 触发动画失败，返回的 mobId 为 null，玩家: " + player.getName() + "，动画: " + animation);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().severe("通过 API 触发动画失败: " + e.getMessage());
                if (plugin.getConfig().getBoolean("debug", false)) {
                    e.printStackTrace();
                }
            } else {
                animationsCorePlugin.getLogger().severe("通过 API 触发动画失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 获取玩家的 join 动画配置
     * 使用 API 的 getPreference 方法（不受触发器开关影响）
     */
    public String getPlayerJoinAnimation(Player player) {
        // 使用 API 的 getPreference 方法
        try {
            org.bukkit.plugin.ServicesManager servicesManager = Bukkit.getServicesManager();
            Class<?> apiClass = Class.forName("net.novua.animationscore.api.AnimationsCoreAPI");
            org.bukkit.plugin.RegisteredServiceProvider<?> registration = servicesManager.getRegistration(apiClass);
            if (registration != null) {
                Object apiInstance = registration.getProvider();
                if (apiInstance != null) {
                    // 使用 API 的 getPreference 方法（不受触发器开关影响）
                    Method getPreferenceMethod = apiClass.getMethod("getPreference", UUID.class, String.class);
                    Object optional = getPreferenceMethod.invoke(apiInstance, player.getUniqueId(), "join");
                    if (optional != null) {
                        // Optional<String> 的处理
                        Method isPresentMethod = optional.getClass().getMethod("isPresent");
                        Method getMethod = optional.getClass().getMethod("get");
                        if ((Boolean) isPresentMethod.invoke(optional)) {
                            String animation = (String) getMethod.invoke(optional);
                            if (animation != null && !animation.trim().isEmpty()) {
                                return animation;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // API 方式失败，回退到直接读取配置文件
        }
        
        // 回退到直接读取配置文件（如果 API 不可用）
        try {
            java.io.File prefsFile = new java.io.File(animationsCorePlugin.getDataFolder(), "player_preferences.yml");
            if (!prefsFile.exists()) {
                return null;
            }
            
            org.bukkit.configuration.file.YamlConfiguration prefs = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(prefsFile);
            String uuidStr = player.getUniqueId().toString();
            
            // AnimationsCore 使用配置节结构：{uuid}.join
            if (prefs.contains(uuidStr)) {
                org.bukkit.configuration.ConfigurationSection section = prefs.getConfigurationSection(uuidStr);
                if (section != null && section.contains("join")) {
                    String animation = section.getString("join");
                    if (animation != null && !animation.trim().isEmpty()) {
                        return animation;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }
}
