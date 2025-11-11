package com.magicbili.animationscoreloginpatch;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 点位持久化：plugins/AnimationsCoreLoginPatch/points.yml
 */
public class PointsStorage {
	
	private final JavaPlugin plugin;
	private final ReentrantLock lock = new ReentrantLock();
	private File file;
	private FileConfiguration yaml;
	private final List<TeleportPoint> points = new ArrayList<>();
	
	public PointsStorage(JavaPlugin plugin) {
		this.plugin = plugin;
	}
	
	public void initialize() {
		lock.lock();
		try {
			if (!plugin.getDataFolder().exists()) {
				plugin.getDataFolder().mkdirs();
			}
			file = new File(plugin.getDataFolder(), "points.yml");
			if (!file.exists()) {
				try {
					file.createNewFile();
				} catch (IOException e) {
					plugin.getLogger().warning("无法创建 points.yml: " + e.getMessage());
				}
			}
			yaml = YamlConfiguration.loadConfiguration(file);
			loadFromYaml();
		} finally {
			lock.unlock();
		}
	}
	
	public List<TeleportPoint> getAll() {
		lock.lock();
		try {
			return new ArrayList<>(points);
		} finally {
			lock.unlock();
		}
	}
	
	public void clearAll() {
		lock.lock();
		try {
			points.clear();
			saveToYaml();
		} finally {
			lock.unlock();
		}
	}
	
	public void reload() {
		lock.lock();
		try {
			yaml = YamlConfiguration.loadConfiguration(file);
			loadFromYaml();
		} finally {
			lock.unlock();
		}
	}
	
	public void addAtEnd(Location loc) {
		lock.lock();
		try {
			int nextIndex = points.isEmpty() ? 1 : (points.get(points.size() - 1).getIndex() + 1);
			points.add(new TeleportPoint(
					loc.getWorld().getName(),
					loc.getX(), loc.getY(), loc.getZ(),
					loc.getYaw(), loc.getPitch(),
					nextIndex
			));
			saveToYaml();
		} finally {
			lock.unlock();
		}
	}
	
	public void insertAtIndex(Location loc, int index) {
		lock.lock();
		try {
			index = Math.max(1, index);
			points.add(new TeleportPoint(
					loc.getWorld().getName(),
					loc.getX(), loc.getY(), loc.getZ(),
					loc.getYaw(), loc.getPitch(),
					index
			));
			renumber();
			saveToYaml();
		} finally {
			lock.unlock();
		}
	}
	
	public boolean removeByIndex(int index) {
		lock.lock();
		try {
			boolean removed = points.removeIf(p -> p.getIndex() == index);
			if (removed) {
				renumber();
				saveToYaml();
			}
			return removed;
		} finally {
			lock.unlock();
		}
	}
	
	private void renumber() {
		Collections.sort(points, Comparator.comparingInt(TeleportPoint::getIndex));
		int i = 1;
		for (TeleportPoint p : points) {
			p.setIndex(i++);
		}
	}
	
	private void loadFromYaml() {
		points.clear();
		ConfigurationSection root = yaml.getConfigurationSection("points");
		if (root == null) return;
		for (String key : root.getKeys(false)) {
			ConfigurationSection sec = root.getConfigurationSection(key);
			if (sec == null) continue;
			int index = sec.getInt("index", Integer.parseInt(key));
			String world = sec.getString("world", "");
			double x = sec.getDouble("x", 0.0);
			double y = sec.getDouble("y", 0.0);
			double z = sec.getDouble("z", 0.0);
			float yaw = (float) sec.getDouble("yaw", 0.0);
			float pitch = (float) sec.getDouble("pitch", 0.0);
			points.add(new TeleportPoint(world, x, y, z, yaw, pitch, index));
		}
		renumber();
	}
	
	private void saveToYaml() {
		yaml = new YamlConfiguration();
		for (TeleportPoint p : points) {
			String path = "points." + p.getIndex();
			yaml.set(path + ".index", p.getIndex());
			yaml.set(path + ".world", p.getWorldName());
			yaml.set(path + ".x", p.getX());
			yaml.set(path + ".y", p.getY());
			yaml.set(path + ".z", p.getZ());
			yaml.set(path + ".yaw", p.getYaw());
			yaml.set(path + ".pitch", p.getPitch());
		}
		try {
			yaml.save(file);
		} catch (IOException e) {
			plugin.getLogger().warning("保存 points.yml 失败: " + e.getMessage());
		}
	}
}


