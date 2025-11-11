package com.magicbili.animationscoreloginpatch;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.provider.LibreLoginProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * LibreLogin API 初始化类
 * 使用多种方法尝试获取 API 实例，以处理类加载器问题
 */
public class LibreLoginAPI {
    
    /**
     * 初始化 LibreLogin API
     */
    public static LibreLoginPlugin<Player, World> initialize(Plugin libreLoginBootstrap, JavaPlugin plugin) {
        plugin.getLogger().info("正在初始化 LibreLogin API...");
        
        // 方法1: 直接检查 instanceof
        try {
            if (libreLoginBootstrap instanceof LibreLoginProvider) {
                plugin.getLogger().info("方法1: 使用 instanceof 检查成功");
                @SuppressWarnings("unchecked")
                LibreLoginProvider<Player, World> provider = (LibreLoginProvider<Player, World>) libreLoginBootstrap;
                LibreLoginPlugin<Player, World> apiInstance = provider.getLibreLogin();
                
                if (apiInstance != null) {
                    plugin.getLogger().info("成功通过方法1获取 LibreLogin API 实例！");
                    return apiInstance;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("方法1失败: " + e.getMessage());
        }
        
        // 方法2: 使用反射调用 getLibreLogin() 方法
        try {
            plugin.getLogger().info("方法2: 尝试使用反射获取 API 实例...");
            Class<?> bootstrapClass = libreLoginBootstrap.getClass();
            Method getLibreLoginMethod = bootstrapClass.getMethod("getLibreLogin");
            Object apiInstance = getLibreLoginMethod.invoke(libreLoginBootstrap);
            
            if (apiInstance != null && apiInstance instanceof LibreLoginPlugin) {
                @SuppressWarnings("unchecked")
                LibreLoginPlugin<Player, World> typedInstance = (LibreLoginPlugin<Player, World>) apiInstance;
                plugin.getLogger().info("成功通过方法2（反射）获取 LibreLogin API 实例！");
                return typedInstance;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("方法2失败: " + e.getMessage());
        }
        
        // 方法3: 检查是否有 libreLogin 字段
        try {
            plugin.getLogger().info("方法3: 尝试直接访问 libreLogin 字段...");
            Class<?> bootstrapClass = libreLoginBootstrap.getClass();
            Field libreLoginField = bootstrapClass.getDeclaredField("libreLogin");
            libreLoginField.setAccessible(true);
            Object apiInstance = libreLoginField.get(libreLoginBootstrap);
            
            if (apiInstance != null && apiInstance instanceof LibreLoginPlugin) {
                @SuppressWarnings("unchecked")
                LibreLoginPlugin<Player, World> typedInstance = (LibreLoginPlugin<Player, World>) apiInstance;
                plugin.getLogger().info("成功通过方法3（字段访问）获取 LibreLogin API 实例！");
                return typedInstance;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("方法3失败: " + e.getMessage());
        }
        
        plugin.getLogger().severe("所有方法都失败了！无法获取 LibreLogin API 实例。");
        return null;
    }
}

