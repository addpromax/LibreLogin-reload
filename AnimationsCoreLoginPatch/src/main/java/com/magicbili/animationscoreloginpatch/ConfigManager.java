package com.magicbili.animationscoreloginpatch;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 配置管理器
 * 管理插件的所有配置项
 */
public class ConfigManager {
    
    private final JavaPlugin plugin;
    
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 获取调试模式
     */
    public boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }
    
    /**
     * 获取动画延迟（tick数）
     */
    public long getAnimationDelayTicks() {
        return plugin.getConfig().getLong("animation-delay-ticks", 10L);
    }
    
    /**
     * 获取配置对象
     */
    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }
}

