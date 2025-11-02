/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.totp.TOTPData;
import xyz.kyngs.librelogin.common.AuthenticHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.event.events.AuthenticAuthenticatedEvent;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuthenticAuthorizationProvider<P, S> extends AuthenticHandler<P, S> implements AuthorizationProvider<P> {

    private final Map<P, Boolean> unAuthorized;
    private final Map<P, String> awaiting2FA;
    private final Cache<UUID, EmailVerifyData> emailConfirmCache;
    private final Cache<UUID, String> passwordResetCache;
    private final HashSet<P> usingFancyDialogs;

    public AuthenticAuthorizationProvider(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
        unAuthorized = new ConcurrentHashMap<>();
        awaiting2FA = new ConcurrentHashMap<>();
        usingFancyDialogs = new HashSet<>();

        Integer millis = plugin.getConfiguration().get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);

        if (millis > 0) {
            plugin.repeat(this::notifyUnauthorized, 0, millis);
        }

        plugin.repeat(this::broadcastActionbars, 0, 1000);

        // Use configured timeout for email verification
        int emailTimeoutSeconds = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_VERIFICATION_TIMEOUT);
        emailConfirmCache = Caffeine.newBuilder()
                .expireAfterWrite(emailTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        passwordResetCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    public Cache<UUID, EmailVerifyData> getEmailConfirmCache() {
        return emailConfirmCache;
    }

    public Cache<UUID, String> getPasswordResetCache() {
        return passwordResetCache;
    }

    public void onExit(P player) {
        stopTracking(player);
        awaiting2FA.remove(player);
        usingFancyDialogs.remove(player);
        
        UUID playerUUID = platformHandle.getUUIDForPlayer(player);
        
        // Check if player has ongoing email registration verification
        var emailData = emailConfirmCache.getIfPresent(playerUUID);
        if (emailData != null && isEmailRegistrationData(emailData)) {
            // Don't invalidate email registration verification - let it expire naturally
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Preserving email registration verification state for player " + player + " (UUID: " + playerUUID + ")");
            }
        } else {
            // For other email confirmations (like email changes), invalidate immediately
            emailConfirmCache.invalidate(playerUUID);
        }
        
        passwordResetCache.invalidate(playerUUID);
    }

    /**
     * Checks if the player needs to see an announcement and shows it if necessary.
     *
     * @param user the user data
     * @param player the player
     */
    private void checkAndShowAnnouncement(User user, P player) {
        // Only show announcements on Paper with FancyDialogs
        if (!(plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin)) {
            return;
        }

        var dialogManager = paperPlugin.getDialogManager();
        if (dialogManager == null || !dialogManager.isAvailable()) {
            return;
        }

        // Check if player is a Bukkit player
        if (!(player instanceof org.bukkit.entity.Player bukkitPlayer)) {
            return;
        }

        try {
            // Get announcement manager from paper plugin
            var announcementManager = paperPlugin.getAnnouncementManager();
            if (announcementManager == null) {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("AnnouncementManager not available");
                }
                return;
            }

            // Check if announcements are enabled in the yml file
            if (!announcementManager.isAnnouncementEnabled()) {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("Announcements disabled in announcement.yml");
                }
                return;
            }

            // Get current announcement hash
            String currentAnnouncementHash = announcementManager.getCurrentHash();
            
            // Get player's last seen announcement hash
            String lastSeenHash = user.getLastSeenAnnouncementHash();
            
            // Show announcement if player hasn't seen current version or hash has changed
            if (lastSeenHash == null || !lastSeenHash.equals(currentAnnouncementHash)) {
                int delay = plugin.getConfiguration().get(ConfigurationKeys.ANNOUNCEMENT_DELAY);
                
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("Scheduling announcement for player " + bukkitPlayer.getName() 
                        + " (last seen hash: " + lastSeenHash + ", current hash: " + currentAnnouncementHash + ")");
                }
                
                // Show announcement after a delay to allow player to fully load
                plugin.delay(() -> {
                    dialogManager.showAnnouncementDialog(bukkitPlayer);
                }, delay);
            } else {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("Player " + bukkitPlayer.getName() 
                        + " has already seen current announcement (hash: " + currentAnnouncementHash + ")");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error checking announcement for player " + player + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isAuthorized(P player) {
        return !unAuthorized.containsKey(player);
    }

    @Override
    public boolean isAwaiting2FA(P player) {
        return awaiting2FA.containsKey(player);
    }

    @Override
    public void authorize(User user, P player, AuthenticatedEvent.AuthenticationReason reason) {
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Authorization Attempt ===");
            plugin.getLogger().debug("Player: " + platformHandle.getUsernameForPlayer(player));
            plugin.getLogger().debug("Reason: " + reason);
            plugin.getLogger().debug("isAuthorized before: " + isAuthorized(player));
            plugin.getLogger().debug("unAuthorized contains player: " + unAuthorized.containsKey(player));
            plugin.getLogger().debug("awaiting2FA contains player: " + awaiting2FA.containsKey(player));
        }
        
        if (isAuthorized(player)) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("❌ Player " + platformHandle.getUsernameForPlayer(player) + " is already authorized, skipping authorization");
            }
            return; // 不抛出异常，直接返回以避免重复授权问题
        }
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("✅ Proceeding with authorization for player: " + platformHandle.getUsernameForPlayer(player));
        }
        
        stopTracking(player);

        // Close any active FancyDialogs
        closeFancyDialog(player);

        user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
        user.setIp(platformHandle.getIP(player));
        plugin.getDatabaseProvider().updateUser(user);

        var audience = platformHandle.getAudienceForPlayer(player);

        audience.clearTitle();
        audience.sendActionBar(Component.empty());
        plugin.getEventProvider().fire(plugin.getEventTypes().authenticated, new AuthenticAuthenticatedEvent<>(user, player, plugin, reason));
        plugin.authorize(player, user, audience);
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("🎉 Successfully authorized player: " + platformHandle.getUsernameForPlayer(player));
            plugin.getLogger().debug("Final isAuthorized status: " + isAuthorized(player));
            plugin.getLogger().debug("unAuthorized contains player after auth: " + unAuthorized.containsKey(player));
        }
        
        // Check and show announcement after successful authorization
        checkAndShowAnnouncement(user, player);
    }

    /**
     * Closes any active FancyDialog for the player.
     *
     * @param player the player
     */
    private void closeFancyDialog(P player) {
        if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin) {
            var dialogManager = paperPlugin.getDialogManager();
            if (dialogManager != null && dialogManager.isAvailable()) {
                try {
                    if (player instanceof org.bukkit.entity.Player bukkitPlayer) {
                        dialogManager.closeAllDialogs(bukkitPlayer);
                    }
                } catch (Exception e) {
                    plugin.getLogger().debug("Failed to close FancyDialog: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean confirmTwoFactorAuth(P player, Integer code, User user) {
        var secret = awaiting2FA.get(player);
        
        if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== 2FA Verification Debug ===");
            plugin.getLogger().debug("Player: " + platformHandle.getUsernameForPlayer(player));
            plugin.getLogger().debug("Input code: " + code);
            plugin.getLogger().debug("Secret from awaiting2FA: " + secret);
            plugin.getLogger().debug("awaiting2FA map size: " + awaiting2FA.size());
            plugin.getLogger().debug("Secret is null: " + (secret == null));
        }
        
        if (secret == null) {
            if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("❌ 2FA verification failed: secret is null");
            }
            return false;
        }
        
        boolean verificationResult = plugin.getTOTPProvider().verify(code, secret);
        
        if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("TOTP verification result: " + verificationResult);
        }
        
        if (verificationResult) {
            user.setSecret(secret);
            plugin.getDatabaseProvider().updateUser(user);
            
            // 清理awaiting2FA状态
            awaiting2FA.remove(player);
            
            if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("✅ 2FA setup successful, secret saved to user and awaiting2FA cleared");
            }
            return true;
        }
        
        if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("❌ 2FA verification failed: wrong code");
        }
        return false;
    }

    public void startTracking(User user, P player) {
        var audience = platformHandle.getAudienceForPlayer(player);

        unAuthorized.put(player, user.isRegistered());

        // Check if we should use FancyDialogs (Paper only)
        boolean useFancyDialogs = tryShowFancyDialog(player, user);

        if (!useFancyDialogs) {
            // Use traditional message/title prompts
            plugin.cancelOnExit(plugin.delay(() -> {
                if (!unAuthorized.containsKey(player)) return;
                sendInfoMessage(user.isRegistered(), audience);
            }, 250), player);

            sendInfoMessage(user.isRegistered(), audience);
        }

        var limit = plugin.getConfiguration().get(ConfigurationKeys.SECONDS_TO_AUTHORIZE);

        if (limit > 0) {
            plugin.cancelOnExit(plugin.delay(() -> {
                if (!unAuthorized.containsKey(player)) return;
                platformHandle.kick(player, plugin.getMessages().getMessage("kick-time-limit"));
            }, limit * 1000L), player);
        }
    }

    /**
     * Attempts to show a FancyDialog for authentication.
     * Only works on Paper servers with FancyDialogs installed.
     *
     * @param player the player
     * @param user   the user data
     * @return true if dialog was shown, false otherwise
     */
    private boolean tryShowFancyDialog(P player, User user) {
        // Check if this is a Paper server with DialogManager
        if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin) {
            var dialogManager = paperPlugin.getDialogManager();
            if (dialogManager != null && dialogManager.isAvailable()) {
                try {
                    // Cast player to Bukkit player
                    if (player instanceof org.bukkit.entity.Player bukkitPlayer) {
                        // Mark this player as using FancyDialogs
                        usingFancyDialogs.add(player);
                        
                        plugin.delay(() -> {
                            // 在显示对话框前再次检查玩家是否已经被授权
                            // 防止授权成功后仍然显示登录对话框
                            if (isAuthorized(player)) {
                                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                                    plugin.getLogger().debug("Player " + bukkitPlayer.getName() + " already authorized, skipping dialog display");
                                }
                                usingFancyDialogs.remove(player);
                                return;
                            }
                            
                            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                                plugin.getLogger().debug("Showing dialog for unauthorized player: " + bukkitPlayer.getName() + " (registered: " + user.isRegistered() + ")");
                            }
                            
                            if (user.isRegistered()) {
                                dialogManager.showLoginDialog(bukkitPlayer, user);
                            } else {
                                dialogManager.showRegisterDialog(bukkitPlayer, user);
                            }
                        }, 250);
                        return true;
                    }
                } catch (Exception e) {
                    plugin.getLogger().debug("Failed to show FancyDialog: " + e.getMessage());
                    usingFancyDialogs.remove(player);
                }
            }
        }
        return false;
    }

    private void broadcastActionbars() {
        var wrong = new HashSet<P>();
        unAuthorized.forEach((player, registered) -> {
            // Skip players using FancyDialogs
            if (usingFancyDialogs.contains(player)) {
                return;
            }
            
            var audience = platformHandle.getAudienceForPlayer(player);

            if (audience == null) {
                wrong.add(player);
                return;
            }

            sendActionBar(registered, audience);

        });

        wrong.forEach(unAuthorized::remove);
    }

    private void sendActionBar(boolean registered, Audience audience) {
        if (plugin.getConfiguration().get(ConfigurationKeys.USE_ACTION_BAR)) {
            audience.sendActionBar(plugin.getMessages().getMessage(registered ? "action-bar-login" : "action-bar-register"));
        }
    }

    private void sendInfoMessage(boolean registered, Audience audience) {
        audience.sendMessage(plugin.getMessages().getMessage(registered ? "prompt-login" : "prompt-register"));
        if (!plugin.getConfiguration().get(ConfigurationKeys.USE_TITLES)) return;
        Integer toRefresh = plugin.getConfiguration().get(ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION);
        //noinspection UnstableApiUsage
        audience.showTitle(Title.title(
                plugin.getMessages().getMessage(registered ? "title-login" : "title-register"),
                plugin.getMessages().getMessage(registered ? "sub-title-login" : "sub-title-register"),
                Title.Times.of(
                        Duration.ofMillis(0),
                        Duration.ofMillis(toRefresh > 0 ?
                                (long) (toRefresh * 1.1) :
                                10000
                        ),
                        Duration.ofMillis(0)
                )
        ));
    }

    public void stopTracking(P player) {
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Stopping tracking for player: " + player);
        }
        unAuthorized.remove(player);
        awaiting2FA.remove(player);
        usingFancyDialogs.remove(player);
    }

    public void notifyUnauthorized() {
        var wrong = new HashSet<P>();
        unAuthorized.forEach((player, registered) -> {
            // Skip players using FancyDialogs
            if (usingFancyDialogs.contains(player)) {
                return;
            }
            
            var audience = platformHandle.getAudienceForPlayer(player);

            if (audience == null) {
                wrong.add(player);
                return;
            }

            sendInfoMessage(registered, audience);

        });

        wrong.forEach(unAuthorized::remove);
    }

    /**
     * Checks if the email verification data represents an email registration verification
     * (as opposed to a simple email change verification).
     * 
     * @param data the email verification data to check
     * @return true if this is email registration data
     */
    private boolean isEmailRegistrationData(EmailVerifyData data) {
        if (data == null || data.token() == null) {
            return false;
        }
        
        // Email registration verification tokens have format: "code:password:algo:hash"
        // Regular email verification tokens are just simple strings
        String[] parts = data.token().split(":", 4);
        return parts.length == 4;
    }
    
    /**
     * Checks if a player currently has an ongoing email registration verification.
     * This is useful for reconnection handling.
     * 
     * @param playerUUID the UUID of the player
     * @return true if player has ongoing email registration verification
     */
    public boolean hasEmailRegistrationVerification(UUID playerUUID) {
        var emailData = emailConfirmCache.getIfPresent(playerUUID);
        return emailData != null && isEmailRegistrationData(emailData);
    }
    
    /**
     * Gets the email address from an ongoing email registration verification.
     * 
     * @param playerUUID the UUID of the player
     * @return the email address if verification is ongoing, null otherwise
     */
    public String getEmailRegistrationEmail(UUID playerUUID) {
        var emailData = emailConfirmCache.getIfPresent(playerUUID);
        if (emailData != null && isEmailRegistrationData(emailData)) {
            return emailData.email();
        }
        return null;
    }

    public record EmailVerifyData(String email, String token, UUID uuid) {
    }

    public void beginTwoFactorAuth(User user, P player, TOTPData data) {
        awaiting2FA.put(player, data.secret());

        var limbo = plugin.getServerHandler().chooseLimboServer(user, player);

        if (limbo == null) {
            platformHandle.kick(player, plugin.getMessages().getMessage("kick-no-limbo"));
            return;
        }

        platformHandle.movePlayer(player, limbo).whenComplete((t, e) -> {
            if (t != null || e != null) awaiting2FA.remove(player);
        });
    }

    @Override
    public void beginRegistrationTwoFactorAuth(User user, P player, xyz.kyngs.librelogin.api.totp.TOTPData data) {
        // For registration flow, we only need to store the secret without moving to limbo
        awaiting2FA.put(player, data.secret());
        
        if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Registration 2FA Setup ===");
            plugin.getLogger().debug("Player: " + platformHandle.getUsernameForPlayer(player));
            plugin.getLogger().debug("Secret stored in awaiting2FA: " + data.secret());
            plugin.getLogger().debug("awaiting2FA map size: " + awaiting2FA.size());
        }
    }
}
