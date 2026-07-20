/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.common.util.EmailRegistrationRateLimiter;
import xyz.kyngs.librelogin.common.util.EmailValidationUtil;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Performs password authentication while a Paper connection is still in the
 * configuration phase. Native Paper dialogs are used here because FancyDialogs
 * actions require a fully joined Bukkit Player.
 */
public final class ConfigurationPhaseListener implements Listener {

    private static final Key LOGIN = Key.key("librelogin", "configuration-login");
    private static final Key REGISTER = Key.key("librelogin", "configuration-register");
    private static final Key EMAIL_REGISTER = Key.key("librelogin", "configuration-email-register");
    private static final Key REGISTER_CONFIRM = Key.key("librelogin", "configuration-register-confirm");
    private static final Key REGISTER_TO_EMAIL = Key.key("librelogin", "configuration-register-to-email");
    private static final Key REGISTER_BACK = Key.key("librelogin", "configuration-register-back");
    private static final Key EMAIL_SUBMIT = Key.key("librelogin", "configuration-email-submit");
    private static final Key EMAIL_VERIFY = Key.key("librelogin", "configuration-email-verify");
    private static final Key EMAIL_RESEND = Key.key("librelogin", "configuration-email-resend");
    private static final Key DISCONNECT = Key.key("librelogin", "configuration-disconnect");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final PaperLibreLogin plugin;
    private final Map<UUID, CompletableFuture<Boolean>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<UUID, String> addresses = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<String, String> addressesByName = new ConcurrentHashMap<>();
    private final Map<UUID, AuthenticatedEvent.AuthenticationReason> preAuthenticated = new ConcurrentHashMap<>();
    private final Map<UUID, User> preAuthenticatedUsers = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingRegistrationPasswords = new ConcurrentHashMap<>();
    private final Map<UUID, PendingEmailRegistration> pendingEmailRegistrations = new ConcurrentHashMap<>();
    private final EmailValidationUtil emailValidator;
    private final EmailRegistrationRateLimiter emailRateLimiter;

    public ConfigurationPhaseListener(PaperLibreLogin plugin) {
        this.plugin = plugin;
        this.emailValidator = new EmailValidationUtil(plugin);
        this.emailRateLimiter = new EmailRegistrationRateLimiter(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        InetAddress address = event.getAddress();
        if (address != null) {
            String host = address.getHostAddress();
            addresses.put(event.getUniqueId(), host);
            names.put(event.getUniqueId(), event.getName());
            addressesByName.put(event.getName(), host);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_USE_CONFIGURATION_PHASE)
                || !plugin.getConfiguration().get(ConfigurationKeys.USE_FANCYDIALOGS)) {
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        UUID uuid = connection.getProfile().getId();
        String name = connection.getProfile().getName();
        if (uuid == null || name == null) {
            disconnect(connection, component(raw(MessageKeys.DIALOG_COMMON_PROFILE_UNAVAILABLE.key())));
            return;
        }
        Integer protocolVersion = protocolVersion(connection);
        if (protocolVersion != null && protocolVersion < 769) {
            disconnect(connection, component(raw(MessageKeys.DIALOG_COMMON_VERSION_UNSUPPORTED.key())));
            return;
        }

        User user = plugin.getDatabaseProvider().getByName(name);
        if (user == null) {
            disconnect(connection, component(raw(MessageKeys.DIALOG_COMMON_ACCOUNT_LOAD_FAILED.key())));
            return;
        }

        if (canSkipAuthentication(user, uuid, name)) {
            return;
        }

        users.put(uuid, user);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        future.completeOnTimeout(false,
                plugin.getConfigurationPhaseTimeoutMillis(
                        plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CONFIGURATION_PHASE_TIMEOUT)),
                TimeUnit.MILLISECONDS);
        pending.put(uuid, future);
        show(connection.getAudience(), user, null);

        if (!future.join()) {
            disconnect(connection, component(raw(MessageKeys.KICK_TIME_LIMIT.key())));
        }

        pending.remove(uuid);
        users.remove(uuid);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDialogClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection connection)) {
            return;
        }

        UUID uuid = connection.getProfile().getId();
        CompletableFuture<Boolean> future = pending.get(uuid);
        User user = users.get(uuid);
        if (future == null || user == null) {
            return;
        }

        Key action = event.getIdentifier();
        if (DISCONNECT.equals(action)) {
            future.complete(false);
            return;
        }
        if (REGISTER_CONFIRM.equals(action)) {
            completeRegistration(connection, user, future);
            return;
        }
        if (REGISTER_TO_EMAIL.equals(action)) {
            if (!emailRegistrationAvailable()) {
                showRegister(connection.getAudience(), user,
                        raw(MessageKeys.ERROR_EMAIL_REGISTER_DISABLED.key()));
                return;
            }
            showEmailInput(connection.getAudience(), user, null);
            return;
        }
        if (REGISTER_BACK.equals(action)) {
            clearRegistrationState(uuid);
            showRegister(connection.getAudience(), user, null);
            return;
        }
        if (EMAIL_RESEND.equals(action)) {
            resendRegistrationEmail(connection, user);
            return;
        }

        DialogResponseView response = event.getDialogResponseView();
        if (response == null) {
            return;
        }

        if (LOGIN.equals(action)) {
            handleLogin(connection, user, response, future);
        } else if (REGISTER.equals(action)) {
            handleRegister(connection, user, response, future);
        } else if (EMAIL_REGISTER.equals(action)) {
            handleEmailRegister(connection, user, response);
        } else if (EMAIL_SUBMIT.equals(action)) {
            handleEmailSubmit(connection, user, response);
        } else if (EMAIL_VERIFY.equals(action)) {
            handleEmailVerify(connection, user, response, future);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        cleanup(event.getPlayerUniqueId());
    }

    public AuthenticatedEvent.AuthenticationReason consumeAuthenticationReason(UUID uuid) {
        return preAuthenticated.remove(uuid);
    }

    public User consumeAuthenticatedUser(UUID uuid) {
        return preAuthenticatedUsers.remove(uuid);
    }

    private void handleLogin(PlayerConfigurationConnection connection, User user,
                             DialogResponseView response, CompletableFuture<Boolean> future) {
        String password = response.getText("password");
        String code = response.getText("totp_code");
        if (password == null || password.isBlank()) {
            show(connection.getAudience(), user, raw(MessageKeys.ERROR_EMPTY_INPUT.key()));
            return;
        }

        var hash = user.getHashedPassword();
        CryptoProvider crypto = hash == null ? null : plugin.getCryptoProvider(hash.algo());
        boolean valid = crypto != null && crypto.matches(password, hash);
        if (valid && user.getSecret() != null) {
            try {
                valid = code != null && !code.isBlank() && plugin.getTOTPProvider() != null
                        && plugin.getTOTPProvider().verify(Integer.parseInt(code.trim()), user.getSecret());
            } catch (NumberFormatException ignored) {
                valid = false;
            }
        }

        if (!valid) {
            show(connection.getAudience(), user, raw(MessageKeys.ERROR_LOGIN_FAILED.key()));
            return;
        }

        UUID uuid = connection.getProfile().getId();
        preAuthenticated.put(uuid, AuthenticatedEvent.AuthenticationReason.LOGIN);
        preAuthenticatedUsers.put(uuid, user);
        future.complete(true);
    }

    private void handleRegister(PlayerConfigurationConnection connection, User user,
                                DialogResponseView response, CompletableFuture<Boolean> future) {
        if (user.isRegistered()) {
            show(connection.getAudience(), user, raw(MessageKeys.ERROR_ALREADY_REGISTERED.key()));
            return;
        }

        String password = validateRegistrationPassword(connection.getAudience(), user, response);
        if (password == null) {
            return;
        }

        pendingRegistrationPasswords.put(connection.getProfile().getId(), password);
        showRegisterConfirmation(connection.getAudience());
    }

    private void handleEmailRegister(PlayerConfigurationConnection connection, User user,
                                     DialogResponseView response) {
        if (!emailRegistrationAvailable()) {
            showRegister(connection.getAudience(), user, raw(MessageKeys.ERROR_EMAIL_REGISTER_DISABLED.key()));
            return;
        }

        String password = validateRegistrationPassword(connection.getAudience(), user, response);
        if (password == null) {
            return;
        }

        pendingRegistrationPasswords.put(connection.getProfile().getId(), password);
        showEmailInput(connection.getAudience(), user, null);
    }

    private String validateRegistrationPassword(Audience audience, User user, DialogResponseView response) {
        String password = response.getText("password");
        String confirmation = response.getText("password_confirm");
        if (password == null || password.isBlank() || confirmation == null || confirmation.isBlank()) {
            showRegister(audience, user, raw(MessageKeys.ERROR_REGISTER_MISSING_FIELDS.key()));
            return null;
        }
        password = password.trim();
        confirmation = confirmation.trim();
        if (!password.equals(confirmation)) {
            showRegister(audience, user, raw(MessageKeys.ERROR_PASSWORD_NOT_MATCH.key()));
            return null;
        }

        int minimumLength = plugin.getConfiguration().get(ConfigurationKeys.MINIMUM_PASSWORD_LENGTH);
        if (minimumLength > 0 && password.length() < minimumLength) {
            showRegister(audience, user, raw(MessageKeys.ERROR_PASSWORD_TOO_SHORT.key())
                    .replace("%length%", String.valueOf(minimumLength)));
            return null;
        }
        if (!plugin.validPassword(password)) {
            showRegister(audience, user, raw(MessageKeys.ERROR_FORBIDDEN_PASSWORD.key()));
            return null;
        }
        return password;
    }

    private void handleEmailSubmit(PlayerConfigurationConnection connection, User user,
                                   DialogResponseView response) {
        UUID uuid = connection.getProfile().getId();
        String password = pendingRegistrationPasswords.get(uuid);
        if (password == null) {
            showRegister(connection.getAudience(), user, raw(MessageKeys.ERROR_REGISTER_MISSING_FIELDS.key()));
            return;
        }
        if (!emailRegistrationAvailable()) {
            showRegister(connection.getAudience(), user, raw(MessageKeys.ERROR_EMAIL_REGISTER_DISABLED.key()));
            return;
        }

        String email = response.getText("email");
        if (email == null || email.isBlank()) {
            showEmailInput(connection.getAudience(), user, raw(MessageKeys.ERROR_EMPTY_INPUT.key()));
            return;
        }
        email = emailValidator.normalizeEmail(email);
        var validation = emailValidator.validateEmail(email);
        if (!validation.isValid()) {
            showEmailInput(connection.getAudience(), user, raw(validation.getErrorMessageKey()));
            return;
        }

        String ip = addresses.getOrDefault(uuid, addressesByName.get(connection.getProfile().getName()));
        var rateLimit = emailRateLimiter.checkRateLimit(uuid, ip, email);
        if (rateLimit.isLimited()) {
            String error = raw(rateLimit.getLimitType().getMessageKey())
                    .replace("%minutes%", String.valueOf(rateLimit.getRemainingMinutes()));
            showEmailInput(connection.getAudience(), user, error);
            return;
        }

        sendRegistrationEmail(connection, user, email, password, false);
    }

    private void handleEmailVerify(PlayerConfigurationConnection connection, User user,
                                   DialogResponseView response, CompletableFuture<Boolean> future) {
        UUID uuid = connection.getProfile().getId();
        PendingEmailRegistration registration = pendingEmailRegistrations.get(uuid);
        if (registration == null || registration.expiresAtMillis() < System.currentTimeMillis()) {
            pendingEmailRegistrations.remove(uuid);
            showRegister(connection.getAudience(), user, raw(MessageKeys.ERROR_EMAIL_VERIFICATION_TIMEOUT.key()));
            return;
        }
        if (registration.expiresAtMillis() < System.currentTimeMillis()) {
            pendingEmailRegistrations.remove(uuid);
            showEmailInput(connection.getAudience(), user, raw(MessageKeys.ERROR_EMAIL_VERIFICATION_TIMEOUT.key()));
            return;
        }

        String code = response.getText("verification_code");
        if (code == null || code.isBlank()) {
            showEmailVerification(connection.getAudience(), registration.email(), raw(MessageKeys.ERROR_EMPTY_INPUT.key()));
            return;
        }
        if (!registration.code().equals(code.trim())) {
            showEmailVerification(connection.getAudience(), registration.email(),
                    raw(MessageKeys.ERROR_EMAIL_VERIFICATION_INVALID.key()));
            return;
        }

        var hash = plugin.getDefaultCryptoProvider().createHash(registration.password());
        if (hash == null) {
            showEmailVerification(connection.getAudience(), registration.email(),
                    raw(MessageKeys.ERROR_PASSWORD_TOO_LONG.key()));
            return;
        }

        user.setEmail(registration.email());
        user.setHashedPassword(hash);
        plugin.getDatabaseProvider().updateUser(user);
        clearRegistrationState(uuid);
        preAuthenticated.put(uuid, AuthenticatedEvent.AuthenticationReason.REGISTER);
        preAuthenticatedUsers.put(uuid, user);
        future.complete(true);
    }

    private void resendRegistrationEmail(PlayerConfigurationConnection connection, User user) {
        UUID uuid = connection.getProfile().getId();
        PendingEmailRegistration registration = pendingEmailRegistrations.get(uuid);
        if (registration == null) {
            showRegister(connection.getAudience(), user, raw(MessageKeys.ERROR_EMAIL_VERIFICATION_TIMEOUT.key()));
            return;
        }
        sendRegistrationEmail(connection, user, registration.email(), registration.password(), true);
    }

    private void sendRegistrationEmail(PlayerConfigurationConnection connection, User user,
                                       String email, String password, boolean resend) {
        try {
            String code = GeneralUtil.generateAlphanumericText(16);
            String name = user.getLastNickname() != null
                    ? user.getLastNickname()
                    : connection.getProfile().getName();
            plugin.getEmailHandler().sendVerificationMail(email, code, name);

            long timeoutSeconds = Math.max(1,
                    plugin.getConfiguration().get(ConfigurationKeys.EMAIL_VERIFICATION_TIMEOUT));
            pendingEmailRegistrations.put(connection.getProfile().getId(),
                    new PendingEmailRegistration(email, password, code,
                            System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds)));
            showEmailVerification(connection.getAudience(), email,
                    resend ? raw(MessageKeys.INFO_EMAIL_RESENT.key()) : null);
        } catch (Exception exception) {
            plugin.getLogger().error("Failed to send registration verification email to " + email + ": "
                    + exception.getMessage());
            showEmailInput(connection.getAudience(), user, raw(MessageKeys.ERROR_MAIL_NOT_SENT.key()));
        }
    }

    private void completeRegistration(PlayerConfigurationConnection connection, User user,
                                      CompletableFuture<Boolean> future) {
        UUID uuid = connection.getProfile().getId();
        String password = pendingRegistrationPasswords.remove(uuid);
        if (password == null) {
            showRegister(connection.getAudience(), user, raw(MessageKeys.ERROR_EMPTY_INPUT.key()));
            return;
        }

        CryptoProvider crypto = plugin.getDefaultCryptoProvider();
        var hash = crypto == null ? null : crypto.createHash(password);
        if (hash == null) {
            showRegister(connection.getAudience(), user, raw(MessageKeys.ERROR_PASSWORD_TOO_LONG.key()));
            return;
        }

        user.setHashedPassword(hash);
        user.setLastAuthentication(Timestamp.valueOf(LocalDateTime.now()));
        user.setIp(addresses.getOrDefault(uuid, addressesByName.get(connection.getProfile().getName())));
        plugin.getDatabaseProvider().updateUser(user);
        clearRegistrationState(uuid);
        preAuthenticated.put(uuid, AuthenticatedEvent.AuthenticationReason.REGISTER);
        preAuthenticatedUsers.put(uuid, user);
        future.complete(true);
    }

    private void show(Audience audience, User user, String error) {
        showDialog(audience, createDialog(user, error, user.isRegistered() ? LOGIN : REGISTER));
    }

    private void showRegister(Audience audience, User user, String error) {
        showDialog(audience, createDialog(user, error, REGISTER));
    }

    private void showRegisterConfirmation(Audience audience) {
        Component title = component(raw(MessageKeys.DIALOG_REGISTER_CONFIRMATION_TITLE.key()));
        Component body = component(raw(MessageKeys.DIALOG_REGISTER_CONFIRMATION_BODY.key()));
        DialogInstancesProvider provider = DialogInstancesProvider.instance();
        ActionButton confirm = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_CONFIRM_CONTINUE.key())))
                .width(150)
                .action(provider.customClick(REGISTER_CONFIRM, null))
                .build();
        ActionButton email = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_GO_EMAIL_REGISTER.key())))
                .width(150)
                .action(provider.customClick(REGISTER_TO_EMAIL, null))
                .build();
        ActionButton back = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_BACK_TO_REGISTER.key())))
                .width(150)
                .action(provider.customClick(REGISTER_BACK, null))
                .build();
        ActionButton cancel = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_DISCONNECT.key())))
                .width(150)
                .action(provider.customClick(DISCONNECT, null))
                .build();

        Dialog dialog = Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.builder(title)
                    .canCloseWithEscape(false)
                    .body(List.of(DialogBody.plainMessage(body)))
                    .build());
            builder.type(DialogType.multiAction(List.of(confirm, email, back, cancel), null, 2));
        });
        showDialog(audience, dialog);
    }

    private void showEmailInput(Audience audience, User user, String error) {
        Component title = component(raw(MessageKeys.DIALOG_EMAIL_INPUT_TITLE.key()));
        Component body = component(raw(MessageKeys.DIALOG_EMAIL_INPUT_BODY.key()));
        List<DialogBody> bodies = dialogBodies(body, error);
        List<DialogInput> inputs = List.of(
                DialogInput.text("email", component(raw(MessageKeys.DIALOG_EMAIL_INPUT_EMAIL_LABEL.key())))
                        .maxLength(254).build());

        DialogInstancesProvider provider = DialogInstancesProvider.instance();
        ActionButton submit = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_EMAIL_SUBMIT.key())))
                .width(150)
                .action(provider.customClick(EMAIL_SUBMIT, null))
                .build();
        ActionButton back = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_BACK_TO_REGISTER.key())))
                .width(150)
                .action(provider.customClick(REGISTER_BACK, null))
                .build();
        ActionButton cancel = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_DISCONNECT.key())))
                .width(150)
                .action(provider.customClick(DISCONNECT, null))
                .build();

        Dialog dialog = Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.builder(title)
                    .canCloseWithEscape(false)
                    .body(bodies)
                    .inputs(inputs)
                    .build());
            builder.type(DialogType.multiAction(List.of(submit, back, cancel), null, 2));
        });
        showDialog(audience, dialog);
    }

    private void showEmailVerification(Audience audience, String email, String message) {
        Component title = component(raw(MessageKeys.DIALOG_EMAIL_VERIFICATION_TITLE.key()));
        int timeoutMinutes = Math.max(1,
                plugin.getConfiguration().get(ConfigurationKeys.EMAIL_VERIFICATION_TIMEOUT) / 60);
        Component body = component(raw(MessageKeys.DIALOG_EMAIL_VERIFICATION_BODY.key())
                .replace("%email%", email)
                .replace("%timeout%", String.valueOf(timeoutMinutes)));
        List<DialogBody> bodies = dialogBodies(body, message);
        List<DialogInput> inputs = List.of(
                DialogInput.text("verification_code",
                                component(raw(MessageKeys.DIALOG_EMAIL_VERIFICATION_CODE_LABEL.key())))
                        .maxLength(16).build());

        DialogInstancesProvider provider = DialogInstancesProvider.instance();
        ActionButton verify = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_VERIFY_EMAIL.key())))
                .width(150)
                .action(provider.customClick(EMAIL_VERIFY, null))
                .build();
        ActionButton resend = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_RESEND_EMAIL.key())))
                .width(150)
                .action(provider.customClick(EMAIL_RESEND, null))
                .build();
        ActionButton back = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_BACK_TO_REGISTER.key())))
                .width(150)
                .action(provider.customClick(REGISTER_BACK, null))
                .build();
        ActionButton cancel = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_DISCONNECT.key())))
                .width(150)
                .action(provider.customClick(DISCONNECT, null))
                .build();

        Dialog dialog = Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.builder(title)
                    .canCloseWithEscape(false)
                    .body(bodies)
                    .inputs(inputs)
                    .build());
            builder.type(DialogType.multiAction(List.of(verify, resend, back, cancel), null, 2));
        });
        showDialog(audience, dialog);
    }

    private Dialog createDialog(User user, String error, Key actionKey) {
        Component title = component(actionKey.equals(LOGIN)
                ? raw(MessageKeys.DIALOG_LOGIN_TITLE.key())
                : raw(MessageKeys.DIALOG_REGISTER_TITLE.key()));
        Component body = component(actionKey.equals(LOGIN)
                ? raw(MessageKeys.DIALOG_LOGIN_BODY.key())
                : raw(MessageKeys.DIALOG_REGISTER_BODY.key()));
        List<DialogBody> bodies = dialogBodies(body, error);

        List<DialogInput> inputs = actionKey.equals(LOGIN)
                ? loginInputs(user)
                : List.of(
                        DialogInput.text("password", component(raw(MessageKeys.DIALOG_REGISTER_PASSWORD_LABEL.key())))
                                .maxLength(128).build(),
                        DialogInput.text("password_confirm", component(raw(MessageKeys.DIALOG_REGISTER_CONFIRM_LABEL.key())))
                                .maxLength(128).build());

        DialogInstancesProvider provider = DialogInstancesProvider.instance();
        ActionButton cancel = ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_DISCONNECT.key())))
                .width(150)
                .action(provider.customClick(DISCONNECT, null))
                .build();

        List<ActionButton> actions = new ArrayList<>();
        if (actionKey.equals(LOGIN)) {
            actions.add(ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_LOGIN.key())))
                    .width(150)
                    .action(provider.customClick(LOGIN, null))
                    .build());
        } else {
            boolean forceEmail = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_FORCE)
                    && emailRegistrationAvailable();
            if (!forceEmail) {
                actions.add(ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_REGISTER.key())))
                        .width(150)
                        .action(provider.customClick(REGISTER, null))
                        .build());
            }
            actions.add(ActionButton.builder(component(raw(MessageKeys.DIALOG_BUTTON_EMAIL_REGISTER.key())))
                    .width(150)
                    .action(provider.customClick(EMAIL_REGISTER, null))
                    .build());
        }
        actions.add(cancel);

        return Dialog.create(factory -> {
            var builder = factory.empty();
            builder.base(DialogBase.builder(title)
                    .canCloseWithEscape(false)
                    .body(bodies)
                    .inputs(inputs)
                    .build());
            builder.type(DialogType.multiAction(actions, null, 2));
        });
    }

    private List<DialogBody> dialogBodies(Component body, String message) {
        return message == null || message.isBlank()
                ? List.of(DialogBody.plainMessage(body))
                : List.of(DialogBody.plainMessage(component(message)), DialogBody.plainMessage(body));
    }

    private void showDialog(Audience audience, Dialog dialog) {
        try {
            for (Method method : audience.getClass().getMethods()) {
                if (!method.getName().equals("showDialog") || method.getParameterCount() != 1) {
                    continue;
                }
                if (method.getParameterTypes()[0].isAssignableFrom(dialog.getClass())) {
                    method.invoke(audience, dialog);
                    return;
                }
            }
            audience.sendMessage(component(raw(MessageKeys.DIALOG_COMMON_CLIENT_UNSUPPORTED.key())));
        } catch (Exception exception) {
            plugin.getLogger().warn("Unable to show configuration-phase authentication dialog: " + exception.getMessage());
        }
    }

    private List<DialogInput> loginInputs(User user) {
        var inputs = new java.util.ArrayList<DialogInput>();
        inputs.add(DialogInput.text("password", component(raw(MessageKeys.DIALOG_LOGIN_PASSWORD_LABEL.key())))
                .maxLength(128).build());
        if (user.getSecret() != null) {
            inputs.add(DialogInput.text("totp_code", component(raw(MessageKeys.DIALOG_LOGIN_2FA_LABEL.key())))
                    .maxLength(6).build());
        }
        return inputs;
    }

    private boolean canSkipAuthentication(User user, UUID uuid, String name) {
        String ip = addresses.getOrDefault(uuid, addressesByName.get(name));
        if (user.autoLoginEnabled()) {
            return true;
        }
        long timeout = plugin.getConfiguration().get(ConfigurationKeys.SESSION_TIMEOUT);
        if (timeout <= 0 || user.getLastAuthentication() == null || ip == null || !ip.equals(user.getIp())) {
            return false;
        }
        return user.getLastAuthentication().toLocalDateTime().plus(Duration.ofSeconds(timeout))
                .isAfter(LocalDateTime.now());
    }

    private boolean emailRegistrationAvailable() {
        return plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_ENABLED)
                && plugin.getConfiguration().get(ConfigurationKeys.MAIL_ENABLED);
    }

    private void clearRegistrationState(UUID uuid) {
        pendingRegistrationPasswords.remove(uuid);
        pendingEmailRegistrations.remove(uuid);
    }

    private void cleanup(UUID uuid) {
        CompletableFuture<Boolean> future = pending.remove(uuid);
        if (future != null) {
            future.complete(false);
        }
        users.remove(uuid);
        addresses.remove(uuid);
        String name = names.remove(uuid);
        if (name != null) {
            addressesByName.remove(name);
        }
        preAuthenticated.remove(uuid);
        preAuthenticatedUsers.remove(uuid);
        clearRegistrationState(uuid);
    }

    private String raw(String key) {
        return plugin.getMessages().getRawMessage(key);
    }

    private Component component(String value) {
        try {
            return MINI_MESSAGE.deserialize(value == null ? "" : value);
        } catch (Exception ignored) {
            return Component.text(value == null ? "" : value);
        }
    }

    private void disconnect(PlayerConfigurationConnection connection, Component reason) {
        try {
            Method method = connection.getClass().getMethod("disconnect", Component.class);
            method.invoke(connection, reason);
        } catch (Exception ignored) {
            connection.getAudience().sendMessage(reason);
            try {
                connection.getClass().getMethod("close").invoke(connection);
            } catch (Exception ignoredAgain) {
                // Older Paper builds expose no configuration-phase disconnect method.
            }
        }
    }

    private Integer protocolVersion(PlayerConfigurationConnection connection) {
        try {
            Method method = connection.getClass().getMethod("getProtocolVersion");
            Object value = method.invoke(connection);
            return value instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private record PendingEmailRegistration(String email, String password, String code,
                                            long expiresAtMillis) {
    }
}
