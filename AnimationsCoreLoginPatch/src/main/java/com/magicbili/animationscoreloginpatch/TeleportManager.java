package com.magicbili.animationscoreloginpatch;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 负责分配点位并传送玩家（全局轮转）
 */
public class TeleportManager {
	
	private final JavaPlugin plugin;
	private final PointsStorage storage;
	private final ReentrantLock lock = new ReentrantLock();
	private final AtomicInteger nextIndex = new AtomicInteger(1);
	
	public TeleportManager(JavaPlugin plugin, PointsStorage storage) {
		this.plugin = plugin;
		this.storage = storage;
	}
	
	/**
	 * 为玩家分配点位并传送
	 * @return 是否成功传送
	 */
	public boolean teleportPlayerToAssignedPoint(Player player) {
		List<TeleportPoint> points = storage.getAll();
		if (points.isEmpty()) {
			return false;
		}
		
		lock.lock();
		try {
			// 轮转尝试不超过列表大小次
			int attempts = points.size();
			int start = normalize(nextIndex.get(), points.size());
			int current = start;
			do {
				TeleportPoint tp = getByIndex(points, current);
				if (tp != null) {
					Location loc = tp.toLocation();
					if (isLocationUsable(loc)) {
						boolean ok = player.teleport(loc);
						// 推进指针
						advance(points.size());
						if (ok) return true;
					}
				}
				current = current + 1;
				if (current > points.size()) current = 1;
				attempts--;
			} while (attempts > 0);
			
			// 推进指针（避免卡住）
			advance(points.size());
			return false;
		} finally {
			lock.unlock();
		}
	}
	
	private void advance(int size) {
		int next = nextIndex.get() + 1;
		if (next > size) next = 1;
		nextIndex.set(next);
	}
	
	private int normalize(int idx, int size) {
		if (idx < 1) return 1;
		if (idx > size) return 1;
		return idx;
	}
	
	private TeleportPoint getByIndex(List<TeleportPoint> points, int index) {
		for (TeleportPoint p : points) {
			if (p.getIndex() == index) return p;
		}
		return null;
	}
	
	private boolean isLocationUsable(Location loc) {
		if (loc == null || loc.getWorld() == null) return false;
		// 简单安全性：确保脚下不是空气下方虚空，头顶非固体
		Block feet = loc.getBlock();
		Block head = loc.clone().add(0, 1, 0).getBlock();
		// 允许空气或非固体方块
		boolean headFree = !head.getType().isSolid();
		// 若 feet 是液体或空气也可，由服务端自行处理站立；避免传送到基岩上方虚空
		// 这里只做基本校验，详细安全传送可后续增强
		return headFree && feet.getLocation().getWorld() != null;
	}
}


