package com.magicbili.animationscoreloginpatch;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.event.EventProvider;
import xyz.kyngs.librelogin.api.event.EventTypes;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;

/**
 * 补丁插件：在玩家点击 CustomScreenMenu 按钮后触发 AnimationsCore 的加入动画
 * 
 * 工作方式：
 * 1. 监听 LibreLogin 的 AuthenticatedEvent（登录后等待按钮点击）
 * 2. 监听触发命令的执行（按钮点击后执行命令）
 * 3. 使用 AnimationsCore 2.5.0+ API 触发动画
 * 
 * 要求：
 * - AnimationsCore 2.5.0+ 版本
 * - AnimationsCore 配置中 animation-triggers.join: false
 * 
 * @author magicbili
 */
public class AnimationsCoreLoginPatch extends JavaPlugin {
    
    private Plugin animationsCorePlugin;
    private LibreLoginPlugin<Player, World> libreLoginPlugin;
    private ConfigManager configManager;
    private AnimationsCoreReflection animationsCoreReflection;
    private AnimationTrigger animationTrigger;
    private ButtonClickListener buttonClickListener;
    private JoinEventBlocker joinEventBlocker;
	private PointsStorage pointsStorage;
	private TeleportManager teleportManager;
    
    @Override
    public void onEnable() {
        getLogger().info("====================================");
        getLogger().info("AnimationsCoreLoginPatch 正在启动...");
        getLogger().info("====================================");
        
        // 保存默认配置
        saveDefaultConfig();
        reloadConfig();
        
        // 初始化配置管理器
        configManager = new ConfigManager(this);
        
        // 检查 AnimationsCore 是否加载
        animationsCorePlugin = Bukkit.getPluginManager().getPlugin("AnimationsCore");
        if (animationsCorePlugin == null) {
            getLogger().warning("AnimationsCore 未找到！插件将无法工作。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("AnimationsCore 已找到: " + animationsCorePlugin.getName());
        
        // 检查 LibreLogin 是否加载
        Plugin libreLoginBootstrap = Bukkit.getPluginManager().getPlugin("LibreLogin");
        if (libreLoginBootstrap == null) {
            getLogger().severe("LibreLogin 插件未找到！请确保 LibreLogin 已安装。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("LibreLogin 已找到: " + libreLoginBootstrap.getName());
        
        if (!libreLoginBootstrap.isEnabled()) {
            getLogger().warning("LibreLogin 插件存在但未启用，等待启用...");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!libreLoginBootstrap.isEnabled()) {
                    getLogger().severe("LibreLogin 仍未启用，插件将被禁用。");
                    getServer().getPluginManager().disablePlugin(this);
                } else {
                    completeInitialization(libreLoginBootstrap);
                }
            }, 20L);
            return;
        }
        
        // 完成初始化
        completeInitialization(libreLoginBootstrap);
    }
    
    @Override
    public void onDisable() {
        // 清理资源
        if (animationTrigger != null) {
            animationTrigger.cleanup();
        }
        if (buttonClickListener != null) {
            buttonClickListener.cleanup();
        }
        
        getLogger().info("AnimationsCoreLoginPatch 已禁用！");
    }
    
    /**
     * 完成插件初始化
     */
    private void completeInitialization(Plugin libreLoginBootstrap) {
        // 初始化 LibreLogin API
        libreLoginPlugin = LibreLoginAPI.initialize(libreLoginBootstrap, this);
        if (libreLoginPlugin == null) {
            getLogger().severe("无法获取 LibreLogin API 实例。插件将被禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化 AnimationsCore 反射访问
        animationsCoreReflection = new AnimationsCoreReflection(animationsCorePlugin, this);
        if (!animationsCoreReflection.initialize(this)) {
            getLogger().severe("无法初始化反射访问 AnimationsCore！插件将被禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
		
		// 初始化登录点位存储与传送管理
		pointsStorage = new PointsStorage(this);
		pointsStorage.initialize();
		teleportManager = new TeleportManager(this, pointsStorage);
        
        // 初始化 Join 事件拦截器（用于阻止 AnimationsCore 的 join 触发器）
        // 注意：JoinEventBlocker 的构造函数会立即检测监听器状态
        joinEventBlocker = new JoinEventBlocker(this, animationsCorePlugin);
        getServer().getPluginManager().registerEvents(joinEventBlocker, this);
        getLogger().info("Join 事件拦截器已注册");
        
        // 检查并警告配置问题（在 JoinEventBlocker 检测之后）
        checkAnimationsCoreJoinConfig();
        
        // 延迟再次检查，确保 AnimationsCore 完全加载
        getServer().getScheduler().runTaskLater(this, () -> {
            if (joinEventBlocker != null) {
                joinEventBlocker.reloadConfig(); // 重新检查配置和监听器状态
            }
        }, 20L); // 1秒后检查
        
        // 初始化动画触发器
		animationTrigger = new AnimationTrigger(this, configManager, animationsCoreReflection, libreLoginPlugin, teleportManager);
        
        // 初始化按钮点击监听器
        buttonClickListener = new ButtonClickListener(this, configManager, animationTrigger, libreLoginPlugin);
        buttonClickListener.register();
        getLogger().info("按钮点击监听器已注册");
        
        // 注册触发命令（让命令可以正确执行）
        registerTriggerCommands();
        getLogger().info("触发命令已注册");
		
		// 注册点位管理命令
		registerPointsCommand();
        
        // 订阅 LibreLogin 的认证事件
        EventProvider<Player, World> eventProvider = libreLoginPlugin.getEventProvider();
        EventTypes<Player, World> eventTypes = libreLoginPlugin.getEventTypes();
        
        eventProvider.subscribe(eventTypes.authenticated, (AuthenticatedEvent<Player, World> event) -> {
            Player player = event.getPlayer();
            if (player == null || !player.isOnline()) {
                return;
            }
            
            // 强制要求等待按钮点击触发动画
            // 不再支持自动触发，动画必须通过按钮点击或命令执行才能触发
            if (configManager.isDebug()) {
                String currentAnimation = animationTrigger.getCurrentAnimation(player);
                getLogger().info("玩家 " + player.getName() + " 已登录，等待按钮点击触发动画");
                getLogger().info("玩家 " + player.getName() + " 的动画配置: " + (currentAnimation != null && !currentAnimation.trim().isEmpty() ? currentAnimation : "无"));
                Plugin customScreenMenuPlugin = Bukkit.getPluginManager().getPlugin("CustomScreenMenu");
                getLogger().info("CustomScreenMenu 状态: " + (customScreenMenuPlugin != null && customScreenMenuPlugin.isEnabled() ? "已启用" : "未启用"));
                getLogger().info("注意：动画必须通过按钮点击或命令执行（joinan）才能触发，不会自动触发");
            }
            buttonClickListener.addWaitingPlayer(player);
        });
        
        getLogger().info("AnimationsCoreLoginPatch 已启用！");
        getLogger().info("将在玩家点击 CustomScreenMenu 按钮后触发 AnimationsCore 加入动画。");
        getLogger().info("请确保 AnimationsCore 配置中 animation-triggers.join: false");
        
        // 监听服务器命令事件，检测 mythicmobsreload 等命令
        setupReloadListener();
    }
    
    /**
     * 检查 AnimationsCore 的 join 配置
     */
    private void checkAnimationsCoreJoinConfig() {
        try {
            org.bukkit.configuration.file.FileConfiguration config = animationsCorePlugin.getConfig();
            boolean joinEnabled = config.getBoolean("animation-triggers.join", true);
            
            if (joinEnabled) {
                getLogger().warning("========================================");
                getLogger().warning("警告：AnimationsCore 的 join 触发器已启用！");
                getLogger().warning("这可能导致 join 动画被重复触发。");
                getLogger().warning("建议将 AnimationsCore 配置中的 animation-triggers.join 设置为 false");
                getLogger().warning("注意：如果插件启动时 join 是 true，即使后来改为 false，");
                getLogger().warning("监听器仍然会被注册。需要重启服务器才能生效。");
                getLogger().warning("========================================");
            } else {
                getLogger().info("AnimationsCore join 触发器已禁用，配置正确。");
            }
        } catch (Exception e) {
            getLogger().warning("无法检查 AnimationsCore 配置: " + e.getMessage());
        }
    }
    
    /**
     * 设置 reload 监听器
     * 监听 mythicmobsreload 等命令，在 reload 后重新检查配置
     */
    private void setupReloadListener() {
        JavaPlugin plugin = this;
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onServerCommand(org.bukkit.event.server.ServerCommandEvent event) {
                String command = event.getCommand().toLowerCase();
                if (command.contains("mythicmobsreload") || command.contains("mm reload") || 
                    command.contains("animcore reload") || command.contains("animationscore reload")) {
                    // 延迟检查，等待 reload 完成
                    getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (joinEventBlocker != null) {
                            joinEventBlocker.reloadConfig();
                            checkAnimationsCoreJoinConfig();
                            getLogger().info("检测到 reload 命令，已重新检查 AnimationsCore 配置");
                        }
                    }, 20L); // 1秒后检查
                }
            }
        }, this);
    }
    
    /**
     * 注册触发命令
     * 固定注册 joinan 命令，仅允许控制台执行
     */
    private void registerTriggerCommands() {
        TriggerCommandHandler commandHandler = new TriggerCommandHandler(this, configManager, 
                buttonClickListener, animationTrigger, libreLoginPlugin);
        
        // 固定注册 joinan 命令
        String cmdName = "joinan";
        
        try {
            PluginCommand pluginCommand = getCommand(cmdName);
            if (pluginCommand == null) {
                // 如果命令未在 plugin.yml 中定义，使用 CommandMap 动态注册
                org.bukkit.command.CommandMap commandMap = getServer().getCommandMap();
                Command command = new org.bukkit.command.Command(cmdName) {
                    @Override
                    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
                        return commandHandler.onCommand(sender, this, commandLabel, args);
                    }
                };
                commandMap.register(cmdName, this.getName(), command);
                getLogger().info("已动态注册命令: " + cmdName);
            } else {
                // 如果命令已在 plugin.yml 中定义，设置执行器
                pluginCommand.setExecutor(commandHandler);
                pluginCommand.setTabCompleter(commandHandler);
                getLogger().info("已设置命令执行器: " + cmdName + "（仅控制台可用）");
            }
        } catch (Exception e) {
            getLogger().warning("注册命令失败: " + cmdName + " - " + e.getMessage());
            if (configManager.isDebug()) {
                e.printStackTrace();
            }
        }
    }
	
	/**
	 * 注册登录点位管理命令
	 */
	private void registerPointsCommand() {
		try {
			PointsCommand cmd = new PointsCommand(this, pointsStorage, teleportManager);
			org.bukkit.command.PluginCommand pc = getCommand("loginpoints");
			if (pc != null) {
				pc.setExecutor(cmd);
				pc.setTabCompleter(cmd);
				getLogger().info("已注册命令: loginpoints");
			} else {
				// 兜底：动态注册
				org.bukkit.command.CommandMap commandMap = getServer().getCommandMap();
				org.bukkit.command.Command command = new org.bukkit.command.Command("loginpoints") {
					@Override
					public boolean execute(@NotNull org.bukkit.command.CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
						return cmd.onCommand(sender, this, commandLabel, args);
					}
				};
				commandMap.register("loginpoints", this.getName(), command);
				getLogger().info("已动态注册命令: loginpoints");
			}
		} catch (Exception e) {
			getLogger().warning("注册 loginpoints 命令失败: " + e.getMessage());
			if (configManager.isDebug()) {
				e.printStackTrace();
			}
		}
	}
}
