/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.image;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 虚拟地图投影器，使用PacketEvents直接发送地图数据包，不创建真实的Bukkit地图。
 * 这种方式避免了Bukkit地图系统的同步问题，提供更稳定的2FA地图显示。
 * 
 * @author LibreLogin Contributors
 */
public class VirtualMapProjector extends AuthenticImageProjector<Player, org.bukkit.World> {

    // 虚拟地图ID范围：使用高位正数避免与服务器地图冲突
    // 🔧 修复：使用更小的地图ID范围，提高兼容性
    private static final int MIN_VIRTUAL_MAP_ID = 100;
    private static final int MAX_VIRTUAL_MAP_ID = 999;
    
    // 地图ID分配器，从最小值开始递增  
    private final AtomicInteger mapIdAllocator = new AtomicInteger(MIN_VIRTUAL_MAP_ID);
    
    // 玩家虚拟地图ID映射
    private final Map<UUID, Integer> playerMapIds = new ConcurrentHashMap<>();

    public VirtualMapProjector(AuthenticLibreLogin<Player, org.bukkit.World> plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        var api = PacketEvents.getAPI();
        if (api != null) {
            plugin.getLogger().info("VirtualMapProjector enabled - using pure PacketEvents map projection");
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Virtual map ID range: " + MIN_VIRTUAL_MAP_ID + " to " + MAX_VIRTUAL_MAP_ID + " (using positive IDs)");
            }
        } else {
            plugin.getLogger().warn("VirtualMapProjector enabled but PacketEvents API is null!");
        }
    }

    @Override
    public void project(BufferedImage image, Player player) {
        boolean debug = plugin.getConfiguration().get(ConfigurationKeys.DEBUG);
        
        if (debug) {
            plugin.getLogger().debug("Starting virtual QR code projection for player: " + player.getName());
            plugin.getLogger().debug("Original image size: " + image.getWidth() + "x" + image.getHeight());
        }

        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            plugin.getLogger().warn("Cannot project virtual map - PacketEvents user is null for player: " + player.getName());
            return;
        }

        // 分配虚拟地图ID
        int mapId = allocateVirtualMapId(player.getUniqueId());
        if (mapId == -1) {
            plugin.getLogger().error("Failed to allocate virtual map ID for player: " + player.getName());
            return;
        }

        if (debug) {
            plugin.getLogger().debug("Allocated virtual map ID: " + mapId + " for player: " + player.getName());
        }

        // 调整图像尺寸到128x128
        BufferedImage resizedImage = resizeImage(image, debug);
        
        if (debug) {
            plugin.getLogger().debug("Image processing completed for map ID: " + mapId);
            // 检查调整后图像的一些像素
            plugin.getLogger().debug(String.format("Resized image pixel [0,0]: RGB=%08x", resizedImage.getRGB(0, 0)));
            plugin.getLogger().debug(String.format("Resized image pixel [64,64]: RGB=%08x", resizedImage.getRGB(64, 64)));
        }

        // 步骤1：隐藏玩家背包（包括所有槽位）
        hidePlayerInventory(player, debug);

        // 步骤2：先发送地图物品到背包
        if (debug) {
            plugin.getLogger().debug("=== VIRTUAL MAP PROJECTION SEQUENCE ===");
            plugin.getLogger().debug("Step 1: Inventory hidden ✅");
            plugin.getLogger().debug("Step 2: Sending virtual map item to slot 36...");
        }
        sendVirtualMapItem(user, mapId, debug);
        
        // 🔧 修复：改进发送时序，确保地图正确激活
        if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin) {
            // 步骤3.1：短延迟后发送初始化包（激活地图状态）
            paperPlugin.delay(() -> {
                sendMapInitialization(user, mapId, debug);
                
                // 步骤3.2：再次延迟发送完整地图数据
                paperPlugin.delay(() -> {
                    sendMapData(user, mapId, resizedImage, debug);
                    
                    // 步骤3.3：最后确认地图状态
                    if (debug) {
                        paperPlugin.delay(() -> {
                            plugin.getLogger().debug("🗺️ Map projection sequence completed for ID: " + mapId);
                            plugin.getLogger().debug("Client should now see: Named map with QR code content");
                        }, 100);
                    }
                }, 200); // 增加延迟确保初始化包先处理
                
            }, 150); // 稍微增加初始延迟
        } else {
            // 非Paper环境同步发送
            sendMapInitialization(user, mapId, debug);
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            sendMapData(user, mapId, resizedImage, debug);
        }

        if (debug) {
            plugin.getLogger().debug("🔧 ENHANCED PROJECTION SEQUENCE:");
            plugin.getLogger().debug("  - Map ID range: " + MIN_VIRTUAL_MAP_ID + "-" + MAX_VIRTUAL_MAP_ID + " (smaller for compatibility)");
            plugin.getLogger().debug("  - Init packet: +150ms (activation)");
            plugin.getLogger().debug("  - Data packet: +350ms (QR content)");
            plugin.getLogger().debug("  - Final check: +450ms");
            plugin.getLogger().debug("Expected result: Complete NBT map → QR display");
            plugin.getLogger().debug("=== ENHANCED SEQUENCE INITIATED ===");
        }
    }

    @Override
    public boolean canProject(Player player) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        return user != null;
    }

    /**
     * 清理玩家的虚拟地图数据
     */
    public void cleanupVirtualMap(Player player) {
        UUID uuid = player.getUniqueId();
        Integer mapId = playerMapIds.remove(uuid);
        
        // 清理虚拟地图标记
        if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin) {
            var inventoryManager = paperPlugin.getInventoryManager();
            if (inventoryManager != null && inventoryManager instanceof xyz.kyngs.librelogin.paper.inventory.InventoryManager manager) {
                manager.clearVirtualMapActive(player);
            }
        }
        
        boolean debug = plugin.getConfiguration().get(ConfigurationKeys.DEBUG);
        if (mapId != null && debug) {
            plugin.getLogger().debug("Released virtual map ID: " + mapId + " and cleared virtual map status for player: " + player.getName());
        }
    }

    /**
     * 获取玩家当前的虚拟地图ID
     */
    public Integer getPlayerMapId(Player player) {
        return playerMapIds.get(player.getUniqueId());
    }

    /**
     * 调整图像尺寸
     */
    private BufferedImage resizeImage(BufferedImage originalImage, boolean debug) {
        if (originalImage.getWidth() == 128 && originalImage.getHeight() == 128) {
            return originalImage;
        }

        BufferedImage resized = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        var graphics = resized.createGraphics();
        graphics.drawImage(originalImage, 0, 0, 128, 128, 0, 0, 
                          originalImage.getWidth(), originalImage.getHeight(), null);
        graphics.dispose();
        
        if (debug) {
            plugin.getLogger().debug("Resized image from " + originalImage.getWidth() + "x" + originalImage.getHeight() + " to 128x128");
        }
        
        return resized;
    }

    /**
     * 隐藏玩家背包并标记虚拟地图状态
     */
    private void hidePlayerInventory(Player player, boolean debug) {
        if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin) {
            var inventoryManager = paperPlugin.getInventoryManager();
            if (inventoryManager != null) {
                inventoryManager.hideInventory(player);
                // 通过InventoryManager标记虚拟地图状态
                if (inventoryManager instanceof xyz.kyngs.librelogin.paper.inventory.InventoryManager manager) {
                    manager.markVirtualMapActive(player);
                }
                if (debug) {
                    plugin.getLogger().debug("Hidden complete inventory and marked virtual map active for player: " + player.getName());
                }
            }
        }
    }

    /**
     * 发送虚拟地图物品到主手
     */
    private void sendVirtualMapItem(User user, int mapId, boolean debug) {
        try {
            // 🔧 使用直接组件设置方式而不是构建器（更可靠）
            
            if (debug) {
                plugin.getLogger().debug("🔧 DIRECT COMPONENT SET - map ID: " + mapId);
                plugin.getLogger().debug("Method: Direct setComponent() calls on ItemStack");
                plugin.getLogger().debug("Components set:");
                plugin.getLogger().debug("  - MAP_ID: " + mapId + " (direct component set)");
                plugin.getLogger().debug("  - CUSTOM_NAME: §a2FA验证码 (direct component set)");
                plugin.getLogger().debug("Approach: ItemStack.setComponent() for 1.20.5+ compatibility");
            }
            
            // 🔧 关键修复：直接构建ItemStack并设置组件 (1.20.5+)
            ItemStack mapItem = ItemStack.builder()
                    .type(ItemTypes.FILLED_MAP) // 填充地图类型
                    .amount(1)
                    .build();
                    
            // 直接在ItemStack上设置数据组件
            mapItem.setComponent(ComponentTypes.MAP_ID, mapId);
            mapItem.setComponent(ComponentTypes.CUSTOM_NAME, net.kyori.adventure.text.Component.text("§a2FA验证码"));

        // 发送SET_SLOT包，设置主手槽位（36）
        // 修复：窗口ID 0 = 玩家背包，slot 36 = 主手
        WrapperPlayServerSetSlot setSlotPacket = new WrapperPlayServerSetSlot(
                0,      // windowId: 玩家背包
                0,      // stateId: 状态ID  
                36,     // slot: 主手槽位（PacketEvents会正确处理为short）
                mapItem // 地图物品
        );

            user.writePacket(setSlotPacket);
            
            if (debug) {
                plugin.getLogger().debug("✅ Sent DIRECT COMPONENT map item to main hand");
                plugin.getLogger().debug("Item verification:");
                plugin.getLogger().debug("  - Type: " + mapItem.getType().getName() + " (FILLED_MAP)");
                plugin.getLogger().debug("  - Amount: " + mapItem.getAmount());
                plugin.getLogger().debug("  - MAP_ID Component: " + mapItem.getComponent(ComponentTypes.MAP_ID));
                plugin.getLogger().debug("  - CUSTOM_NAME Component: " + mapItem.getComponent(ComponentTypes.CUSTOM_NAME));
                plugin.getLogger().debug("  - Has NBT: " + (mapItem.getNBT() != null) + " (should be false)");
                plugin.getLogger().debug("Expected: Client should see proper filled map with ID " + mapId);
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to send virtual map item: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 发送地图初始化包 - 激活地图，让Empty Map准备接收数据
     */
    private void sendMapInitialization(User user, int mapId, boolean debug) {
        try {
            if (debug) {
                plugin.getLogger().debug("=== MAP INITIALIZATION DEBUG ===");
                plugin.getLogger().debug("Sending map initialization packet for map ID: " + mapId);
                plugin.getLogger().debug("Init packet params:");
                plugin.getLogger().debug("  - mapId: " + mapId);
                plugin.getLogger().debug("  - scale: 0");
                plugin.getLogger().debug("  - trackingPosition: false");
                plugin.getLogger().debug("  - locked: true");
                plugin.getLogger().debug("  - decorations: null");
                plugin.getLogger().debug("  - columns: 1 (FIXED: was 0, now 1 pixel)");
                plugin.getLogger().debug("  - rows: 1");
                plugin.getLogger().debug("  - CRITICAL FIX: columns > 0 required for data transmission!");
            }
            
            // 🔧 关键修复：发送实际像素数据来激活地图
            // PacketEvents源码显示：columns=0时不会发送任何地图数据！
            byte[] initData = new byte[1];
            initData[0] = (byte) 8; // 白色像素用于初始化
            
            WrapperPlayServerMapData initPacket = new WrapperPlayServerMapData(
                    mapId,           // 地图ID
                    (byte) 0,        // 缩放级别 0 = 1:1
                    false,           // trackingPosition: 不跟踪位置
                    true,            // locked: 锁定地图
                    null,            // decorations: 无装饰
                    1,               // columns: 1像素宽（FIXED: 不能是0！）
                    1,               // rows: 1像素高
                    0,               // x: 起始坐标
                    0,               // z: 起始坐标  
                    initData         // data: 1个白色像素（激活地图）
            );

            user.writePacket(initPacket);
            
            if (debug) {
                plugin.getLogger().debug("Sent map initialization packet for map ID: " + mapId);
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to send map initialization packet: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 发送地图数据 - 关键：这个包让空白地图变成填充地图
     */
    private void sendMapData(User user, int mapId, BufferedImage image, boolean debug) {
        try {
            // 将图像转换为地图数据
            byte[] mapData = convertImageToMapData(image);
            
            // 验证地图数据
            if (mapData == null || mapData.length != 128 * 128) {
                plugin.getLogger().error("Invalid map data: " + (mapData == null ? "null" : mapData.length + " bytes"));
                return;
            }
            
            if (debug) {
                plugin.getLogger().debug("=== MAP DATA PACKET DEBUG ===");
                plugin.getLogger().debug("Creating MAP_DATA packet for map ID: " + mapId);
                plugin.getLogger().debug("Map data length: " + mapData.length + " bytes");
                plugin.getLogger().debug("Data packet params:");
                plugin.getLogger().debug("  - mapId: " + mapId + " (must match item NBT)");
                plugin.getLogger().debug("  - scale: 0 (1:1 zoom)");
                plugin.getLogger().debug("  - trackingPosition: false");
                plugin.getLogger().debug("  - locked: true");
                plugin.getLogger().debug("  - decorations: null");
                plugin.getLogger().debug("  - columns: 128 (full map width)");
                plugin.getLogger().debug("  - rows: 128 (full map height)");
                plugin.getLogger().debug("  - x: 0 (update start X)");
                plugin.getLogger().debug("  - z: 0 (update start Z)");
                plugin.getLogger().debug("  - data: " + mapData.length + " bytes of pixel data");
            }
            
            // 重要：必须使用正确的构造函数参数顺序
            // 参考PacketEvents源码的write()方法顺序
            WrapperPlayServerMapData mapDataPacket = new WrapperPlayServerMapData(
                    mapId,           // 地图ID
                    (byte) 0,        // 缩放级别(0=1:1, 1=1:2, 2=1:4, 3=1:8, 4=1:16)
                    false,           // trackingPosition - 是否跟踪玩家位置
                    true,            // locked - 地图是否锁定
                    null,            // decorations - 地图装饰（图标）
                    128,             // columns - 更新区域宽度
                    128,             // rows - 更新区域高度  
                    0,               // x - 更新区域起始X坐标
                    0,               // z - 更新区域起始Z坐标
                    mapData          // data - 地图像素数据(128*128字节)
            );

            // 发送数据包
            user.writePacket(mapDataPacket);
            
            if (debug) {
                plugin.getLogger().debug("✅ Successfully sent MAP_DATA packet for map ID: " + mapId);
                
                // 输出数据预览和统计
                StringBuilder dataPreview = new StringBuilder();
                int blackCount = 0, whiteCount = 0;
                
                for (int i = 0; i < Math.min(64, mapData.length); i++) {
                    byte b = mapData[i];
                    dataPreview.append(String.format("%02x ", b & 0xFF));
                    if (b == (byte)119) blackCount++;      // 黑色
                    else if (b == (byte)8) whiteCount++;   // 白色
                    if ((i + 1) % 16 == 0) dataPreview.append("\\n");
                }
                
                plugin.getLogger().debug("Map data preview (first 64 bytes):\\n" + dataPreview.toString());
                plugin.getLogger().debug("Sample data validation:");
                plugin.getLogger().debug("  - Black pixels (119/0x77) in sample: " + blackCount);  
                plugin.getLogger().debug("  - White pixels (8/0x08) in sample: " + whiteCount);
                plugin.getLogger().debug("  - Expected: Black=119, White=8");
                plugin.getLogger().debug("=== END MAP DATA DEBUG ===");
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to send MAP_DATA packet: " + e.getMessage());
            if (debug) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 将BufferedImage转换为Minecraft地图数据格式
     */
    private byte[] convertImageToMapData(BufferedImage image) {
        byte[] data = new byte[128 * 128];
        
        boolean debug = plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG);
        int blackPixels = 0, whitePixels = 0;
        
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                int rgb = image.getRGB(x, y);
                
                // 🔧 修复：使用Minecraft 1.21标准地图颜色
                byte color;
                if (isBlackPixel(rgb)) {
                    // 使用标准黑色 - Minecraft地图颜色表
                    color = (byte) 119; // 黑色 (Terracotta Black: 29*4+3=119)
                    blackPixels++;
                } else {
                    // 使用标准白色
                    color = (byte) 8;   // 白色 (Snow: 2*4+0=8)
                    whitePixels++;
                }
                
                // 确保坐标映射正确：row-major order
                data[y * 128 + x] = color;
                
                // 调试：输出多个区域的像素信息
                if (debug) {
                    // 左上角
                    if (x < 3 && y < 3) {
                        int red = (rgb >> 16) & 0xFF;
                        int green = (rgb >> 8) & 0xFF;
                        int blue = rgb & 0xFF;
                        plugin.getLogger().debug(String.format("Corner[%d,%d]: RGB(%d,%d,%d) -> %s -> color:%d", 
                            x, y, red, green, blue, isBlackPixel(rgb) ? "BLACK" : "WHITE", color & 0xFF));
                    }
                    // 中心区域
                    if (x >= 60 && x <= 67 && y >= 60 && y <= 67) {
                        int red = (rgb >> 16) & 0xFF;
                        int green = (rgb >> 8) & 0xFF;
                        int blue = rgb & 0xFF;
                        plugin.getLogger().debug(String.format("Center[%d,%d]: RGB(%d,%d,%d) -> %s -> color:%d", 
                            x, y, red, green, blue, isBlackPixel(rgb) ? "BLACK" : "WHITE", color & 0xFF));
                    }
                }
            }
        }
        
        if (debug) {
            plugin.getLogger().debug(String.format("Image conversion completed: %d black pixels, %d white pixels", blackPixels, whitePixels));
        }
        
        return data;
    }

    /**
     * 判断像素是否为黑色
     */
    private boolean isBlackPixel(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        
        // 使用更严格的阈值来判断黑白像素，提高QR码的对比度
        int luminance = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
        return luminance < 128; // 亮度低于128认为是黑色
    }

    /**
     * 为玩家分配虚拟地图ID
     */
    private int allocateVirtualMapId(UUID playerUuid) {
        // 如果玩家已经有地图ID，返回现有的
        Integer existing = playerMapIds.get(playerUuid);
        if (existing != null) {
            return existing;
        }

        // 分配新的地图ID
        int newId = mapIdAllocator.getAndIncrement();
        if (newId > MAX_VIRTUAL_MAP_ID) {
            // ID用完了，重置分配器
            mapIdAllocator.set(MIN_VIRTUAL_MAP_ID);
            newId = mapIdAllocator.getAndIncrement();
        }

        playerMapIds.put(playerUuid, newId);
        return newId;
    }
}
