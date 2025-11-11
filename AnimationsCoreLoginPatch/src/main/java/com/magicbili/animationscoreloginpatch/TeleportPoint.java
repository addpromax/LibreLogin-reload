package com.magicbili.animationscoreloginpatch;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * 全局跨世界登录点位
 */
public class TeleportPoint {
	
	private final String world;
	private final double x;
	private final double y;
	private final double z;
	private final float yaw;
	private final float pitch;
	private int index;
	
	public TeleportPoint(String world, double x, double y, double z, float yaw, float pitch, int index) {
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		this.index = index;
	}
	
	public String getWorldName() {
		return world;
	}
	
	public double getX() {
		return x;
	}
	
	public double getY() {
		return y;
	}
	
	public double getZ() {
		return z;
	}
	
	public float getYaw() {
		return yaw;
	}
	
	public float getPitch() {
		return pitch;
	}
	
	public int getIndex() {
		return index;
	}
	
	public void setIndex(int index) {
		this.index = index;
	}
	
	public World getWorld() {
		return world == null || world.isEmpty() ? null : Bukkit.getWorld(world);
	}
	
	public Location toLocation() {
		World w = getWorld();
		return w == null ? null : new Location(w, x, y, z, yaw, pitch);
	}
}


