/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.image;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;

import java.awt.image.BufferedImage;

/**
 * ImageProjector implementation using PacketEvents.
 * Displays QR codes on maps for 2FA setup.
 * 
 * @author LibreLogin Contributors
 */
public class PacketEventsImageProjector extends AuthenticImageProjector<Player, org.bukkit.World> {

    public PacketEventsImageProjector(AuthenticLibreLogin<Player, org.bukkit.World> plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        // PacketEvents is loaded in constructor and initialized in enable()
        var api = PacketEvents.getAPI();
        if (api != null) {
            plugin.getLogger().info("PacketEventsImageProjector enabled");
            if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("PacketEvents API status: " + 
                    (api.isInitialized() ? "initialized" : "loaded (will be initialized soon)"));
            }
        } else {
            plugin.getLogger().warn("PacketEventsImageProjector enabled but PacketEvents API is null!");
        }
    }

    @Override
    public void project(BufferedImage image, Player player) {
        boolean debug = plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG);
        
        if (debug) {
            plugin.getLogger().debug("Starting QR code projection for player: " + player.getName());
            plugin.getLogger().debug("Original image size: " + image.getWidth() + "x" + image.getHeight());
        }

        // Resize image to 128x128 if needed
        if (image.getWidth() != 128 || image.getHeight() != 128) {
            var resized = new BufferedImage(128, 128, image.getType());
            var graphics = resized.createGraphics();
            graphics.drawImage(image, 0, 0, 128, 128, 0, 0, image.getWidth(), image.getHeight(), null);
            graphics.dispose();
            image = resized;
            if (debug) {
                plugin.getLogger().debug("Resized image to 128x128");
            }
        }

        final BufferedImage finalImage = image;

        // Create a real Bukkit MapView and render QR code on it
        MapView mapView = Bukkit.createMap(player.getWorld());
        mapView.setScale(MapView.Scale.NORMAL);
        mapView.setTrackingPosition(false);
        mapView.setLocked(true);
        
        // Remove all default renderers
        for (MapRenderer renderer : mapView.getRenderers()) {
            mapView.removeRenderer(renderer);
        }

        // Add custom QR code renderer
        mapView.addRenderer(new MapRenderer() {
            private boolean rendered = false;

            @Override
            @SuppressWarnings("deprecation")
            public void render(MapView map, MapCanvas canvas, Player player) {
                if (rendered) return;
                rendered = true;

                // Draw the QR code image onto the map canvas
                for (int x = 0; x < 128; x++) {
                    for (int y = 0; y < 128; y++) {
                        int pixel = finalImage.getRGB(x, y);
                        // Map black pixels to dark color, others to light color
                        byte color = (byte) (pixel == -16777216 ? 116 : 56);
                        canvas.setPixel(x, y, color);
                    }
                }
                
                if (debug) {
                    plugin.getLogger().debug("Rendered QR code onto map canvas");
                }
            }
        });

        int mapId = mapView.getId();

        if (debug) {
            plugin.getLogger().debug("Created MapView with ID: " + mapId);
        }

        // Create Bukkit map item
        org.bukkit.inventory.ItemStack bukkitMapItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP);
        org.bukkit.inventory.meta.MapMeta mapMeta = (org.bukkit.inventory.meta.MapMeta) bukkitMapItem.getItemMeta();
        if (mapMeta != null) {
            mapMeta.setMapView(mapView);
            bukkitMapItem.setItemMeta(mapMeta);
        }

        // Give the player the map in main hand (server-side)
        // This allows Bukkit to automatically send MAP_DATA packets
        player.getInventory().setItemInMainHand(bukkitMapItem);

        if (debug) {
            plugin.getLogger().debug("Gave player map item in main hand (server-side)");
        }

        // Hide the rest of the inventory via packets
        if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin) {
            var inventoryManager = paperPlugin.getInventoryManager();
            if (inventoryManager != null) {
                // Use packet-based inventory hiding
                if (inventoryManager instanceof xyz.kyngs.librelogin.paper.inventory.InventoryManager manager) {
                    manager.hideInventory(player);
                    if (debug) {
                        plugin.getLogger().debug("Hidden inventory contents via packets for player: " + player.getName());
                    }
                }
            }
        }

        if (debug) {
            plugin.getLogger().debug("QR code projection completed for player: " + player.getName());
        }
    }

    @Override
    public boolean canProject(Player player) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        return user != null;
    }
}
