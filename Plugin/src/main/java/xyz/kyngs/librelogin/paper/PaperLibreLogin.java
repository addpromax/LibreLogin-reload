/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import co.aikar.commands.PaperCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import net.kyori.adventure.audience.Audience;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.CustomChart;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.SLF4JLogger;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;
import xyz.kyngs.librelogin.paper.image.VirtualMapProjector;
import xyz.kyngs.librelogin.common.util.CancellableTask;
import xyz.kyngs.librelogin.paper.protocol.PacketListener;
import xyz.kyngs.librelogin.paper.scheduler.SchedulerAdapter;
import xyz.kyngs.librelogin.paper.scheduler.SchedulerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG;

public class PaperLibreLogin extends AuthenticLibreLogin<Player, World> {

    private final PaperBootstrap bootstrap;
    private PaperListeners listeners;
    private Object configurationPhaseListener;
    private GrimIntegration grimIntegration;
    private boolean started;
    private xyz.kyngs.librelogin.paper.dialogs.DialogManager dialogManager;
    private xyz.kyngs.librelogin.paper.inventory.InventoryManager inventoryManager;
    private VirtualMapProjector virtualMapProjector;
    private xyz.kyngs.librelogin.common.config.AnnouncementManager announcementManager;
    private boolean usingExternalPacketEvents;
    private final SchedulerAdapter scheduler;

    public PaperLibreLogin(PaperBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        this.started = false;
        this.usingExternalPacketEvents = false;
        
        // Initialize scheduler adapter based on platform (Bukkit/Paper or Folia)
        this.scheduler = SchedulerFactory.createScheduler();
        bootstrap.getSLF4JLogger().info("Using " + scheduler.getSchedulerType() + " scheduler adapter");

        // Check if PacketEvents is already loaded as an external plugin
        var packetEventsPlugin = Bukkit.getPluginManager().getPlugin("packetevents");
        if (packetEventsPlugin != null && packetEventsPlugin.isEnabled()) {
            // PacketEvents is loaded externally, reuse its API instance
            usingExternalPacketEvents = true;
            bootstrap.getSLF4JLogger().info("Detected external PacketEvents plugin, using it instead of bundled version");
        } else {
            // Use bundled PacketEvents
            bootstrap.getSLF4JLogger().info("Loading bundled PacketEvents");
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(bootstrap));

            PacketEvents.getAPI().getSettings()
                    .checkForUpdates(false)
                    .bStats(false);

            PacketEvents.getAPI().load();
        }
    }

    public PaperBootstrap getBootstrap() {
        return bootstrap;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return bootstrap.getResource(name);
    }

    @Override
    public File getDataFolder() {
        return bootstrap.getDataFolder();
    }

    @Override
    public String getVersion() {
        return bootstrap.getDescription().getVersion();
    }

    @Override
    public boolean isPresent(UUID uuid) {
        return Bukkit.getPlayer(uuid) != null;
    }

    @Override
    public boolean multiProxyEnabled() {
        return false;
    }

    @Override
    public Player getPlayerForUUID(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    protected PaperPlatformHandle providePlatformHandle() {
        return new PaperPlatformHandle(this);
    }

    @Override
    protected Logger provideLogger() {
        return new SLF4JLogger(bootstrap.getSLF4JLogger(), () -> getConfiguration().get(DEBUG));
    }

    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> provideManager() {
        return new PaperCommandManager(bootstrap);
    }

    @Override
    protected boolean mainThread() {
        return scheduler.isPrimaryThread() && started;
    }

    @Override
    public Player getPlayerFromIssuer(CommandIssuer issuer) {
        var bukkitIssuer = (BukkitCommandIssuer) issuer;

        return bukkitIssuer.getPlayer();
    }

    @Override
    protected void disable() {
        // Disable InventoryManager
        if (inventoryManager != null) {
            inventoryManager.disable();
        }
        
        // Only terminate PacketEvents if we're using the bundled version
        if (!usingExternalPacketEvents) {
            PacketEvents.getAPI().terminate();
        }
        if (getDatabaseProvider() == null) return; //Not initialized

        super.disable();
    }

    @Override
    protected void enable() {

        logger = provideLogger();

        if (Bukkit.getOnlineMode()) {
            getLogger().error("!!!The server is running in online mode! LibreLogin won't start unless you set it to false!!!");
            disable();
            return;
        }

        if (Bukkit.spigot().getSpigotConfig().getBoolean("settings.bungeecord") || Bukkit.spigot().getPaperConfig().getBoolean("settings.velocity-support.enabled")) {
            getLogger().error("!!!This server is running under a proxy, LibreLogin won't start!!!");
            getLogger().error("If you want to use LibreLogin under a proxy, place it on the proxy and remove it from the server.");
            disable();
            return;
        }

        try {
            super.enable();
        } catch (ShutdownException e) {
            return;
        }

        // Display plugin information
        getLogger().info("This server is running FOSS authentication plugin, LibreLogin.");
        getLogger().info("Version: " + getVersion());
        getLogger().info("Authors: magicbili, kyngs, and other contributors");
        getLogger().info("Source: https://github.com/addpromax/LibreLogin-reload");
        getLogger().info("License: Mozilla Public License 2.0");
        getLogger().info("Platform: " + getPlatformHandle().getPlatformIdentifier());
        getLogger().warn("⚠️ WARNING: The original author has stopped updating this plugin.");
        getLogger().warn("Please DO NOT report issues to the original author's GitHub repository.");

        var provider = getEventProvider();

        if (pluginPresent("GrimAC")) {
            grimIntegration = GrimIntegration.create(this);
        }

        provider.subscribe(provider.getTypes().authenticated, event -> {
            var player = event.getPlayer();
            if (player == null) return;
            player.setInvisible(false);
            
            // For PREMIUM and SESSION login, we need to check announcement and open menu
            // because authorize() is not called for these cases
            if (event.getReason() == xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent.AuthenticationReason.PREMIUM ||
                event.getReason() == xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent.AuthenticationReason.SESSION) {
                
                var user = event.getUser();
                if (user == null) {
                    return;
                }
                
                if (player instanceof org.bukkit.entity.Player bukkitPlayer) {
                    var authProvider = getAuthorizationProvider();
                    if (authProvider instanceof xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider) {
                        // Call checkAndShowAnnouncement for premium/session login
                        // This will handle both announcement and CustomScreenMenu
                        var authenticProvider = (xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider<org.bukkit.entity.Player, org.bukkit.World>) authProvider;
                        authenticProvider.checkAndShowAnnouncementForAuthenticatedEvent(user, bukkitPlayer, event.getReason());
                    }
                }
            }
        });

        listeners = new PaperListeners(this);

        Bukkit.getPluginManager().registerEvents(listeners, bootstrap);
        Bukkit.getPluginManager().registerEvents(new Blockers(this), bootstrap);
        
        // Initialize PacketEvents if we're using the bundled version
        if (!usingExternalPacketEvents) {
            try {
                PacketEvents.getAPI().init();
                if (getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    getLogger().debug("PacketEvents initialized successfully");
                }
            } catch (Exception e) {
                getLogger().error("Failed to initialize PacketEvents: " + e.getMessage());
                if (getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    e.printStackTrace();
                }
            }
        }
        
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener(listeners));

        // Initialize AnnouncementManager for managing announcement.yml  
        announcementManager = new xyz.kyngs.librelogin.common.config.AnnouncementManager(this);
        if (announcementManager.initialize()) {
            getLogger().info("AnnouncementManager initialized successfully");
        } else {
            getLogger().warn("AnnouncementManager initialization failed");
        }

        // Initialize FancyDialogs integration (using Java code configuration only)
        dialogManager = new xyz.kyngs.librelogin.paper.dialogs.DialogManager(this);
        dialogManager.initialize();

        try {
            // Register this listener regardless of the current value so the
            // setting can be toggled with the normal configuration reload.
            configurationPhaseListener = new ConfigurationPhaseListener(this);
            Bukkit.getPluginManager().registerEvents((ConfigurationPhaseListener) configurationPhaseListener, bootstrap);
            if (getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_USE_CONFIGURATION_PHASE)
                    && getConfiguration().get(ConfigurationKeys.USE_FANCYDIALOGS)) {
                getLogger().info("Configuration-phase authentication enabled. Players must authenticate before joining.");
            }
        } catch (LinkageError error) {
            getLogger().warn("Configuration-phase authentication requires Paper 1.21.6 or newer; keeping the normal post-join flow.");
        }

        // Initialize InventoryManager for hiding/restoring player inventories
        inventoryManager = new xyz.kyngs.librelogin.paper.inventory.InventoryManager(this);
        inventoryManager.enable(); // Register packet listener
        if (getConfiguration().get(ConfigurationKeys.DEBUG)) {
            getLogger().debug("InventoryManager initialized and enabled");
        }

        started = true;
    }

    /**
     * 获取虚拟地图投影器实例
     * 
     * @return VirtualMapProjector实例，如果未初始化则返回null
     */
    public VirtualMapProjector getVirtualMapProjector() {
        return virtualMapProjector;
    }

    /**
     * Gets the announcement manager instance.
     *
     * @return the announcement manager, or null if not initialized
     */
    public xyz.kyngs.librelogin.common.config.AnnouncementManager getAnnouncementManager() {
        return announcementManager;
    }

    public Object getConfigurationPhaseListener() {
        return configurationPhaseListener;
    }

    public long getConfigurationPhaseTimeoutMillis(int configuredSeconds) {
        long configuredMillis = Math.max(1, configuredSeconds) * 1000L;
        return grimIntegration == null
                ? configuredMillis
                : grimIntegration.limitConfigurationPhaseTimeout(configuredMillis);
    }

    @Override
    public void authorize(Player player, User user, Audience audience) {
        // Clean up virtual map data
        if (virtualMapProjector != null) {
            virtualMapProjector.cleanupVirtualMap(player);
        }

        // Restore player inventory if it was hidden
        if (inventoryManager != null && inventoryManager.isInventoryHidden(player)) {
            delay(() -> inventoryManager.restoreInventory(player), 100);
        }

        // REMOVED: No longer teleporting players after authentication
        // Players stay at their current location
        // var location = listeners.getSpawnLocationCache().getIfPresent(player);
        // if (location != null) {
        //     listeners.getSpawnLocationCache().invalidate(player);
        //     var finalLocation = location;
        //     PaperUtil.runSyncAndWait(() -> player.teleportAsync(finalLocation), this);
        // }
    }

    @Override
    public CancellableTask delay(Runnable runnable, long delayInMillis) {
        return scheduler.runTaskLaterAsynchronously(bootstrap, runnable, delayInMillis / 50);
    }

    @Override
    public CancellableTask repeat(Runnable runnable, long delayInMillis, long repeatInMillis) {
        return scheduler.runTaskTimerAsynchronously(bootstrap, runnable, delayInMillis / 50, repeatInMillis / 50);
    }

    @Override
    public boolean pluginPresent(String pluginName) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    @Override
    protected AuthenticImageProjector<Player, World> provideImageProjector() {
        try {
            // PacketEvents API is available after load() in constructor
            // It will be fully initialized with init() later in enable()
            if (PacketEvents.getAPI() == null) {
                getLogger().warn("PacketEvents API is not available, ImageProjector will not be available");
                return null;
            }
            
            // 创建VirtualMapProjector作为主要的地图投影器
            virtualMapProjector = new VirtualMapProjector(this);
            // Note: virtualMapProjector.enable() is called later in AuthenticLibreLogin.enable()
            return virtualMapProjector;
        } catch (Exception e) {
            getLogger().error("Failed to initialize VirtualMapProjector: " + e.getMessage());
            if (getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    protected void initMetrics(CustomChart... charts) {
        var metrics = new Metrics(bootstrap, 17915);

        for (var chart : charts) {
            metrics.addCustomChart(chart);
        }

        var isVelocity = new SimplePie("is_velocity", () -> "Paper");

        metrics.addCustomChart(isVelocity);
    }

    @Override
    protected void shutdownProxy(int code) {
        bootstrap.disable();
        bootstrap.getServer().shutdown();
        throw new ShutdownException();
    }

    @Override
    public Audience getAudienceFromIssuer(CommandIssuer issuer) {
        return ((BukkitCommandIssuer) issuer).getIssuer();
    }

    /**
     * Gets the FancyDialogs dialog manager.
     *
     * @return the dialog manager, or null if not initialized
     */
    public xyz.kyngs.librelogin.paper.dialogs.DialogManager getDialogManager() {
        return dialogManager;
    }

    public void recordHuHoBotSuccessfulLogin(Player player) {
        if (dialogManager != null) dialogManager.recordHuHoBotSuccessfulLogin(player);
    }

    /**
     * Gets the inventory manager for hiding/restoring player inventories.
     *
     * @return the inventory manager, or null if not initialized
     */
    public xyz.kyngs.librelogin.paper.inventory.InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    /**
     * Gets the scheduler adapter for this platform.
     *
     * @return the scheduler adapter (Bukkit or Folia)
     */
    public SchedulerAdapter getScheduler() {
        return scheduler;
    }
}
