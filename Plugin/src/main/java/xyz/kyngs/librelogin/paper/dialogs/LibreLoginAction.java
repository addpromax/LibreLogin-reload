/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.dialogs;

import com.fancyinnovations.fancydialogs.api.Dialog;
import com.fancyinnovations.fancydialogs.api.DialogAction;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.common.util.EmailValidationUtil;
import xyz.kyngs.librelogin.common.util.EmailRegistrationRateLimiter;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Custom DialogAction for LibreLogin authentication operations.
 * Handles login, register, and password reset actions from FancyDialogs.
 *
 * @author LibreLogin Contributors
 */
public class LibreLoginAction implements DialogAction {

    private final PaperLibreLogin plugin;
    private final DialogManager manager;
    private final EmailValidationUtil emailValidator;
    private final EmailRegistrationRateLimiter emailRateLimiter;

    public LibreLoginAction(PaperLibreLogin plugin, DialogManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.emailValidator = new EmailValidationUtil(plugin);
        this.emailRateLimiter = new EmailRegistrationRateLimiter(plugin);
    }

    @Override
    public void execute(Player player, Dialog dialog, String data) {
        String dialogId = dialog.getId();
        
        try {
            // Enhanced debug logging to understand FancyDialogs behavior
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("=== FancyDialogs Action Debug ===");
                plugin.getLogger().debug("Player: " + player.getName());
                plugin.getLogger().debug("Dialog ID: " + dialogId);
                plugin.getLogger().debug("Data: " + data);
                plugin.getLogger().debug("Dialog toString: " + dialog.toString());
                plugin.getLogger().debug("=====================================");
            }
            
            // First check for button-only actions (these have priority over form submissions)
            if ("librelogin_forgot_password".equals(data)) {
                handleForgotPassword(player);
                return;
            } else if ("librelogin_continue_password_reset".equals(data)) {
                handleContinuePasswordReset(player);
                return;
            } else if ("librelogin_back_to_login".equals(data)) {
                handleBackToLogin(player);
                return;
            } else if ("librelogin_disconnect".equals(data)) {
                handleDisconnect(player);
                return;
            } else if ("librelogin_announcement_confirm".equals(data)) {
                handleAnnouncementConfirm(player);
                return;
            } else if ("librelogin_2fa_skip".equals(data)) {
                handleTwoFactorSkip(player);
                return;
            } else if (data != null && data.startsWith("librelogin_2fa_rescan:")) {
                // Handle rescan with secret: "librelogin_2fa_rescan:SECRET"
                String secret = data.substring("librelogin_2fa_rescan:".length());
                handleTwoFactorRescan(player, secret);
                return;
            } else if ("librelogin_email_register".equals(data)) {
                handleEmailRegisterButton(player);
                return;
            } else if ("librelogin_back_to_register".equals(data)) {
                handleBackToRegister(player);
                return;
            } else if ("librelogin_resend_registration_email".equals(data)) {
                handleResendRegistrationEmail(player);
                return;
            }
            
            // Handle form submissions based on dialog ID
            switch (dialogId) {
                case "librelogin_login" -> handleLogin(player, data);
                case "librelogin_register" -> handleRegister(player, data);
                case "librelogin_password_reset" -> handlePasswordReset(player, data);
                case "librelogin_2fa_setup" -> handleTwoFactorSetup(player, data);
                case "librelogin_email_register" -> handleEmailRegisterSubmit(player, data);
                case "librelogin_email_verification" -> handleEmailVerify(player, data);
                case "librelogin_email_input" -> handleEmailInputSubmit(player, data);
                default -> {
                    // Unexpected dialog ID
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        plugin.getLogger().debug("Unknown dialog action - Dialog ID: " + dialogId + ", Data: " + data);
                    }
                    showError(player, plugin.getMessages().getRawMessage("error-unknown-command"));
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().error("Error executing dialog action: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            showError(player, plugin.getMessages().getRawMessage("error-unknown") + ": " + e.getMessage());
        }
    }

    /**
     * Utility method to parse input data by splitting on ':' delimiter.
     * Handles null/empty data gracefully and supports passwords with colons.
     */
    private String[] parseData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return new String[0];
        }
        return data.split(":", -1); // -1 to preserve empty trailing strings
    }
    
    /**
     * Safe method to parse input data with known field count to handle passwords with colons.
     * This method correctly handles passwords that may contain colon characters.
     * 
     * @param data the data string to parse
     * @param expectedFields the expected number of fields
     * @return parsed array with exact field count
     */
    private String[] parseDataSafe(String data, int expectedFields) {
        if (data == null || data.trim().isEmpty()) {
            return new String[expectedFields]; // Return array filled with nulls
        }
        
        // Split with limit to preserve colons in passwords
        String[] parts = data.split(":", expectedFields);
        
        // Ensure we have the expected number of fields
        if (parts.length < expectedFields) {
            String[] result = new String[expectedFields];
            System.arraycopy(parts, 0, result, 0, parts.length);
            return result;
        }
        
        return parts;
    }

    /**
     * Shows an error message to the player and closes dialogs.
     */
    private void showError(Player player, String message) {
        player.sendMessage(Component.text("§c" + message));
        manager.closeAllDialogs(player);
    }

    /**
     * Shows an error in dialog by reopening the current context dialog with error message.
     */
    private void showErrorInDialog(Player player, User user, String message, String type) {
        // Fallback to regular error if user is null
        if (user == null) {
            showError(player, message);
            return;
        }
        
        // Close current dialog and show register dialog with error
        manager.closeAllDialogs(player);
        plugin.delay(() -> {
            manager.showRegisterDialog(player, user, message, type);
        }, 100);
    }

    /**
     * Shows an error in password reset dialog by reopening the dialog with error message.
     */
    private void showErrorInPasswordResetDialog(Player player, User user, String message, String type) {
        // Fallback to regular error if user is null
        if (user == null) {
            showError(player, message);
            return;
        }
        
        // Close current dialog and show password reset dialog with error
        manager.closeAllDialogs(player);
        plugin.delay(() -> {
            manager.showPasswordResetDialog(player, user, message, type);
        }, 100);
    }

    /**
     * Shows an error in login dialog by reopening the dialog with error message.
     */
    private void showErrorInLoginDialog(Player player, User user, String message, String type) {
        // Fallback to regular error if user is null
        if (user == null) {
            showError(player, message);
            return;
        }
        
        // Close current dialog and show login dialog with error
        manager.closeAllDialogs(player);
        plugin.delay(() -> {
            manager.showLoginDialog(player, user, message, type);
        }, 100);
    }

    /**
     * Handles login action.
     * Data format: "password" or "password:totp_code"
     */
    private void handleLogin(Player player, String data) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (!user.isRegistered()) {
            showError(player, plugin.getMessages().getRawMessage("error-not-registered"));
            manager.closeAllDialogs(player);
            return;
        }

        // Check if data is empty or null
        if (data == null || data.trim().isEmpty()) {
			showErrorInLoginDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }
        
        // Parse data: password or password:totp_code
        // Use parseDataSafe with max 2 fields, but handle cases with only 1 field
        String[] parts = parseDataSafe(data, 2);
        String password = parts[0] != null ? parts[0] : "";
        String totpCode = parts[1] != null ? parts[1] : "";

        if (password.isEmpty()) {
			showErrorInLoginDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }

        // Verify password
        var hashedPassword = user.getHashedPassword();
        if (hashedPassword == null) {
            showError(player, plugin.getMessages().getRawMessage("error-password-invalid"));
            manager.closeAllDialogs(player);
            return;
        }

        var cryptoProvider = plugin.getCryptoProvider(hashedPassword.algo());
        if (cryptoProvider == null) {
            showError(player, plugin.getMessages().getRawMessage("error-password-invalid"));
            manager.closeAllDialogs(player);
            return;
        }

        if (!cryptoProvider.matches(password, hashedPassword)) {
            // 密码错误，重新显示登录对话框，提示统一错误信息
            showErrorInLoginDialog(player, user, plugin.getMessages().getRawMessage(MessageKeys.ERROR_LOGIN_FAILED.key()), "error");
            return;
        }

        // Check 2FA if enabled
        if (user.getSecret() != null && !totpCode.isEmpty()) {
            var totpProvider = plugin.getTOTPProvider();
            if (totpProvider != null) {
                try {
                        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        plugin.getLogger().debug("=== Login 2FA Verification ===");
                        plugin.getLogger().debug("Player: " + player.getName());
                        plugin.getLogger().debug("TOTP Code: " + totpCode);
                        plugin.getLogger().debug("User Secret: " + user.getSecret());
                    }
                    
                    // 🔧 修复：正确的参数顺序和类型
                    // verify(Integer code, String secret)
                    Integer code = Integer.parseInt(totpCode);  // 解析用户输入的验证码
                    if (!totpProvider.verify(code, user.getSecret())) {  // 正确的参数顺序
                        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                            plugin.getLogger().debug("2FA verification failed for login");
                        }
                        // 2FA验证失败，重新显示登录对话框，提示统一错误信息
                        showErrorInLoginDialog(player, user, plugin.getMessages().getRawMessage(MessageKeys.ERROR_LOGIN_FAILED.key()), "error");
                        return;
                    }
                    
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        plugin.getLogger().debug("2FA verification successful for login");
                    }
                } catch (NumberFormatException e) {
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        plugin.getLogger().debug("Invalid 2FA code format for login: " + totpCode);
                    }
                    // 验证码格式错误，重新显示登录对话框，提示统一错误信息
                    showErrorInLoginDialog(player, user, plugin.getMessages().getRawMessage(MessageKeys.ERROR_LOGIN_FAILED.key()), "error");
                    return;
                } catch (Exception e) {
                    plugin.getLogger().error("2FA verification error for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        e.printStackTrace();
                    }
                    // 2FA验证异常，重新显示登录对话框，提示统一错误信息
                    showErrorInLoginDialog(player, user, plugin.getMessages().getRawMessage(MessageKeys.ERROR_LOGIN_FAILED.key()), "error");
                    return;
                }
            }
        } else if (user.getSecret() != null && totpCode.isEmpty()) {
            // User has 2FA enabled but didn't provide TOTP code
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Player has 2FA enabled but didn't provide TOTP code");
            }
            // 未提供验证码，重新显示登录对话框，提示统一错误信息
            showErrorInLoginDialog(player, user, plugin.getMessages().getRawMessage(MessageKeys.ERROR_LOGIN_FAILED.key()), "error");
            return;
        }

        // Update last login timestamp and IP
            user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
            user.setIp(plugin.getPlatformHandle().getIP(player));
            plugin.getDatabaseProvider().updateUser(user);
            
        // Authorize player
        manager.closeAllDialogs(player);
        plugin.getAuthorizationProvider().authorize(user, player, AuthenticatedEvent.AuthenticationReason.LOGIN);
    }

    /**
     * Handles register action.
     * Data format: "NORMAL:password:password_confirm" or "EMAIL:password:password_confirm"
     * 
     * This method now routes to different handlers based on button type.
     */
    private void handleRegister(Player player, String data) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Register Action Debug - Player: " + player.getName() + " ===");
            plugin.getLogger().debug("Received data: " + data);
        }
        
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (user.isRegistered()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-already-registered"), "error");
            return;
        }

        // Check if data is empty or null
        if (data == null || data.trim().isEmpty()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }
        
        // Parse button type from data format: "TYPE:password:password_confirm"
        String[] parts = parseDataSafe(data, 3);
        
        // Check for new format with button type prefix
        if (parts.length >= 3 && parts[0] != null && ("NORMAL".equals(parts[0]) || "EMAIL".equals(parts[0]))) {
            String buttonType = parts[0];
            String passwordData = parts[1] + ":" + parts[2]; // password:password_confirm
                        
                        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Button type: " + buttonType + ", Password data: " + passwordData);
            }
            
            // Route to appropriate handler based on button type
            switch (buttonType) {
                case "NORMAL" -> handleNormalRegisterButton(player, passwordData);
                case "EMAIL" -> handleEmailRegisterButton(player, passwordData);
            }
            } else {
            // Fallback for old format or unknown type - treat as normal register
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Old format or unknown type, treating as normal register");
            }
            handleNormalRegisterButton(player, data);
        }
    }
    
    /**
     * Handles normal register button click - shows confirmation dialog.
     * Data format: "password:password_confirm"
     */
    private void handleNormalRegisterButton(Player player, String data) {
        handleRegisterConfirmation(player, data);
    }
    
    /**
     * Handles email register button click - goes directly to email input.
     * Data format: "password:password_confirm" 
     */
    private void handleEmailRegisterButton(Player player, String data) {
        handleEmailRegisterWithPassword(player, data);
    }

    /**
     * Handles register confirmation - shows confirmation dialog before proceeding with registration.
     * Data format: "password:password_confirm"
     */
    private void handleRegisterConfirmation(Player player, String data) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (user.isRegistered()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-already-registered"), "error");
            return;
        }

        // Check if data is empty or null
        if (data == null || data.trim().isEmpty()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }
        
        // Parse data: password:password_confirm
        String[] parts = parseDataSafe(data, 2);
        if (parts[0] == null || parts[1] == null) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-register-missing-fields"), "error");
            return;
        }
        
        String password = parts[0].trim();
        String passwordConfirm = parts[1].trim();

        if (password.isEmpty() || passwordConfirm.isEmpty()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }

        // Check if passwords match
        if (!password.equals(passwordConfirm)) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-password-not-match"), "error");
            return;
        }

        // Validate password length
        Integer minLength = plugin.getConfiguration().get(ConfigurationKeys.MINIMUM_PASSWORD_LENGTH);
        if (minLength > 0 && password.length() < minLength) {
            String errorMsg = plugin.getMessages().getRawMessage("error-password-too-short")
                    .replace("%length%", String.valueOf(minLength));
            showErrorInDialog(player, user, errorMsg, "error");
            return;
        }

        // Check forbidden passwords (weak passwords)
        if (!plugin.validPassword(password)) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-forbidden-password"), "error");
            return;
        }

        // Show confirmation dialog with delay to prevent race condition
        // 🔧 修复：延迟显示确认对话框，防止FancyDialogs认为对话框未打开的警告
        plugin.delay(() -> {
            manager.showRegisterConfirmationDialog(player, user, password, passwordConfirm);
        }, 100);
    }

    /**
     * Handles email registration with password already provided from register dialog.
     * Data format: "password:password_confirm"
     */
    private void handleEmailRegisterWithPassword(Player player, String data) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (user.isRegistered()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-already-registered"), "error");
            return;
        }

        // Check if email registration is enabled
        boolean emailRegisterEnabled = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_ENABLED);
        boolean mailEnabled = plugin.getConfiguration().get(ConfigurationKeys.MAIL_ENABLED);
        
        if (!emailRegisterEnabled || !mailEnabled) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-email-register-disabled"), "error");
            return;
        }

        // Check if data is empty or null
        if (data == null || data.trim().isEmpty()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }
        
        // Parse data: password:password_confirm
        String[] parts = parseDataSafe(data, 2);
        if (parts[0] == null || parts[1] == null) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-register-missing-fields"), "error");
            return;
        }
        
        String password = parts[0].trim();
        String passwordConfirm = parts[1].trim();

        if (password.isEmpty() || passwordConfirm.isEmpty()) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }

        // Check if passwords match
        if (!password.equals(passwordConfirm)) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-password-not-match"), "error");
            return;
        }

        // Validate password length
        Integer minLength = plugin.getConfiguration().get(ConfigurationKeys.MINIMUM_PASSWORD_LENGTH);
        if (minLength > 0 && password.length() < minLength) {
            String errorMsg = plugin.getMessages().getRawMessage("error-password-too-short")
                    .replace("%length%", String.valueOf(minLength));
            showErrorInDialog(player, user, errorMsg, "error");
            return;
        }

        // Check forbidden passwords (weak passwords)
        if (!plugin.validPassword(password)) {
            showErrorInDialog(player, user, plugin.getMessages().getRawMessage("error-forbidden-password"), "error");
            return;
        }

        // Store password temporarily and show email input dialog with delay
        // 🔧 修复：延迟显示邮箱输入对话框，防止FancyDialogs认为对话框未打开的警告
        plugin.delay(() -> {
            manager.showEmailInputDialog(player, user, password, passwordConfirm);
        }, 100);
    }

    /**
     * Handles password reset action.
     * Data format: "reset_token:new_password:new_password_confirm"
     */
    private void handlePasswordReset(Player player, String data) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (!user.isRegistered()) {
            showError(player, plugin.getMessages().getRawMessage("error-not-registered"));
            manager.closeAllDialogs(player);
            return;
        }
        
        // Parse data
        String[] parts = parseDataSafe(data, 3);
        if (parts[0] == null || parts[1] == null || parts[2] == null) {
            showErrorInPasswordResetDialog(player, user, plugin.getMessages().getRawMessage("error-password-reset-missing-fields"), "error");
            return;
        }
        
        String token = parts[0];
        String newPassword = parts[1];
        String newPasswordConfirm = parts[2];

        // Validate input
        if (token.isEmpty() || newPassword.isEmpty() || newPasswordConfirm.isEmpty()) {
            showErrorInPasswordResetDialog(player, user, plugin.getMessages().getRawMessage("error-empty-input"), "error");
            return;
        }

        if (!newPassword.equals(newPasswordConfirm)) {
            showErrorInPasswordResetDialog(player, user, plugin.getMessages().getRawMessage("error-password-not-match"), "error");
            return;
        }

        // Validate password requirements
        Integer minLength = plugin.getConfiguration().get(ConfigurationKeys.MINIMUM_PASSWORD_LENGTH);
        if (minLength > 0 && newPassword.length() < minLength) {
            String errorMsg = plugin.getMessages().getRawMessage("error-password-too-short")
                    .replace("%length%", String.valueOf(minLength));
            showErrorInPasswordResetDialog(player, user, errorMsg, "error");
            return;
        }

        if (!plugin.validPassword(newPassword)) {
            showErrorInPasswordResetDialog(player, user, plugin.getMessages().getRawMessage("error-forbidden-password"), "error");
            return;
        }

        // Verify password reset token
        String cachedToken = plugin.getAuthorizationProvider().getPasswordResetCache().getIfPresent(player.getUniqueId());
        if (cachedToken == null || !cachedToken.equals(token)) {
            showError(player, plugin.getMessages().getRawMessage("error-wrong-password-reset"));
            manager.closeAllDialogs(player);
            return;
        }

        // Hash the new password
        var hashedPassword = plugin.getDefaultCryptoProvider().createHash(newPassword);
        if (hashedPassword == null) {
            showErrorInPasswordResetDialog(player, user, plugin.getMessages().getRawMessage("error-password-too-long"), "error");
            return;
        }

        // Update user password
        user.setHashedPassword(hashedPassword);
        user.setLastAuthentication(java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
        user.setIp(plugin.getPlatformHandle().getIP(player));
        plugin.getDatabaseProvider().updateUser(user);

        // Clear the reset token from cache
        plugin.getAuthorizationProvider().getPasswordResetCache().invalidate(player.getUniqueId());

        // Show success message and authorize user
        player.sendMessage(net.kyori.adventure.text.Component.text("§a" + plugin.getMessages().getRawMessage("info-password-reset")));
        manager.closeAllDialogs(player);
        plugin.getAuthorizationProvider().authorize(user, player, xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent.AuthenticationReason.LOGIN);

        if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Password reset completed successfully for player: " + player.getName());
        }
    }

    /**
     * Handles forgot password button action.
     * Shows email status dialog first.
     */
    private void handleForgotPassword(Player player) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (!user.isRegistered()) {
            showError(player, plugin.getMessages().getRawMessage("error-not-registered"));
            manager.closeAllDialogs(player);
            return;
        }

        // Show email status dialog with delay to prevent race condition
        // 🔧 修复：延迟显示邮箱状态对话框，防止FancyDialogs认为对话框未打开的警告
        plugin.delay(() -> {
            manager.showEmailStatusDialog(player, user);
        }, 100);
    }

    /**
     * Handles continue password reset action from email status dialog.
     */
    private void handleContinuePasswordReset(Player player) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (!user.isRegistered()) {
            showError(player, plugin.getMessages().getRawMessage("error-not-registered"));
            manager.closeAllDialogs(player);
            return;
        }

        // Check if user has email address
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            showError(player, plugin.getMessages().getRawMessage("error-no-email"));
            manager.closeAllDialogs(player);
            return;
        }

        // Check if mail is enabled
        if (!plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.MAIL_ENABLED)) {
            showError(player, plugin.getMessages().getRawMessage("error-email-register-disabled"));
            manager.closeAllDialogs(player);
            return;
        }

        // Check rate limiting
        String playerIP = plugin.getPlatformHandle().getIP(player);
        var rateLimitResult = emailRateLimiter.checkRateLimit(player.getUniqueId(), playerIP, user.getEmail());
        if (rateLimitResult.isLimited()) {
            String errorMessage = plugin.getMessages().getRawMessage(rateLimitResult.getLimitType().getMessageKey())
                    .replace("%minutes%", String.valueOf(rateLimitResult.getRemainingMinutes()));
            showError(player, errorMessage);
            manager.closeAllDialogs(player);
            return;
        }

        try {
            // Generate password reset token
            String token = xyz.kyngs.librelogin.common.util.GeneralUtil.generateAlphanumericText(16);
            
            if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("=== Password Reset Request ===");
                plugin.getLogger().debug("Player: " + player.getName());
                plugin.getLogger().debug("Email: " + user.getEmail());
                plugin.getLogger().debug("Generated token: " + token);
            }

            // Send password reset email
            plugin.getEmailHandler().sendPasswordResetMail(
                user.getEmail(), 
                token, 
                user.getLastNickname() != null ? user.getLastNickname() : player.getName(),
                plugin.getPlatformHandle().getIP(player)
            );

            // Store token in cache for verification
            plugin.getAuthorizationProvider().getPasswordResetCache().put(player.getUniqueId(), token);

        // Show password reset dialog with delay to prevent race condition
            // 🔧 修复：延迟显示密码重置对话框，防止FancyDialogs认为对话框未打开的警告
            plugin.delay(() -> {
                manager.showPasswordResetDialog(player, user, 
                    plugin.getMessages().getRawMessage("info-reset-password-mail-sent"), null);
            }, 100);

            if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Password reset email sent successfully to " + user.getEmail());
            }

        } catch (Exception e) {
            plugin.getLogger().error("Failed to send password reset email: " + e.getMessage());
            if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            showError(player, plugin.getMessages().getRawMessage("error-mail-not-sent"));
        }
    }

    /**
     * Handles back to login button action.
     */
    private void handleBackToLogin(Player player) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            manager.closeAllDialogs(player);
            return;
        }

        // Show login dialog with delay to prevent race condition
        // 🔧 修复：延迟显示登录对话框，防止FancyDialogs认为对话框未打开的警告
        plugin.delay(() -> {
            manager.showLoginDialog(player, user);
        }, 100);
    }

    /**
     * Handles disconnect button action.
     */
    private void handleDisconnect(Player player) {
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Player " + player.getName() + " clicked disconnect button");
        }
        
        manager.closeAllDialogs(player);
        
        // Send disconnect message and kick player immediately
        String disconnectMessage = plugin.getMessages().getRawMessage("info-disconnect");
        if (disconnectMessage == null || disconnectMessage.isEmpty()) {
            disconnectMessage = "§c已断开连接"; // Default disconnect message
        }
        
        final String finalDisconnectMessage = disconnectMessage;
        // Use scheduler adapter to ensure player kick happens on appropriate thread
        plugin.getScheduler().runEntityTask(plugin.getBootstrap(), player, () -> {
                if (player.isOnline()) {
                player.kick(Component.text(finalDisconnectMessage));
                }
        });
    }

    /**
     * Handles two-factor authentication setup.
     * Data format: "totp_code"
     */
    private void handleTwoFactorSetup(Player player, String data) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (data == null || data.trim().isEmpty()) {
            showError(player, plugin.getMessages().getRawMessage("error-empty-input"));
            manager.closeAllDialogs(player);
            return;
        }
        
        // 解析数据：可能是 "验证码" 或 "secret:验证码" 格式
        String totpCode;
        if (data.contains(":")) {
            // 如果包含冒号，取冒号后面的部分（验证码）
            String[] parts = data.split(":", 2);
            totpCode = parts.length > 1 ? parts[1].trim() : parts[0].trim();
        } else {
            // 直接是验证码
            totpCode = data.trim();
        }
        
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== 2FA Setup Verification ===");
                plugin.getLogger().debug("Player: " + player.getName());
            plugin.getLogger().debug("Raw Data: " + data);
            plugin.getLogger().debug("Extracted TOTP Code: " + totpCode);
        }
        
        // 获取待验证的2FA数据
        var totpProvider = plugin.getTOTPProvider();
        if (totpProvider == null) {
            showError(player, plugin.getMessages().getRawMessage("error-2fa-not-available"));
            manager.closeAllDialogs(player);
            return;
        }
            
        // 检查是否有待处理的2FA设置
        var authProvider = plugin.getAuthorizationProvider();
        if (!authProvider.isAwaiting2FA(player)) {
            showError(player, plugin.getMessages().getRawMessage("error-no-pending-2fa"));
            manager.closeAllDialogs(player);
                return;
            }
            
        try {
            // 验证码应该是6位数字
            if (!totpCode.matches("\\d{6}")) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("Invalid TOTP code format: " + totpCode);
                }
                var totpData = totpProvider.generate(user);
                // 🔧 修复：延迟显示2FA设置对话框，防止FancyDialogs认为对话框未打开的警告
                plugin.delay(() -> {
                    manager.showTwoFactorSetupDialog(player, user, totpData, 
                        plugin.getMessages().getRawMessage("error-2fa-verify-failed"), "error");
                }, 100);
            return;
        }

            // 使用现有的confirmTwoFactorAuth方法来验证2FA
            Integer code = Integer.parseInt(totpCode);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("=== Calling confirmTwoFactorAuth ===");
                plugin.getLogger().debug("Player: " + player.getName());
                plugin.getLogger().debug("Parsed code: " + code);
                plugin.getLogger().debug("User UUID: " + user.getUuid());
                plugin.getLogger().debug("isAwaiting2FA before call: " + authProvider.isAwaiting2FA(player));
            }
            
            boolean verificationResult = authProvider.confirmTwoFactorAuth(player, code, user);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("=== confirmTwoFactorAuth Result ===");
                plugin.getLogger().debug("Verification result: " + verificationResult);
                plugin.getLogger().debug("isAwaiting2FA after call: " + authProvider.isAwaiting2FA(player));
            }
            
            if (!verificationResult) {
                // 验证失败，重新显示2FA设置对话框
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("2FA verification failed for player: " + player.getName() + ", showing error dialog");
                }
                var totpData = totpProvider.generate(user);
                // 🔧 修复：延迟显示2FA设置对话框，防止FancyDialogs认为对话框未打开的警告
                plugin.delay(() -> {
                    manager.showTwoFactorSetupDialog(player, user, totpData, 
                        plugin.getMessages().getRawMessage("error-2fa-verify-failed"), "error");
                }, 100);
            return;
        }

            // 验证成功，清理临时数据并授权用户
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("2FA verification successful! Starting authorization for player: " + player.getName());
            }
            
            // 关闭对话框并授权用户（authorize方法内部会调用stopTracking）
        manager.closeAllDialogs(player);
            plugin.getAuthorizationProvider().authorize(user, player, AuthenticatedEvent.AuthenticationReason.REGISTER);
            
            // 发送成功消息
            String successMessage = plugin.getMessages().getRawMessage("success-2fa-setup");
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Sending success message: " + successMessage);
            }
            player.sendMessage(net.kyori.adventure.text.Component.text(successMessage));
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("2FA setup completed successfully for player: " + player.getName());
            }
            
        } catch (NumberFormatException e) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Invalid TOTP code number format for " + player.getName() + ": " + data);
            }
            // 重新显示2FA设置对话框，显示格式错误
            try {
                var totpData = totpProvider.generate(user);
                // 🔧 修复：延迟显示2FA设置对话框，防止FancyDialogs认为对话框未打开的警告
                plugin.delay(() -> {
                    manager.showTwoFactorSetupDialog(player, user, totpData, 
                        plugin.getMessages().getRawMessage("error-2fa-verify-failed"), "error");
                }, 100);
            } catch (Exception fallbackException) {
                showError(player, plugin.getMessages().getRawMessage("error-2fa-setup-failed"));
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to complete 2FA setup for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            showError(player, plugin.getMessages().getRawMessage("error-2fa-setup-failed"));
        }
    }

    /**
     * Handles two-factor authentication skip.
     */
    private void handleTwoFactorSkip(Player player) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }
        
        // Close dialogs and authorize (skip 2FA setup)
        manager.closeAllDialogs(player);
            plugin.getAuthorizationProvider().authorize(user, player, AuthenticatedEvent.AuthenticationReason.REGISTER);
    }

    /**
     * Handles two-factor authentication rescan with specific secret.
     */
    private void handleTwoFactorRescan(Player player, String secret) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== 2FA Rescan Request ===");
            plugin.getLogger().debug("Player: " + player.getName());
            plugin.getLogger().debug("Secret: " + secret);
        }

        try {
            // 获取TOTP提供者
            var totpProvider = plugin.getTOTPProvider();
            if (totpProvider == null) {
                showError(player, plugin.getMessages().getRawMessage(MessageKeys.ERROR_2FA_NOT_AVAILABLE.key()));
                return;
            }

            // 使用传入的secret重新生成TOTP数据（包含二维码）
            var totpData = totpProvider.generate(user, secret);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Regenerated TOTP data for rescan");
            }

            // 关闭当前对话框
            manager.closeAllDialogs(player);

            // 重新注册pending setup，等待地图丢弃后显示输入对话框
            manager.registerPendingTwoFactorSetup(player, user, totpData);

            // 重新显示2FA地图
            showTwoFactorMap(player, totpData);

            // 发送重新扫码的提示消息
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "§e已重新生成二维码！请重新使用Google Authenticator扫描地图上的二维码，然后按 §eQ键 §a丢弃地图继续设置！"));

            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("✅ Successfully initiated 2FA rescan for player: " + player.getName());
            }

        } catch (Exception e) {
            plugin.getLogger().error("Failed to handle 2FA rescan for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            
            // 如果重新扫码失败，重新显示2FA设置对话框
            try {
                var totpProvider = plugin.getTOTPProvider();
                if (totpProvider != null) {
                    var totpData = totpProvider.generate(user, secret);
                    // 🔧 修复：延迟显示2FA设置对话框，防止FancyDialogs认为对话框未打开的警告
                    plugin.delay(() -> {
                        manager.showTwoFactorSetupDialog(player, user, totpData, 
                            plugin.getMessages().getRawMessage(MessageKeys.ERROR_2FA_RESCAN_FAILED.key()), "error");
                    }, 100);
                }
            } catch (Exception fallbackException) {
                plugin.getLogger().error("Fallback 2FA dialog also failed for " + player.getName() + ": " + fallbackException.getMessage());
                showError(player, plugin.getMessages().getRawMessage(MessageKeys.ERROR_2FA_SETUP_FAILED.key()));
            }
        }
    }

    /**
     * Handles announcement confirm action.
     */
    private void handleAnnouncementConfirm(Player player) {
        try {
            // Mark announcement as seen for this player
            // This is typically handled by storing in cache or database
            plugin.getLogger().info("Player " + player.getName() + " confirmed announcement " + 
                (plugin.getAnnouncementManager() != null ? plugin.getAnnouncementManager().getAnnouncementTitle() : ""));
            
            // Close current dialog
            manager.closeAllDialogs(player);

            // Check if player is already authorized - if so, don't show auth dialogs
            if (plugin.getAuthorizationProvider().isAuthorized(player)) {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    plugin.getLogger().debug("Player " + player.getName() + " is already authorized after announcement confirm, not showing auth dialogs");
                }
                
                // Update the user's last seen announcement hash to mark as seen
                var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
                if (user != null && plugin.getAnnouncementManager() != null) {
                    String currentHash = plugin.getAnnouncementManager().getCurrentHash();
                    if (currentHash != null) {
                        user.setLastSeenAnnouncementHash(currentHash);
                        plugin.getDatabaseProvider().updateUser(user);

                        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                            plugin.getLogger().debug("Updated announcement hash for authorized player: " + player.getName() + " to " + currentHash);
                        }
                    }
                }
                
                // Player is already authorized, no need to show auth dialogs
                return;
            }

            // Show appropriate next dialog based on player state (only for non-authorized players)
            // 🔧 修复：延迟显示下一个对话框，防止FancyDialogs认为对话框未打开的警告
            var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
            if (user != null) {
                plugin.delay(() -> {
                    if (!user.isRegistered()) {
                        manager.showRegisterDialog(player, user);
                    } else {
                        manager.showLoginDialog(player, user);
                    }
                }, 100);
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error handling announcement confirm: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            showError(player, plugin.getMessages().getRawMessage("error-unknown") + ": " + e.getMessage());
        }
    }

    /**
     * Handles the email register button action.
     * Shows the email registration dialog.
     */
    private void handleEmailRegisterButton(Player player) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        // Check if email registration is enabled
        boolean emailRegisterEnabled = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_ENABLED);
        boolean mailEnabled = plugin.getConfiguration().get(ConfigurationKeys.MAIL_ENABLED);
        
        if (!emailRegisterEnabled || !mailEnabled) {
            showError(player, plugin.getMessages().getRawMessage("error-email-register-disabled"));
            return;
        }

        // Show email register dialog with delay to prevent race condition
        // 🔧 修复：延迟显示邮箱注册对话框，防止FancyDialogs认为对话框未打开的警告
        plugin.delay(() -> {
            manager.showEmailRegisterDialog(player, user);
        }, 100);
    }

    /**
     * Handles email registration form submission.
     * Data format: "password:password_confirm:email"
     */
    private void handleEmailRegisterSubmit(Player player, String data) {
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Email Registration Debug - Player: " + player.getName() + " ===");
            plugin.getLogger().debug("Received data: " + data);
        }
        
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (user.isRegistered()) {
            showError(player, plugin.getMessages().getRawMessage("error-already-registered"));
            manager.closeAllDialogs(player);
            return;
        }

        // Check if email registration is enabled
        boolean emailRegisterEnabled = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_ENABLED);
        boolean mailEnabled = plugin.getConfiguration().get(ConfigurationKeys.MAIL_ENABLED);
        
        if (!emailRegisterEnabled || !mailEnabled) {
            showError(player, plugin.getMessages().getRawMessage("error-email-register-disabled"));
            manager.closeAllDialogs(player);
            return;
        }

        // Parse and validate data
        String[] parts = parseDataSafe(data, 3);
        if (parts[0] == null || parts[1] == null || parts[2] == null) {
            showError(player, plugin.getMessages().getRawMessage("error-email-missing-fields"));
            manager.closeAllDialogs(player);
            return;
        }
        
        String password = parts[0].trim();
        String passwordConfirm = parts[1].trim();
        String email = parts[2].trim();

        if (password.isEmpty() || passwordConfirm.isEmpty() || email.isEmpty()) {
            showError(player, plugin.getMessages().getRawMessage("error-empty-input"));
            manager.closeAllDialogs(player);
            return;
        }

        if (!password.equals(passwordConfirm)) {
            showError(player, plugin.getMessages().getRawMessage("error-password-not-match"));
            manager.closeAllDialogs(player);
            return;
        }

        // Validate password requirements
        Integer minLength = plugin.getConfiguration().get(ConfigurationKeys.MINIMUM_PASSWORD_LENGTH);
        if (minLength > 0 && password.length() < minLength) {
            String errorMsg = plugin.getMessages().getRawMessage("error-password-too-short")
                    .replace("%length%", String.valueOf(minLength));
            showError(player, errorMsg);
            manager.closeAllDialogs(player);
            return;
        }

        if (!plugin.validPassword(password)) {
            showError(player, plugin.getMessages().getRawMessage("error-forbidden-password"));
            manager.closeAllDialogs(player);
            return;
        }

        // Validate email
        var emailValidation = emailValidator.validateEmail(email);
        if (!emailValidation.isValid()) {
            String errorMessage = plugin.getMessages().getRawMessage(emailValidation.getErrorMessageKey());
            showError(player, errorMessage);
            manager.closeAllDialogs(player);
            return;
        }

        // Check rate limiting
        String playerIP = plugin.getPlatformHandle().getIP(player);
        var rateLimitResult = emailRateLimiter.checkRateLimit(player.getUniqueId(), playerIP, email);
        if (rateLimitResult.isLimited()) {
            String errorMessage = plugin.getMessages().getRawMessage(rateLimitResult.getLimitType().getMessageKey())
                    .replace("%minutes%", String.valueOf(rateLimitResult.getRemainingMinutes()));
            showError(player, errorMessage);
            manager.closeAllDialogs(player);
            return;
        }

        // Process email registration: send verification email and show verification dialog
        try {
            // Generate verification token
            String token = xyz.kyngs.librelogin.common.util.GeneralUtil.generateAlphanumericText(16);
            
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Generated verification token for direct email registration: " + token);
                plugin.getLogger().debug("Sending verification email to: " + email);
        }
        
            // Send verification email
            plugin.getEmailHandler().sendVerificationMail(email, token, user.getLastNickname() != null ? user.getLastNickname() : player.getName());
            
            // Store password data temporarily for completion after verification
            // Encode password in the token for email registration: "token:password:algo:hash"
        var hashedPassword = plugin.getDefaultCryptoProvider().createHash(password);
            String registrationToken = token + ":" + password + ":" + hashedPassword.algo() + ":" + hashedPassword.hash();
            var registrationData = new xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider.EmailVerifyData(email, registrationToken, player.getUniqueId());
            plugin.getAuthorizationProvider().getEmailConfirmCache().put(player.getUniqueId(), registrationData);
            
            // Show email verification dialog with delay to prevent race condition
            // 🔧 修复：延迟显示邮箱验证对话框，防止FancyDialogs认为对话框未打开的警告
            plugin.delay(() -> {
                manager.showEmailVerificationDialog(player, user, email, true, null, null);
            }, 100);
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to send verification email for direct registration: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            showError(player, plugin.getMessages().getRawMessage("error-mail-not-sent"));
        }
    }

    /**
     * Handles email input form submission from the simplified email dialog.
     * Data format: "password:password_confirm:email"
     */
    private void handleEmailInputSubmit(Player player, String data) {
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Email Input Submit Debug - Player: " + player.getName() + " ===");
            plugin.getLogger().debug("Received data: " + data);
        }
        
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (user.isRegistered()) {
            showError(player, plugin.getMessages().getRawMessage("error-already-registered"));
            manager.closeAllDialogs(player);
            return;
        }

        // Check if email registration is enabled
        boolean emailRegisterEnabled = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_ENABLED);
        boolean mailEnabled = plugin.getConfiguration().get(ConfigurationKeys.MAIL_ENABLED);
        
        if (!emailRegisterEnabled || !mailEnabled) {
            showError(player, plugin.getMessages().getRawMessage("error-email-register-disabled"));
            manager.closeAllDialogs(player);
                return;
            }
            
        // Parse and validate data
        String[] parts = parseData(data);
        if (parts.length < 3) {
            showError(player, plugin.getMessages().getRawMessage("error-email-missing-fields"));
            manager.closeAllDialogs(player);
                return;
            }
            
        String password = parts[0] != null ? parts[0].trim() : "";
        String passwordConfirm = parts[1] != null ? parts[1].trim() : "";
        String email = parts[2] != null ? parts[2].trim() : "";

        if (password.isEmpty() || passwordConfirm.isEmpty() || email.isEmpty()) {
            showError(player, plugin.getMessages().getRawMessage("error-empty-input"));
            manager.closeAllDialogs(player);
            return;
        }

        // Validate email
        var emailValidation = emailValidator.validateEmail(email);
        if (!emailValidation.isValid()) {
            String errorMessage = plugin.getMessages().getRawMessage(emailValidation.getErrorMessageKey());
            // Show error in email input dialog and return to it with delay
            // 🔧 修复：延迟显示邮箱输入对话框，防止FancyDialogs认为对话框未打开的警告
            plugin.delay(() -> {
                manager.showEmailInputDialog(player, user, password, passwordConfirm, errorMessage, "error");
            }, 100);
            return;
        }

        // Check rate limiting
        String playerIP = plugin.getPlatformHandle().getIP(player);
        var rateLimitResult = emailRateLimiter.checkRateLimit(player.getUniqueId(), playerIP, email);
        if (rateLimitResult.isLimited()) {
            String errorMessage = plugin.getMessages().getRawMessage(rateLimitResult.getLimitType().getMessageKey())
                    .replace("%minutes%", String.valueOf(rateLimitResult.getRemainingMinutes()));
            // 🔧 修复：延迟显示邮箱输入对话框，防止FancyDialogs认为对话框未打开的警告
            plugin.delay(() -> {
                manager.showEmailInputDialog(player, user, password, passwordConfirm, errorMessage, "error");
            }, 100);
            return;
        }

        // Process email registration: send verification email and show verification dialog
        try {
            // Generate verification token
            String token = xyz.kyngs.librelogin.common.util.GeneralUtil.generateAlphanumericText(16);
                
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Generated verification token: " + token);
                plugin.getLogger().debug("Sending verification email to: " + email);
            }
            
            // Send verification email
            plugin.getEmailHandler().sendVerificationMail(email, token, user.getLastNickname() != null ? user.getLastNickname() : player.getName());
            
            // Store verification data in cache
            var emailVerifyData = new xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider.EmailVerifyData(email, token, player.getUniqueId());
            plugin.getAuthorizationProvider().getEmailConfirmCache().put(player.getUniqueId(), emailVerifyData);
            
            // Store password data temporarily for completion after verification
            // We'll encode password in the token for email registration: "token:password:algo:hash"  
            var hashedPassword = plugin.getDefaultCryptoProvider().createHash(password);
            String registrationToken = token + ":" + password + ":" + hashedPassword.algo() + ":" + hashedPassword.hash();
            var registrationData = new xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider.EmailVerifyData(email, registrationToken, player.getUniqueId());
            plugin.getAuthorizationProvider().getEmailConfirmCache().put(player.getUniqueId(), registrationData);
            
            // Show email verification dialog with delay to prevent race condition
            // 🔧 修复：延迟显示邮箱验证对话框，防止FancyDialogs认为对话框未打开的警告
            plugin.delay(() -> {
                manager.showEmailVerificationDialog(player, user, email, true, null, null);
            }, 100);
            
                } catch (Exception e) {
            plugin.getLogger().error("Failed to send verification email: " + e.getMessage());
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        e.printStackTrace();
                    }
            // 🔧 修复：延迟显示邮箱输入对话框，防止FancyDialogs认为对话框未打开的警告
            plugin.delay(() -> {
                manager.showEmailInputDialog(player, user, password, passwordConfirm, 
                    plugin.getMessages().getRawMessage("error-mail-not-sent"), "error");
            }, 100);
        }
    }

    /**
     * Handles email verification form submission.
     * Data format: "verification_code"
     */
    private void handleEmailVerify(Player player, String data) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        if (data == null || data.trim().isEmpty()) {
            showError(player, plugin.getMessages().getRawMessage("error-empty-input"));
            manager.closeAllDialogs(player);
            return;
        }

        // Get verification data from cache
        var emailVerifyData = plugin.getAuthorizationProvider().getEmailConfirmCache().getIfPresent(player.getUniqueId());
        if (emailVerifyData == null) {
            showError(player, plugin.getMessages().getRawMessage("error-no-pending-verification"));
                return;
            }

        String verificationCode = data.trim();
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Email Verification Debug ===");
            plugin.getLogger().debug("Player: " + player.getName());
            plugin.getLogger().debug("Input code: " + verificationCode);
            plugin.getLogger().debug("Stored token: " + emailVerifyData.token());
            plugin.getLogger().debug("Email: " + emailVerifyData.email());
        }
        
        // Check if this is email registration verification (token format: "code:password:algo:hash")
        String[] tokenParts = emailVerifyData.token().split(":", 4);
        boolean isEmailRegistration = tokenParts.length == 4;
        
        if (isEmailRegistration) {
            // This is email registration verification
            String expectedCode = tokenParts[0];
            // tokenParts[1] is password, not needed for verification
            String algo = tokenParts[2]; 
            String hash = tokenParts[3];

            if (!expectedCode.equals(verificationCode)) {
                // Wrong verification code with delay to prevent race condition
                // 🔧 修复：延迟显示邮箱验证对话框，防止FancyDialogs认为对话框未打开的警告
                plugin.delay(() -> {
                    manager.showEmailVerificationDialog(player, user, emailVerifyData.email(), true,
                        plugin.getMessages().getRawMessage("error-verification-code-wrong"), "error");
                }, 100);
                return;
            }

            // Verification successful - complete registration
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Email verification successful, completing registration...");
            }
            
            try {
                // Set user email and password
                user.setEmail(emailVerifyData.email());
            var hashedPassword = new xyz.kyngs.librelogin.api.crypto.HashedPassword(hash, null, algo);
            user.setHashedPassword(hashedPassword);
            plugin.getDatabaseProvider().updateUser(user);

                // Clear verification cache
                plugin.getAuthorizationProvider().getEmailConfirmCache().invalidate(player.getUniqueId());

                // Send success message
                player.sendMessage(net.kyori.adventure.text.Component.text("§a" + plugin.getMessages().getRawMessage("info-registration-email-success")));

                // 检查是否需要强制设置2FA
                if (shouldForce2FA(player)) {
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        plugin.getLogger().debug("Player " + player.getName() + " has force-2fa permission after email registration, starting 2FA setup");
                    }
                    // 关闭当前对话框并启动2FA设置
            manager.closeAllDialogs(player);
                    start2FASetup(player, user);
                } else {
                    // 没有2FA权限或未开启强制2FA，直接授权
                    manager.closeAllDialogs(player);
                    plugin.getAuthorizationProvider().authorize(user, player, xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent.AuthenticationReason.REGISTER);
                }
                
            } catch (Exception e) {
                plugin.getLogger().error("Failed to complete email registration: " + e.getMessage());
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    e.printStackTrace();
                }
                showError(player, plugin.getMessages().getRawMessage("error-registration-failed"));
            }
            
        } else {
            // This is regular email verification (not registration)
            if (!emailVerifyData.token().equals(verificationCode)) {
                // Wrong verification code with delay to prevent race condition
                // 🔧 修复：延迟显示邮箱验证对话框，防止FancyDialogs认为对话框未打开的警告
                plugin.delay(() -> {
                    manager.showEmailVerificationDialog(player, user, emailVerifyData.email(), true,
                        plugin.getMessages().getRawMessage("error-verification-code-wrong"), "error");
                }, 100);
                return;
            }
            
            // Update user email
            user.setEmail(emailVerifyData.email());
            plugin.getDatabaseProvider().updateUser(user);
            
            // Clear verification cache
            plugin.getAuthorizationProvider().getEmailConfirmCache().invalidate(player.getUniqueId());
            
            showError(player, plugin.getMessages().getRawMessage("info-email-updated"));
        }
    }

    /**
     * Handles back to register button action.
     */
    private void handleBackToRegister(Player player) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            manager.closeAllDialogs(player);
            return;
        }

        // Show register dialog with delay to prevent race condition
        // 🔧 修复：延迟显示注册对话框，防止FancyDialogs认为对话框未打开的警告
        plugin.delay(() -> {
            manager.showRegisterDialog(player, user);
        }, 100);
    }

    /**
     * Handles resend registration email button action.
     */
    private void handleResendRegistrationEmail(Player player) {
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
            return;
        }

        // Resend registration email
        var emailVerifyData = plugin.getAuthorizationProvider().getEmailConfirmCache().getIfPresent(player.getUniqueId());
        if (emailVerifyData == null) {
            showError(player, plugin.getMessages().getRawMessage("error-no-pending-verification"));
            return;
        }
        
        // Check if this is email registration verification
        String[] tokenParts = emailVerifyData.token().split(":", 4);
        boolean isEmailRegistration = tokenParts.length == 4;
        
        if (!isEmailRegistration) {
            showError(player, plugin.getMessages().getRawMessage("error-no-pending-email-registration"));
            return;
        }

        try {
            // Generate new verification token
            String newToken = xyz.kyngs.librelogin.common.util.GeneralUtil.generateAlphanumericText(16);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Resending verification email with new token: " + newToken);
                plugin.getLogger().debug("Target email: " + emailVerifyData.email());
            }
                
                // Send new verification email
            plugin.getEmailHandler().sendVerificationMail(emailVerifyData.email(), newToken, user.getLastNickname() != null ? user.getLastNickname() : player.getName());
            
            // Update cached data with new token but keep password data
            String password = tokenParts[1];
            String algo = tokenParts[2];
            String hash = tokenParts[3];
            String newRegistrationToken = newToken + ":" + password + ":" + algo + ":" + hash;
            
                    var newRegistrationData = new xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider.EmailVerifyData(
                emailVerifyData.email(), newRegistrationToken, player.getUniqueId());
            plugin.getAuthorizationProvider().getEmailConfirmCache().put(player.getUniqueId(), newRegistrationData);
            
            // Show success message  
            player.sendMessage(net.kyori.adventure.text.Component.text("§a" + plugin.getMessages().getRawMessage("info-verification-mail-sent")));
            
            } catch (Exception e) {
            plugin.getLogger().error("Failed to resend verification email: " + e.getMessage());
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    e.printStackTrace();
                }
            showError(player, plugin.getMessages().getRawMessage("error-mail-not-sent"));
            }
    }

    /**
     * Handles register confirmation - user chose to continue without email.
     * Data format: "password:password_confirm"
     */
    public void handleRegisterConfirmContinue(Player player, String data) {
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Register Confirm Continue - Player: " + player.getName() + " ===");
            plugin.getLogger().debug("Received data: " + data);
        }
        
        var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
        if (user == null) {
            showError(player, plugin.getMessages().getRawMessage("error-user-data"));
            manager.closeAllDialogs(player);
                    return;
                }

        if (user.isRegistered()) {
            showError(player, plugin.getMessages().getRawMessage("error-already-registered"));
        manager.closeAllDialogs(player);
                    return;
                }

        // Parse data: password:password_confirm (already validated)
        String[] parts = parseDataSafe(data, 2);
        if (parts[0] == null || parts[1] == null) {
            showError(player, plugin.getMessages().getRawMessage("error-register-missing-fields"));
            manager.closeAllDialogs(player);
                    return;
                }

        String password = parts[0].trim();

        if (password.isEmpty()) {
            showError(player, plugin.getMessages().getRawMessage("error-empty-input"));
            manager.closeAllDialogs(player);
            return;
        }
        
        // Hash password
        var hashedPassword = plugin.getDefaultCryptoProvider().createHash(password);
        if (hashedPassword == null) {
            showError(player, plugin.getMessages().getRawMessage("error-password-too-long"));
        manager.closeAllDialogs(player);
            return;
        }
        
        // Update user
        user.setHashedPassword(hashedPassword);
        user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
        user.setIp(plugin.getPlatformHandle().getIP(player));
        plugin.getDatabaseProvider().updateUser(user);

        // 检查是否需要强制设置2FA
        if (shouldForce2FA(player)) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Player " + player.getName() + " has force-2fa permission, starting 2FA setup");
            }
            // 关闭当前对话框并启动2FA设置
                    manager.closeAllDialogs(player);
            start2FASetup(player, user);
                } else {
            // 没有2FA权限或未开启强制2FA，直接授权
            manager.closeAllDialogs(player);
            plugin.getAuthorizationProvider().authorize(user, player, AuthenticatedEvent.AuthenticationReason.REGISTER);
        }
    }

    /**
     * Handles go to email register - user chose to register with email from confirmation dialog.
     * Data format: "password:password_confirm"
     */
    public void handleGoEmailRegister(Player player, String data) {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("=== Go Email Register - Player: " + player.getName() + " ===");
            plugin.getLogger().debug("Received data: " + data);
        }
        
        // This is the same as handleEmailRegisterWithPassword
        handleEmailRegisterWithPassword(player, data);
    }
    
    /**
     * 检查玩家是否需要强制设置2FA
     * @param player 玩家
     * @return true 如果需要强制设置2FA
     */
    private boolean shouldForce2FA(Player player) {
        // 检查配置是否启用注册后强制2FA
        boolean forceOnRegister = plugin.getConfiguration().get(ConfigurationKeys.TOTP_FORCE_ON_REGISTER);
        
        // 检查玩家是否有相应权限
        boolean hasPermission = player.hasPermission("librelogin.force-2fa");
        
        return forceOnRegister && hasPermission;
    }
    
    /**
     * 为玩家启动2FA设置流程
     * @param player 玩家
     * @param user 用户数据
     */
    private void start2FASetup(Player player, xyz.kyngs.librelogin.api.database.User user) {
        try {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Starting 2FA setup for player: " + player.getName());
            }
            
            // 生成TOTP数据
            var totpProvider = plugin.getTOTPProvider();
            if (totpProvider == null) {
                plugin.getLogger().warn("TOTP Provider not available, skipping 2FA setup for " + player.getName());
                // 如果TOTP提供者不可用，则直接授权
                plugin.getAuthorizationProvider().authorize(user, player, AuthenticatedEvent.AuthenticationReason.REGISTER);
            return;
        }
        
            var totpData = totpProvider.generate(user);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Generated TOTP data for " + player.getName() + ", secret: " + totpData.secret());
            }
            
            // 开始注册流程的2FA验证 - 设置awaiting2FA状态但不移动到limbo服务器
            plugin.getAuthorizationProvider().beginRegistrationTwoFactorAuth(user, player, totpData);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("=== After beginRegistrationTwoFactorAuth ===");
                plugin.getLogger().debug("isAwaiting2FA: " + plugin.getAuthorizationProvider().isAwaiting2FA(player));
            }
            
            // 🔧 修复：正确的2FA流程应该是：
            // 1. 先显示地图（包含二维码）
            // 2. 注册pending setup，等待地图丢弃
            // 3. 地图丢弃后再显示输入验证码的对话框
            
            // 注册pending setup，等待地图丢弃后显示输入对话框
            manager.registerPendingTwoFactorSetup(player, user, totpData);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Registered pending 2FA setup for " + player.getName());
            }
            
            // 显示2FA地图（包含二维码）
            showTwoFactorMap(player, totpData);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Displayed 2FA map for " + player.getName());
            }
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to start 2FA setup for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            // 如果2FA设置失败，直接授权用户
            plugin.getAuthorizationProvider().authorize(user, player, AuthenticatedEvent.AuthenticationReason.REGISTER);
        }
    }
    
    /**
     * 显示2FA地图给玩家
     * @param player 玩家
     * @param totpData TOTP数据
     */
    private void showTwoFactorMap(Player player, xyz.kyngs.librelogin.api.totp.TOTPData totpData) {
        try {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("=== Showing 2FA Map to Player ===");
                plugin.getLogger().debug("Player: " + player.getName());
                plugin.getLogger().debug("TOTP Secret: " + totpData.secret());
            }
            
            // 获取虚拟地图投影器
            var mapProjector = plugin.getVirtualMapProjector();
            if (mapProjector == null) {
                plugin.getLogger().warn("VirtualMapProjector not available for " + player.getName());
                // 如果地图投影器不可用，直接显示对话框
                manager.showTwoFactorSetupDialog(player, 
                    plugin.getDatabaseProvider().getByUUID(player.getUniqueId()), totpData);
            return;
        }
        
            // 获取二维码图片
            var qrImage = totpData.qr();
            if (qrImage == null) {
                plugin.getLogger().warn("Failed to get QR code image for " + player.getName());
                // 如果二维码获取失败，直接显示对话框
                manager.showTwoFactorSetupDialog(player, 
                    plugin.getDatabaseProvider().getByUUID(player.getUniqueId()), totpData);
                return;
            }
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("QR code image obtained, size: " + qrImage.getWidth() + "x" + qrImage.getHeight());
            }
            
            // 显示虚拟地图
            mapProjector.project(qrImage, player);
            
            // 发送提示消息
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "§a请使用Google Authenticator扫描地图上的二维码，然后按 §eQ键 §a丢弃地图继续设置！"));
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("✅ Successfully displayed 2FA map for " + player.getName());
            }
            
        } catch (Exception e) {
            plugin.getLogger().error("Failed to show 2FA map for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            
            // 如果地图显示失败，直接显示对话框
            try {
                var user = plugin.getDatabaseProvider().getByUUID(player.getUniqueId());
                if (user != null) {
                    manager.showTwoFactorSetupDialog(player, user, totpData);
                }
            } catch (Exception fallbackException) {
                plugin.getLogger().error("Fallback 2FA dialog also failed for " + player.getName() + ": " + fallbackException.getMessage());
            }
        }
    }

    /**
     * Handles post-login actions after successful authentication.
     * This method is called after announcement confirmation or after login when no announcement is needed.
     *
     * @param player the player who has completed login
     */
    private void handlePostLoginActions(Player player) {
        // Post-login actions can be added here in the future if needed
    }
}