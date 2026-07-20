/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.dialogs;

import com.fancyinnovations.fancydialogs.api.Dialog;
import com.fancyinnovations.fancydialogs.api.FancyDialogs;
import com.github.retrooper.packetevents.PacketEvents;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.totp.TOTPData;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages FancyDialogs integration for LibreLogin.
 * Handles dialog creation, display, and version compatibility checking.
 *
 * @author LibreLogin Contributors
 */
public class DialogManager implements Listener {

    private final PaperLibreLogin plugin;
    private final Map<UUID, Dialog> activeDialogs;
    private final Map<UUID, PendingTwoFactorSetup> pendingTwoFactorSetups;
    private final Map<UUID, BossBar> activeBossBars;
    private final LibreLoginAction actionHandler;
    private FancyDialogs fancyDialogs;
    private boolean available;

    private LoginDialog loginDialog;
    private RegisterDialog registerDialog;
    private PasswordResetDialog passwordResetDialog;
    private EmailStatusDialog emailStatusDialog;
    private TwoFactorSetupDialog twoFactorSetupDialog;
    private AnnouncementDialog announcementDialog;
    private EmailRegisterDialog emailRegisterDialog;
    private EmailVerificationDialog emailVerificationDialog;
    private EmailInputDialog emailInputDialog;
    private RegisterConfirmationDialog registerConfirmationDialog;
    private HuHoBotPasswordResetDialog huhobotPasswordResetDialog;
    private HuHoBotResetRequestDialog huhobotResetRequestDialog;
    private HuHoBotIntegration huhobotIntegration;
    private final VirtualMapDropListener virtualMapDropListener;

    public DialogManager(PaperLibreLogin plugin) {
        this.plugin = plugin;
        this.activeDialogs = new ConcurrentHashMap<>();
        this.activeBossBars = new ConcurrentHashMap<>();
        this.actionHandler = new LibreLoginAction(plugin, this);
        
        // 🔧 创建并注册虚拟地图丢出监听器  
        this.virtualMapDropListener = new VirtualMapDropListener(plugin, this);
        PacketEvents.getAPI().getEventManager().registerListener(virtualMapDropListener);
        this.pendingTwoFactorSetups = new ConcurrentHashMap<>();
        this.available = false;
    }
    
    /**
     * Data class to hold pending 2FA setup information
     */
    public static class PendingTwoFactorSetup {
        final User user;
        final TOTPData totpData;
        
        PendingTwoFactorSetup(User user, TOTPData totpData) {
            this.user = user;
            this.totpData = totpData;
        }
    }

    /**
     * Initializes the DialogManager and checks for FancyDialogs availability.
     * This method should be called during plugin startup.
     *
     * @return true if FancyDialogs is available and initialized successfully
     */
    public boolean initialize() {
        // Check if FancyDialogs is enabled in configuration
        if (!plugin.getConfiguration().get(ConfigurationKeys.USE_FANCYDIALOGS)) {
            plugin.getLogger().info("FancyDialogs integration is disabled in configuration.");
            return false;
        }

        // Check if server is running Paper
        if (!isPaper()) {
            plugin.getLogger().warn("FancyDialogs integration is only available on Paper servers.");
            available = false;
            return false;
        }

        // Check Minecraft version
        if (!isMinecraftVersionSupported()) {
            plugin.getLogger().warn(plugin.getMessages().getRawMessage(MessageKeys.ERROR_FANCYDIALOGS_VERSION.key()));
            available = false;
            return false;
        }

        // Check if FancyDialogs plugin is installed
        try {
            var fdPlugin = Bukkit.getPluginManager().getPlugin("FancyDialogs");
            if (fdPlugin == null || !fdPlugin.isEnabled()) {
                plugin.getLogger().warn(plugin.getMessages().getRawMessage(MessageKeys.ERROR_FANCYDIALOGS_NOT_INSTALLED.key()));
                available = false;
                return false;
            }

            fancyDialogs = FancyDialogs.get();
            if (fancyDialogs == null) {
                plugin.getLogger().warn("Failed to get FancyDialogs API instance.");
                available = false;
                return false;
            }

            // Initialize dialog instances
            loginDialog = new LoginDialog(this, plugin);
            registerDialog = new RegisterDialog(this, plugin);
            passwordResetDialog = new PasswordResetDialog(this, plugin);
            emailStatusDialog = new EmailStatusDialog(this, plugin);
            twoFactorSetupDialog = new TwoFactorSetupDialog(this, plugin);
            announcementDialog = new AnnouncementDialog(this, plugin);
            emailRegisterDialog = new EmailRegisterDialog(this, plugin);
            emailVerificationDialog = new EmailVerificationDialog(this, plugin);
            emailInputDialog = new EmailInputDialog(this, plugin);
            registerConfirmationDialog = new RegisterConfirmationDialog(this, plugin);
            huhobotPasswordResetDialog = new HuHoBotPasswordResetDialog(this, plugin);
            huhobotResetRequestDialog = new HuHoBotResetRequestDialog(this, plugin);

            // Register custom dialog actions
            registerDialogActions();

            available = true;
            
            // Register this DialogManager as an event listener for map drop detection
            Bukkit.getPluginManager().registerEvents(this, plugin.getBootstrap());
            huhobotIntegration = new HuHoBotIntegration(plugin, this);
            huhobotIntegration.register();
            
            plugin.getLogger().info("FancyDialogs integration initialized successfully!");
            return true;

        } catch (NoClassDefFoundError e) {
            plugin.getLogger().warn("FancyDialogs classes not found. Make sure FancyDialogs is installed.");
            available = false;
            return false;
        } catch (Exception e) {
            plugin.getLogger().error("Failed to initialize FancyDialogs integration: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            available = false;
            return false;
        }
    }

    /**
     * Checks if FancyDialogs is available for use.
     *
     * @return true if FancyDialogs is available
     */
    public boolean isAvailable() {
        return available;
    }

    /** Starts a HuHoBot password recovery request for the player. */
    public void beginHuHoBotPasswordReset(Player player) {
        if (huhobotIntegration == null) {
            player.sendMessage(plugin.getMessages().getMessage(MessageKeys.DIALOG_COMMON_HUHOBOT_UNAVAILABLE.key()));
            return;
        }
        huhobotIntegration.begin(player);
    }

    /** Records a successful normal login for HuHoBot QQ identity promotion. */
    public void recordHuHoBotSuccessfulLogin(Player player) {
        if (huhobotIntegration != null) huhobotIntegration.recordSuccessfulLogin(player);
    }

    /** Shows the one-time HuHoBot code in a dialog instead of chat. */
    public void showHuHoBotResetRequestDialog(Player player, String code, long expiresInMinutes) {
        if (!isAvailable()) return;
        try {
            closeAllDialogs(player);
            Dialog dialog = huhobotResetRequestDialog.create(player, code, expiresInMinutes);
            fancyDialogs.getDialogRegistry().register(dialog);
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show HuHoBot recovery code dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) e.printStackTrace();
        }
    }

    /**
     * Shows the login dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     */
    public void showLoginDialog(Player player, User user) {
        showLoginDialog(player, user, null, null);
    }

    /**
     * Shows the login dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showLoginDialog(Player player, User user, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = loginDialog.create(player, user, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show login dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows the register dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     */
    public void showRegisterDialog(Player player, User user) {
        showRegisterDialog(player, user, null, null);
    }

    /**
     * Shows the register dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showRegisterDialog(Player player, User user, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            // Check for ongoing email registration verification
            var authProvider = plugin.getAuthorizationProvider();
            if (authProvider instanceof xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider authenticProvider) {
                if (authenticProvider.hasEmailRegistrationVerification(player.getUniqueId())) {
                    // Player has ongoing email verification - redirect to verification dialog
                    String email = authenticProvider.getEmailRegistrationEmail(player.getUniqueId());
                    if (email != null) {
                        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                            plugin.getLogger().debug("Redirecting player " + player.getName() + " to email verification dialog (reconnection)");
                        }
                        showEmailVerificationDialog(player, user, email, true,
                                plugin.getMessages().getRawMessage(MessageKeys.DIALOG_COMMON_EMAIL_RECONNECT.key()), "warning");
                        return;
                    }
                }
            }
            
            closeAllDialogs(player);
            Dialog dialog = registerDialog.create(player, user, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show register dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows the password reset dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     */
    public void showPasswordResetDialog(Player player, User user) {
        showPasswordResetDialog(player, user, null, null);
    }

    /**
     * Shows the password reset dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showPasswordResetDialog(Player player, User user, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = passwordResetDialog.create(player, user, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show password reset dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /** Shows the new-password form after a HuHoBot recovery code was verified. */
    public void showHuHoBotPasswordResetDialog(Player player) {
        showHuHoBotPasswordResetDialog(player, null, null);
    }

    public void showHuHoBotPasswordResetDialog(Player player, String errorMessage, String errorType) {
        if (!isAvailable()) return;
        try {
            closeAllDialogs(player);
            Dialog dialog = huhobotPasswordResetDialog.create(player, errorMessage, errorType);
            fancyDialogs.getDialogRegistry().register(dialog);
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show HuHoBot password reset dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) e.printStackTrace();
        }
    }

    /**
     * Shows the email status dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     */
    public void showEmailStatusDialog(Player player, User user) {
        showEmailStatusDialog(player, user, null, null);
    }

    /**
     * Shows the email status dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, "success" for green
     */
    public void showEmailStatusDialog(Player player, User user, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = emailStatusDialog.create(player, user, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show email status dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Closes all active dialogs for a player.
     *
     * @param player the player to close dialogs for
     */
    public void closeAllDialogs(Player player) {
        if (!isAvailable()) return;

        Dialog dialog = activeDialogs.remove(player.getUniqueId());
        if (dialog != null) {
            try {
                // 🔧 修复：先从DialogRegistry注销对话框，防止FancyDialogs认为对话框仍然打开
                fancyDialogs.getDialogRegistry().unregister(dialog.getId());
                
                // 然后关闭对话框
                dialog.close(player);
                
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("Closed and unregistered dialog " + dialog.getId() + " for player: " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().debug("Failed to close dialog for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Gets the FancyDialogs API instance.
     *
     * @return the FancyDialogs API instance, or null if not available
     */
    public FancyDialogs getFancyDialogs() {
        return fancyDialogs;
    }

    /**
     * Gets the plugin instance.
     *
     * @return the plugin instance
     */
    public PaperLibreLogin getPlugin() {
        return plugin;
    }

    /**
     * 
     * @param pluginInstance the CustomScreenMenu plugin instance
     * @param player the player to open the menu for
     * @param menuName the name of the menu to open
     * @return true if menu was opened successfully, false otherwise
     */
    private boolean tryOpenMenuViaAPI(org.bukkit.plugin.Plugin pluginInstance, org.bukkit.entity.Player player, String menuName) {
        try {
            Class<?> pluginClass = pluginInstance.getClass();
            
            // Try the known API method: setupCursor(Player, String)
            try {
                java.lang.reflect.Method setupCursorMethod = pluginClass.getMethod("setupCursor", org.bukkit.entity.Player.class, String.class);
                setupCursorMethod.invoke(pluginInstance, player, menuName);
                plugin.getLogger().info("[CustomScreenMenu] Opened menu '" + menuName + "' for " + player.getName() + " via API");
                return true;
            } catch (NoSuchMethodException e) {
                // Method not found, try alternatives
            }
            
            // Fallback: Try to find the method with different names
            String[] possibleMethodNames = {
                "setupCursor", "openMenu", "startMenu", "showMenu"
            };
            
            for (String methodName : possibleMethodNames) {
                try {
                    java.lang.reflect.Method method = pluginClass.getMethod(methodName, org.bukkit.entity.Player.class, String.class);
                    method.invoke(pluginInstance, player, menuName);
                    plugin.getLogger().info("[CustomScreenMenu] Opened menu '" + menuName + "' for " + player.getName() + " via API (" + methodName + ")");
                    return true;
                } catch (NoSuchMethodException e) {
                    continue;
                }
            }
            
            return false;
        } catch (Exception e) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().warn("[CustomScreenMenu] Error calling API: " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Attempts to open CustomScreenMenu via command execution.
     * Based on CustomScreenMenu source code, the command format is:
     * - cursormenu run <menu-name> (for self)
     * - cursormenu run <menu-name> <player> (for other player)
     * 
     * @param player the player to open the menu for
     * @param menuName the name of the menu to open
     * @return true if command executed successfully, false otherwise
     */
    private boolean tryOpenMenuViaCommand(org.bukkit.entity.Player player, String menuName) {
        try {
            // Based on Commands.java, the correct command format is: cursormenu run <menu-name>
            String command = "cursormenu run " + menuName;
            boolean success = player.performCommand(command);
            
            if (success) {
                return true;
            }
            
            // Try alternative: cmenu run <menu-name>
            command = "cmenu run " + menuName;
            success = player.performCommand(command);
            
            return success;
        } catch (Exception e) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().warn("[CustomScreenMenu] Error executing command: " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Checks if the server is running Paper.
     *
     * @return true if running Paper
     */
    private boolean isPaper() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if the Minecraft version is 1.21.6 or higher.
     * FancyDialogs requires Minecraft 1.21.6+ due to the dialog feature.
     *
     * @return true if version is supported
     */
    private boolean isMinecraftVersionSupported() {
        String version = Bukkit.getMinecraftVersion();
        
        try {
            // Parse version string (e.g., "1.21.6" -> [1, 21, 6])
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

            // Check if version is 1.21.6 or higher
            if (major > 1) return true;
            if (major == 1 && minor > 21) return true;
            if (major == 1 && minor == 21 && patch >= 6) return true;

            return false;
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to parse Minecraft version: " + version);
            return false;
        }
    }

    /**
     * Gets the login dialog instance.
     *
     * @return the login dialog
     */
    public LoginDialog getLoginDialog() {
        return loginDialog;
    }

    /**
     * Gets the register dialog instance.
     *
     * @return the register dialog
     */
    public RegisterDialog getRegisterDialog() {
        return registerDialog;
    }

    /**
     * Gets the password reset dialog instance.
     *
     * @return the password reset dialog
     */
    public PasswordResetDialog getPasswordResetDialog() {
        return passwordResetDialog;
    }

    /**
     * Gets the two-factor setup dialog instance.
     *
     * @return the two-factor setup dialog
     */
    public TwoFactorSetupDialog getTwoFactorSetupDialog() {
        return twoFactorSetupDialog;
    }

    /**
     * Gets the announcement dialog instance.
     *
     * @return the announcement dialog
     */
    public AnnouncementDialog getAnnouncementDialog() {
        return announcementDialog;
    }

    /**
     * Gets the email register dialog instance.
     *
     * @return the email register dialog
     */
    public EmailRegisterDialog getEmailRegisterDialog() {
        return emailRegisterDialog;
    }

    /**
     * Gets the email verification dialog instance.
     *
     * @return the email verification dialog
     */
    public EmailVerificationDialog getEmailVerificationDialog() {
        return emailVerificationDialog;
    }

    /**
     * Shows the 2FA setup dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param totpData the TOTP data containing QR code and secret
     */
    public void showTwoFactorSetupDialog(Player player, xyz.kyngs.librelogin.api.database.User user, xyz.kyngs.librelogin.api.totp.TOTPData totpData) {
        showTwoFactorSetupDialog(player, user, totpData, null, null);
    }

    /**
     * Shows the 2FA setup dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param totpData the TOTP data containing QR code and secret
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showTwoFactorSetupDialog(Player player, xyz.kyngs.librelogin.api.database.User user, xyz.kyngs.librelogin.api.totp.TOTPData totpData, String errorMessage, String errorType) {
        if (!isAvailable()) {
            plugin.getLogger().warn("Cannot show 2FA dialog - DialogManager not available for player: " + player.getName());
            return;
        }

        try {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Creating 2FA setup dialog for player: " + player.getName());
            }
            
            closeAllDialogs(player);
            Dialog dialog = twoFactorSetupDialog.create(player, user, totpData, errorMessage, errorType);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Registering 2FA dialog to FancyDialogs registry");
            }
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Opening 2FA dialog for player: " + player.getName());
            }
            
            dialog.open(player);
            
            plugin.getLogger().info("Successfully opened 2FA setup dialog for player: " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show 2FA setup dialog for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows the announcement dialog to a player.
     *
     * @param player the player to show the dialog to
     */
    public void showAnnouncementDialog(Player player) {
        showAnnouncementDialog(player, null, null);
    }

    /**
     * Shows the announcement dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showAnnouncementDialog(Player player, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = announcementDialog.create(player, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show announcement dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows the email register dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     */
    public void showEmailRegisterDialog(Player player, User user) {
        showEmailRegisterDialog(player, user, null, null);
    }

    /**
     * Shows the email register dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showEmailRegisterDialog(Player player, User user, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = emailRegisterDialog.create(player, user, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show email register dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows the email verification dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param email  the email address being verified
     */
    public void showEmailVerificationDialog(Player player, User user, String email) {
        showEmailVerificationDialog(player, user, email, false, null, null);
    }

    /**
     * Shows the email verification dialog to a player with resend button control.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param email  the email address being verified
     * @param showResendButton whether to show the resend email button
     */
    public void showEmailVerificationDialog(Player player, User user, String email, boolean showResendButton) {
        showEmailVerificationDialog(player, user, email, showResendButton, null, null);
    }

    /**
     * Shows the register confirmation dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param password the already validated password
     * @param passwordConfirm the password confirmation
     */
    public void showRegisterConfirmationDialog(Player player, User user, String password, String passwordConfirm) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = registerConfirmationDialog.create(player, user, password, passwordConfirm);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show register confirmation dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows the email input dialog to a player.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param password the already validated password
     * @param passwordConfirm the password confirmation
     */
    public void showEmailInputDialog(Player player, User user, String password, String passwordConfirm) {
        showEmailInputDialog(player, user, password, passwordConfirm, null, null);
    }

    /**
     * Shows the email input dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param password the already validated password
     * @param passwordConfirm the password confirmation
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showEmailInputDialog(Player player, User user, String password, String passwordConfirm, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = emailInputDialog.create(player, user, password, passwordConfirm, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show email input dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Shows the email verification dialog with an optional error message.
     *
     * @param player the player to show the dialog to
     * @param user   the user data
     * @param email  the email address being verified
     * @param showResendButton whether to show the resend email button
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow
     */
    public void showEmailVerificationDialog(Player player, User user, String email, boolean showResendButton, String errorMessage, String errorType) {
        if (!isAvailable()) return;

        try {
            closeAllDialogs(player);
            Dialog dialog = emailVerificationDialog.create(player, user, email, showResendButton, errorMessage, errorType);
            
            // Register dialog to DialogRegistry so FancyDialogs can find it
            fancyDialogs.getDialogRegistry().register(dialog);
            
            activeDialogs.put(player.getUniqueId(), dialog);
            dialog.open(player);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show email verification dialog: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Registers a pending 2FA setup that will be opened when the player drops the map.
     *
     * @param player the player
     * @param user the user data
     * @param totpData the TOTP data
     */
    public void registerPendingTwoFactorSetup(Player player, User user, TOTPData totpData) {
        pendingTwoFactorSetups.put(player.getUniqueId(), new PendingTwoFactorSetup(user, totpData));
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Registered pending 2FA setup for player: " + player.getName());
        }
    }
    
    /**
     * Handles map drop event to open 2FA setup dialog.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // 🔧 增强调试：检查所有丢出事件
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== PLAYER DROP ITEM DEBUG ===");
            plugin.getLogger().debug("Player: " + player.getName() + " dropped item");
            plugin.getLogger().debug("Item type: " + event.getItemDrop().getItemStack().getType());
            plugin.getLogger().debug("Item material: " + event.getItemDrop().getItemStack().getType().name());
            plugin.getLogger().debug("Has pending 2FA: " + (pendingTwoFactorSetups.containsKey(uuid)));
        }
        
        // Check if this player has a pending 2FA setup
        PendingTwoFactorSetup pending = pendingTwoFactorSetups.get(uuid);
        if (pending == null) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("No pending 2FA setup found for player: " + player.getName());
            }
            return;
        }
        
        // 🔧 修复：检查多种地图类型和虚拟地图标识
        if (event.getItemDrop().getItemStack().getType() == Material.FILLED_MAP || 
            event.getItemDrop().getItemStack().getType() == Material.MAP) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("✅ Player " + player.getName() + " dropped map, cancelling drop and opening 2FA setup dialog");
                plugin.getLogger().debug("Map type confirmed: " + event.getItemDrop().getItemStack().getType());
            }
            
            // Cancel the drop event to prevent the map from being dropped
            event.setCancelled(true);
            
            // Remove the map from player's hand
            player.getInventory().setItemInMainHand(null);
            
            // Remove from pending
            pendingTwoFactorSetups.remove(uuid);
            
            // Open the 2FA setup dialog
            plugin.delay(() -> {
                showTwoFactorSetupDialog(player, pending.user, pending.totpData);
            }, 100);
        }
    }
    
    /**
     * Checks if a player has a pending 2FA setup.
     *
     * @param player the player
     * @return true if the player has a pending 2FA setup
     */
    public boolean hasPendingTwoFactorSetup(Player player) {
        return pendingTwoFactorSetups.containsKey(player.getUniqueId());
    }
    
    /**
     * Gets the pending 2FA setup for a player.
     *
     * @param player the player
     * @return the pending setup or null if none exists
     */
    public PendingTwoFactorSetup getPendingTwoFactorSetup(Player player) {
        return pendingTwoFactorSetups.get(player.getUniqueId());
    }
    
    /**
     * Cancels a pending 2FA setup for a player.
     *
     * @param player the player
     */
    public void cancelPendingTwoFactorSetup(Player player) {
        pendingTwoFactorSetups.remove(player.getUniqueId());
    }

    /**
     * Shows a BossBar to a player and manages it to prevent duplicates.
     * If the player already has an active BossBar, it will be hidden first.
     *
     * @param player the player to show the BossBar to
     * @param bossBar the BossBar to show
     * @param autoHideDelayMs delay in milliseconds after which to automatically hide the BossBar
     */
    public void showBossBar(Player player, BossBar bossBar, long autoHideDelayMs) {
        // Hide any existing BossBar for this player first
        hideBossBar(player);
        
        // Show the new BossBar
        player.showBossBar(bossBar);
        activeBossBars.put(player.getUniqueId(), bossBar);
        
        // Schedule automatic hiding
        if (autoHideDelayMs > 0) {
            plugin.delay(() -> {
                hideBossBar(player);
            }, autoHideDelayMs);
        }
    }

    /**
     * Hides the active BossBar for a player.
     *
     * @param player the player to hide the BossBar for
     */
    public void hideBossBar(Player player) {
        BossBar bossBar = activeBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            try {
                player.hideBossBar(bossBar);
            } catch (Exception e) {
                // Silently ignore exceptions when hiding BossBar
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("Failed to hide BossBar for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handles player quit event to clean up pending 2FA setups, saved inventories, and BossBars.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Clean up any pending 2FA setup
        if (pendingTwoFactorSetups.remove(uuid) != null) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Cleaned up pending 2FA setup for disconnected player: " + player.getName());
            }
        }
        
        // Clean up active BossBar
        if (activeBossBars.remove(uuid) != null) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Cleaned up active BossBar for disconnected player: " + player.getName());
            }
        }
        
        // Clean up saved inventory data
        var inventoryManager = plugin.getInventoryManager();
        if (inventoryManager != null) {
            inventoryManager.cleanup(player);
        }
    }

    /**
     * Registers custom dialog actions for LibreLogin authentication.
     */
    private void registerDialogActions() {
        var actionRegistry = fancyDialogs.getDialogActionRegistry();

        // Core authentication actions
        actionRegistry.registerAction("librelogin_login", actionHandler);
        actionRegistry.registerAction("librelogin_register", actionHandler);
        actionRegistry.registerAction("librelogin_reset_password", actionHandler);
        actionRegistry.registerAction("librelogin_huhobot_reset", actionHandler);
        actionRegistry.registerAction("librelogin_huhobot_password_reset", actionHandler);
        
        // Simple button actions (no data)
        actionRegistry.registerAction("librelogin_forgot_password", actionHandler);
        actionRegistry.registerAction("librelogin_continue_password_reset", actionHandler);
        actionRegistry.registerAction("librelogin_back_to_login", actionHandler);
        actionRegistry.registerAction("librelogin_disconnect", actionHandler);
        actionRegistry.registerAction("librelogin_announcement_confirm", actionHandler);
        actionRegistry.registerAction("librelogin_back_to_register", actionHandler);
        
        // 2FA actions
        actionRegistry.registerAction("librelogin_2fa_setup", actionHandler);
        actionRegistry.registerAction("librelogin_2fa_skip", actionHandler);
        actionRegistry.registerAction("librelogin_2fa_rescan", actionHandler);
        
        // Email registration actions
        actionRegistry.registerAction("librelogin_email_register", actionHandler);
        actionRegistry.registerAction("librelogin_email_verify", actionHandler);
        actionRegistry.registerAction("librelogin_resend_registration_email", actionHandler);
        actionRegistry.registerAction("librelogin_email_input", actionHandler);
        
        // Special confirmation actions with dedicated handlers
        actionRegistry.registerAction("librelogin_register_confirm_continue", (player, dialog, data) -> {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Direct action call: librelogin_register_confirm_continue, data: " + data);
            }
            // Handle confirm continue registration directly
            actionHandler.handleRegisterConfirmContinue(player, data);
        });
        
        actionRegistry.registerAction("librelogin_go_email_register", (player, dialog, data) -> {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Direct action call: librelogin_go_email_register, data: " + data);
            }
            // Handle go to email register directly  
            actionHandler.handleGoEmailRegister(player, data);
        });
        
        plugin.getLogger().info("Registered " + 18 + " FancyDialogs actions");
        
        // Debug: List all registered actions
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Registered FancyDialogs Actions ===");
            plugin.getLogger().debug("Core: librelogin_login, librelogin_register, librelogin_reset_password");
            plugin.getLogger().debug("Buttons: librelogin_forgot_password, librelogin_continue_password_reset, librelogin_back_to_login, librelogin_disconnect, etc.");
            plugin.getLogger().debug("2FA: librelogin_2fa_setup, librelogin_2fa_skip, librelogin_2fa_rescan");
            plugin.getLogger().debug("Email: librelogin_email_register, librelogin_email_verify, librelogin_email_input");
            plugin.getLogger().debug("Confirmation: librelogin_register_confirm_continue, librelogin_go_email_register");
        }
    }

    public boolean completeConfigurationPhaseRegistration(Player player, User user) {
        actionHandler.completeConfigurationPhaseRegistration(player, user);
        return true;
    }
}
