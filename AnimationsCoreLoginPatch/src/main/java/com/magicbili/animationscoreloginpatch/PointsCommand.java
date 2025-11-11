package com.magicbili.animationscoreloginpatch;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PointsCommand implements CommandExecutor, TabCompleter {
	
	private static final String PERM = "animationscoreloginpatch.points.admin";
	private final JavaPlugin plugin;
	private final PointsStorage storage;
	private final TeleportManager teleportManager;
	
	public PointsCommand(JavaPlugin plugin, PointsStorage storage, TeleportManager teleportManager) {
		this.plugin = plugin;
		this.storage = storage;
		this.teleportManager = teleportManager;
	}
	
	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (!sender.hasPermission(PERM)) {
			sender.sendMessage(ChatColor.RED + "你没有权限执行该命令。");
			return true;
		}
		if (args.length == 0) {
			help(sender, label);
			return true;
		}
		String sub = args[0].toLowerCase();
		switch (sub) {
			case "add": {
				if (!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "该子命令需由玩家执行。");
					return true;
				}
				Player p = (Player) sender;
				if (args.length >= 2) {
					Integer idx = parseInt(args[1]);
					if (idx == null || idx < 1) {
						sender.sendMessage(ChatColor.RED + "索引必须是正整数。");
						return true;
					}
					storage.insertAtIndex(p.getLocation(), idx);
					sender.sendMessage(ChatColor.GREEN + "已在索引 " + idx + " 处插入点位。");
				} else {
					storage.addAtEnd(p.getLocation());
					sender.sendMessage(ChatColor.GREEN + "已追加点位到末尾。");
				}
				return true;
			}
			case "insert": {
				if (!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "该子命令需由玩家执行。");
					return true;
				}
				if (args.length < 2) {
					sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " insert <index>");
					return true;
				}
				Integer idx = parseInt(args[1]);
				if (idx == null || idx < 1) {
					sender.sendMessage(ChatColor.RED + "索引必须是正整数。");
					return true;
				}
				Player p = (Player) sender;
				storage.insertAtIndex(p.getLocation(), idx);
				sender.sendMessage(ChatColor.GREEN + "已在索引 " + idx + " 处插入点位。");
				return true;
			}
			case "remove": {
				if (args.length < 2) {
					sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " remove <index>");
					return true;
				}
				Integer idx = parseInt(args[1]);
				if (idx == null || idx < 1) {
					sender.sendMessage(ChatColor.RED + "索引必须是正整数。");
					return true;
				}
				boolean ok = storage.removeByIndex(idx);
				if (ok) {
					sender.sendMessage(ChatColor.GREEN + "已删除索引 " + idx + " 的点位。");
				} else {
					sender.sendMessage(ChatColor.RED + "未找到索引 " + idx + " 的点位。");
				}
				return true;
			}
			case "list": {
				List<TeleportPoint> all = storage.getAll();
				if (all.isEmpty()) {
					sender.sendMessage(ChatColor.YELLOW + "当前没有配置任何点位。");
					return true;
				}
				sender.sendMessage(ChatColor.AQUA + "全局登录点位列表（index: world x y z yaw pitch）：");
				for (TeleportPoint tp : all) {
					sender.sendMessage(ChatColor.GRAY + String.format("#%d: %s %.2f %.2f %.2f %.1f %.1f",
							tp.getIndex(), tp.getWorldName(), tp.getX(), tp.getY(), tp.getZ(), tp.getYaw(), tp.getPitch()));
				}
				return true;
			}
			case "tp": {
				if (!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "该子命令需由玩家执行。");
					return true;
				}
				if (args.length < 2) {
					sender.sendMessage(ChatColor.YELLOW + "用法: /" + label + " tp <index>");
					return true;
				}
				Integer idx = parseInt(args[1]);
				if (idx == null || idx < 1) {
					sender.sendMessage(ChatColor.RED + "索引必须是正整数。");
					return true;
				}
				Player p = (Player) sender;
				TeleportPoint tp = storage.getAll().stream().filter(it -> it.getIndex() == idx).findFirst().orElse(null);
				if (tp == null || tp.toLocation() == null) {
					sender.sendMessage(ChatColor.RED + "该点位无效或世界未加载。");
					return true;
				}
				boolean ok = p.teleport(tp.toLocation());
				if (ok) {
					sender.sendMessage(ChatColor.GREEN + "已传送到索引 " + idx + " 的点位。");
				} else {
					sender.sendMessage(ChatColor.RED + "传送失败。");
				}
				return true;
			}
			case "clear": {
				storage.clearAll();
				sender.sendMessage(ChatColor.GREEN + "已清空所有点位。");
				return true;
			}
			case "reload": {
				storage.reload();
				sender.sendMessage(ChatColor.GREEN + "已重新加载 points.yml。");
				return true;
			}
			default: {
				help(sender, label);
				return true;
			}
		}
	}
	
	private void help(CommandSender sender, String label) {
		sender.sendMessage(ChatColor.AQUA + "用法: /" + label + " <add|insert|remove|list|tp|clear|reload> [参数]");
		sender.sendMessage(ChatColor.GRAY + "/" + label + " add [index] - 在当前位置新增点位（可指定索引）");
		sender.sendMessage(ChatColor.GRAY + "/" + label + " insert <index> - 在指定索引插入点位");
		sender.sendMessage(ChatColor.GRAY + "/" + label + " remove <index> - 删除指定索引点位");
		sender.sendMessage(ChatColor.GRAY + "/" + label + " list - 列出全部点位");
		sender.sendMessage(ChatColor.GRAY + "/" + label + " tp <index> - 传送到指定索引点位");
		sender.sendMessage(ChatColor.GRAY + "/" + label + " clear - 清空全部点位");
		sender.sendMessage(ChatColor.GRAY + "/" + label + " reload - 重载点位文件");
	}
	
	@Nullable
	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
		if (!sender.hasPermission(PERM)) return Collections.emptyList();
		if (args.length == 1) {
			return Arrays.asList("add","insert","remove","list","tp","clear","reload").stream()
					.filter(s -> s.startsWith(args[0].toLowerCase()))
					.collect(Collectors.toList());
		}
		if (args.length == 2) {
			switch (args[0].toLowerCase()) {
				case "insert":
				case "remove":
				case "tp":
					List<TeleportPoint> all = storage.getAll();
					List<String> ids = new ArrayList<>();
					for (TeleportPoint p : all) ids.add(String.valueOf(p.getIndex()));
					return ids.stream().filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
			}
		}
		return Collections.emptyList();
	}
	
	private Integer parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			return null;
		}
	}
}


