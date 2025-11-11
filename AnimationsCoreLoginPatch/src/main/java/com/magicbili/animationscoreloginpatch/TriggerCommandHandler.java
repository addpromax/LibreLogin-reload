package com.magicbili.animationscoreloginpatch;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 触发命令处理器
 * 用于注册触发命令（如 joinan），让命令可以正确执行
 * 当命令被执行时，从命令参数中解析玩家名称并触发动画
 */
public class TriggerCommandHandler implements CommandExecutor, TabCompleter {
    
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ButtonClickListener buttonClickListener;
    private final AnimationTrigger animationTrigger;
    private final LibreLoginPlugin<org.bukkit.entity.Player, org.bukkit.World> libreLoginPlugin;
    
    public TriggerCommandHandler(JavaPlugin plugin, ConfigManager configManager, 
                                ButtonClickListener buttonClickListener,
                                AnimationTrigger animationTrigger,
                                LibreLoginPlugin<org.bukkit.entity.Player, org.bukkit.World> libreLoginPlugin) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.buttonClickListener = buttonClickListener;
        this.animationTrigger = animationTrigger;
        this.libreLoginPlugin = libreLoginPlugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // 只允许控制台执行此命令
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[命令处理器] 命令 '" + label + "' 只能由控制台执行，发送者: " + sender.getName());
            }
            return true;
        }
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("[命令处理器] 命令 '" + label + "' 被执行，参数: " + String.join(" ", args));
        }
        
        // 从命令参数中解析玩家名称
        String playerName = null;
        if (args.length > 0) {
            // 第一个参数可能是玩家名称
            playerName = args[0].trim();
        }
        
        // 如果命令是通过控制台执行的，尝试从命令字符串中解析玩家名称
        if (playerName == null || playerName.isEmpty()) {
            // 尝试从命令字符串中解析（如果命令包含玩家名称）
            String fullCommand = label + " " + String.join(" ", args);
            playerName = parsePlayerNameFromCommand(fullCommand);
        }
        
        if (playerName == null || playerName.isEmpty()) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[命令处理器] 无法从命令中解析玩家名称: " + label + " " + String.join(" ", args));
            }
            return true;
        }
        
        if (configManager.isDebug()) {
            plugin.getLogger().info("[命令处理器] 解析到的玩家名称: " + playerName);
        }
        
        // 查找玩家
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[命令处理器] 玩家不在线: " + playerName);
            }
            return true;
        }
        
        UUID uuid = player.getUniqueId();
        
        // 检查玩家是否在等待列表中（已登录且等待按钮点击）
        if (!buttonClickListener.isPlayerWaiting(uuid)) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[命令处理器] 玩家不在等待列表中: " + playerName);
            }
            return true;
        }
        
        // 触发动画
        if (configManager.isDebug()) {
            plugin.getLogger().info("[命令处理器] 触发动画，玩家: " + playerName);
        }
        
        // 使用 ButtonClickListener 的方法来触发动画（确保逻辑一致）
        buttonClickListener.triggerAnimationForPlayer(player);
        
        return true;
    }
    
    /**
     * 从命令中解析玩家名称
     */
    private String parsePlayerNameFromCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return null;
        }
        
        // 方法1：从命令参数中解析玩家名称
        String[] parts = command.trim().split("\\s+");
        if (parts.length >= 2) {
            // 优先检查最后一个参数（通常是玩家名称）
            for (int i = parts.length - 1; i >= 1; i--) {
                String possiblePlayerName = parts[i].trim();
                
                // 移除可能的占位符标记
                possiblePlayerName = possiblePlayerName.replace("%player%", "")
                    .replace("{player}", "")
                    .replace("%player_name%", "")
                    .trim();
                
                if (!possiblePlayerName.isEmpty()) {
                    // 检查是否是有效的玩家名称
                    if (possiblePlayerName.matches("^[a-zA-Z0-9_]{1,16}$")) {
                        // 验证玩家是否在线
                        Player player = Bukkit.getPlayer(possiblePlayerName);
                        if (player != null && player.isOnline()) {
                            return possiblePlayerName;
                        }
                    }
                }
            }
        }
        
        // 方法2：从命令中查找所有在线玩家的名称
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            String playerName = onlinePlayer.getName();
            // 检查命令中是否包含玩家名称（作为完整单词）
            String pattern = "\\b" + java.util.regex.Pattern.quote(playerName) + "\\b";
            if (java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(command).find()) {
                return playerName;
            }
        }
        
        return null;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // 返回空列表，不提供 Tab 补全
        return new ArrayList<>();
    }
}

