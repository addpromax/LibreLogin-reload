package com.magicbili.animationscoreloginpatch;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按钮点击事件监听器
 * 通过监听后台命令执行来检测 CustomScreenMenu 按钮点击
 * 
 * 实现方式：
 * 1. 监听后台命令执行（当 CustomScreenMenu 按钮被点击时执行后台命令）
 * 2. 从命令中解析玩家名称（支持 %player% 占位符或命令参数）
 * 3. 找到对应的玩家并触发动画
 * 
 * 注意：cmenu支持后台执行指令并支持papi，所以命令中可能包含papi占位符
 */
public class ButtonClickListener implements Listener {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final AnimationTrigger animationTrigger;
    private final LibreLoginPlugin<Player, World> libreLoginPlugin;
    private final Map<UUID, PendingAnimationInfo> pendingAnimationsWaitingButtonClick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCommandTime = new ConcurrentHashMap<>(); // 防止重复触发
    
    private boolean registered = false;
    
    public ButtonClickListener(JavaPlugin plugin, ConfigManager configManager,
                               AnimationTrigger animationTrigger,
                               LibreLoginPlugin<Player, World> libreLoginPlugin) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.animationTrigger = animationTrigger;
        this.libreLoginPlugin = libreLoginPlugin;
    }
    
    /**
     * 注册按钮点击事件监听器
     * 使用命令监听方式，直接监听 CustomScreenMenu 执行的命令
     * 注意：现在强制要求等待按钮点击，不再支持自动触发
     */
    public void register() {
        // 注册命令监听器
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registered = true;
        
        // 验证监听器是否注册成功
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("按钮点击监听器注册信息：");
        plugin.getLogger().info("  监听器类: " + this.getClass().getName());
        plugin.getLogger().info("  触发命令: joinan");
        plugin.getLogger().info("  事件优先级: LOWEST");
        plugin.getLogger().info("  忽略取消: false");
        plugin.getLogger().info("  注册状态: " + registered);
        plugin.getLogger().info("========================================");
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("按钮点击监听器已注册（后台命令监听方式）");
            plugin.getLogger().info("触发命令: joinan（仅控制台可用）");
            plugin.getLogger().info("将监听后台命令执行，并解析玩家名称");
        }
    }
    
    /**
     * 监听后台命令执行事件
     * 当 CustomScreenMenu 按钮被点击时执行后台命令，我们监听这些命令来触发动画
     * 从命令中解析玩家名称（支持 %player% 占位符或命令参数）
     * 
     * 使用 LOWEST 优先级，确保在所有其他插件处理之前就能捕获到命令
     */
    @org.bukkit.event.EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand();
        CommandSender sender = event.getSender();
        
        // 固定检查 joinan 命令
        String commandLower = command.toLowerCase().trim();
        String commandToCheck = commandLower;
        if (commandToCheck.startsWith("/")) {
            commandToCheck = commandToCheck.substring(1);
        }
        
        // 检查命令是否匹配 joinan（支持前缀匹配）
        if (!commandToCheck.equals("joinan") && !commandToCheck.startsWith("joinan ")) {
            // 不是 joinan 命令，不处理
            return;
        }
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("[后台命令监听] ✓ 命令匹配: '" + command + "' 匹配触发命令: 'joinan'");
        }
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("[后台命令监听] ========================================");
            plugin.getLogger().info("[后台命令监听] 后台命令执行事件被触发");
            plugin.getLogger().info("[后台命令监听] 命令: '" + command + "'");
            plugin.getLogger().info("[后台命令监听] 发送者: " + sender.getName());
        }
        
        // 从命令中解析玩家名称
        String playerName = parsePlayerNameFromCommand(command);
        
        if (playerName == null || playerName.isEmpty()) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[后台命令监听] 无法从命令中解析玩家名称: " + command);
            }
            return;
        }
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("[后台命令监听] 解析到的玩家名称: " + playerName);
        }
        
        // 查找玩家
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[后台命令监听] 玩家不在线: " + playerName);
            }
            return;
        }
        
        UUID uuid = player.getUniqueId();
        
        // 检查玩家是否在等待列表中（已登录且等待按钮点击）
        PendingAnimationInfo pendingInfo = pendingAnimationsWaitingButtonClick.get(uuid);
        
        if (pendingInfo == null) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[后台命令监听] 玩家不在等待列表中: " + playerName);
            }
            return;
        }
        
        // 防止短时间内重复触发（1秒内只触发一次）
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastCommandTime.get(uuid);
        if (lastTime != null && (currentTime - lastTime) < 1000) {
            if (configManager.isDebug()) {
                plugin.getLogger().info("[后台命令监听] 玩家 " + playerName + " 命令触发过于频繁，忽略");
            }
            return;
        }
        lastCommandTime.put(uuid, currentTime);
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("[后台命令监听] 玩家 " + playerName + " 执行了触发命令: " + command);
            plugin.getLogger().info("[后台命令监听] 触发动画");
        }
        
        // 延迟触发动画，确保命令先执行
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingAnimationInfo info = pendingAnimationsWaitingButtonClick.remove(uuid);
            if (info != null && !info.isTriggered()) {
                lastCommandTime.remove(uuid);
                info.setTriggered(true);
                
                if (player.isOnline() && libreLoginPlugin.getAuthorizationProvider().isAuthorized(player)) {
                    if (configManager.isDebug()) {
                        plugin.getLogger().info("[后台命令监听] 准备触发动画，玩家: " + player.getName());
                    }
                    animationTrigger.triggerJoinAnimationWithDelay(player);
                } else {
                    if (configManager.isDebug()) {
                        plugin.getLogger().warning("[后台命令监听] 玩家不在线或未授权，无法触发动画: " + player.getName());
                    }
                }
            } else {
                if (configManager.isDebug()) {
                    plugin.getLogger().warning("[后台命令监听] 玩家不在等待列表中或已触发: " + player.getName());
                }
            }
        }, 2L); // 延迟 2 tick，确保命令先执行
    }
    
    /**
     * 从命令中解析玩家名称
     * 
     * cmenu 执行命令时的处理流程：
     * 1. 先替换 %player% 为玩家名称
     * 2. 使用 PlaceholderAPI 解析所有 papi 占位符（如 %player_name% 也会被解析为玩家名称）
     * 3. 如果命令以 [console] 开头，会移除这个前缀，然后使用 Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd) 执行后台命令
     * 
     * 所以，当后台命令执行时，命令中应该已经包含了实际的玩家名称（papi已解析）
     * 
     * 支持以下格式：
     * 1. 命令参数中包含玩家名称（如 "joinan PlayerName"）
     * 2. 命令中包含已解析的玩家名称（papi已解析的占位符）
     * 
     * @param command 命令字符串（已移除 [console] 前缀，papi已解析）
     * @return 玩家名称，如果无法解析则返回null
     */
    private String parsePlayerNameFromCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }
        
        // 方法1：从命令参数中解析玩家名称
        // 格式：trigger-command <player> 或 trigger-command <other-args> <player>
        String[] parts = command.trim().split("\\s+");
        if (parts.length >= 2) {
            // 优先检查最后一个参数（通常是玩家名称）
            for (int i = parts.length - 1; i >= 1; i--) {
                String possiblePlayerName = parts[i].trim();
                
                // 移除可能的占位符标记（虽然papi已解析，但为了安全仍检查）
                possiblePlayerName = possiblePlayerName.replace("%player%", "")
                    .replace("{player}", "")
                    .replace("%player_name%", "")
                    .trim();
                
                if (!possiblePlayerName.isEmpty()) {
                    // 检查是否是有效的玩家名称（Minecraft玩家名称格式：1-16个字符，只能包含字母、数字、下划线）
                    if (possiblePlayerName.matches("^[a-zA-Z0-9_]{1,16}$")) {
                        // 验证玩家是否在线（确保是有效的玩家名称）
                        Player player = Bukkit.getPlayer(possiblePlayerName);
                        if (player != null && player.isOnline()) {
                            return possiblePlayerName;
                        }
                    }
                }
            }
        }
        
        // 方法2：从命令中查找所有在线玩家的名称
        // 如果命令中包含玩家名称（papi已解析），尝试匹配在线玩家
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            String playerName = onlinePlayer.getName();
            // 检查命令中是否包含玩家名称（作为完整单词，避免误匹配）
            // 使用单词边界匹配，避免部分匹配（如 "test" 匹配 "test123"）
            String pattern = "\\b" + java.util.regex.Pattern.quote(playerName) + "\\b";
            if (java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(command).find()) {
                return playerName;
            }
        }
        
        return null;
    }
    
    
    /**
     * 添加等待按钮点击的玩家
     * 注意：不再有超时机制，玩家必须通过按钮点击或命令执行才能触发动画
     */
    public void addWaitingPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        PendingAnimationInfo pendingInfo = new PendingAnimationInfo(player, System.currentTimeMillis());
        pendingAnimationsWaitingButtonClick.put(uuid, pendingInfo);
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("[按钮监听] 玩家 " + player.getName() + " 已添加到等待列表，等待按钮点击");
            plugin.getLogger().info("[按钮监听] 等待列表大小: " + pendingAnimationsWaitingButtonClick.size());
            plugin.getLogger().info("[按钮监听] 注意：动画必须通过按钮点击或命令执行才能触发，不会自动触发");
        }
    }
    
    /**
     * 检查玩家是否在等待列表中
     */
    public boolean isPlayerWaiting(UUID uuid) {
        return pendingAnimationsWaitingButtonClick.containsKey(uuid);
    }
    
    /**
     * 为玩家触发动画（从命令处理器调用）
     */
    public void triggerAnimationForPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        // 检查玩家是否在等待列表中
        PendingAnimationInfo pendingInfo = pendingAnimationsWaitingButtonClick.get(uuid);
        if (pendingInfo == null) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[命令处理器] 玩家不在等待列表中: " + player.getName());
            }
            return;
        }
        
        // 防止短时间内重复触发（1秒内只触发一次）
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastCommandTime.get(uuid);
        if (lastTime != null && (currentTime - lastTime) < 1000) {
            if (configManager.isDebug()) {
                plugin.getLogger().info("[命令处理器] 玩家 " + player.getName() + " 命令触发过于频繁，忽略");
            }
            return;
        }
        lastCommandTime.put(uuid, currentTime);
        
        // 延迟触发动画，确保命令先执行
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingAnimationInfo info = pendingAnimationsWaitingButtonClick.remove(uuid);
            if (info != null && !info.isTriggered()) {
                lastCommandTime.remove(uuid);
                info.setTriggered(true);
                
                if (player.isOnline() && libreLoginPlugin.getAuthorizationProvider().isAuthorized(player)) {
                    // 直接触发动画（如果 join 触发器已关闭，配置不会被移除，可以直接使用）
                    if (configManager.isDebug()) {
                        String currentAnimation = animationTrigger.getCurrentAnimation(player);
                        plugin.getLogger().info("[命令处理器] 准备触发动画，玩家: " + player.getName() + 
                            (currentAnimation != null && !currentAnimation.trim().isEmpty() ? 
                                "，动画: " + currentAnimation : "，无动画配置"));
                    }
                    animationTrigger.triggerJoinAnimationWithDelay(player);
                } else {
                    if (configManager.isDebug()) {
                        plugin.getLogger().warning("[命令处理器] 玩家不在线或未授权，无法触发动画: " + player.getName());
                    }
                }
            } else {
                if (configManager.isDebug()) {
                    plugin.getLogger().warning("[命令处理器] 玩家不在等待列表中或已触发: " + player.getName());
                }
            }
        }, 2L); // 延迟 2 tick，确保命令先执行
    }
    
    /**
     * 清理
     */
    public void cleanup() {
        // 清理所有数据
        pendingAnimationsWaitingButtonClick.clear();
        lastCommandTime.clear();
    }
    
    /**
     * 待处理的动画信息
     */
    public static class PendingAnimationInfo {
        private final Player player;
        private final long createTime;
        private boolean triggered;
        
        public PendingAnimationInfo(Player player, long createTime) {
            this.player = player;
            this.createTime = createTime;
            this.triggered = false;
        }
        
        public Player getPlayer() {
            return player;
        }
        
        public long getCreateTime() {
            return createTime;
        }
        
        public boolean isTriggered() {
            return triggered;
        }
        
        public void setTriggered(boolean triggered) {
            this.triggered = triggered;
        }
    }
}

