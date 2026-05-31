/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.dialogs;

import com.fancyinnovations.fancydialogs.api.Dialog;
import com.fancyinnovations.fancydialogs.api.data.DialogBodyData;
import com.fancyinnovations.fancydialogs.api.data.DialogButton;
import com.fancyinnovations.fancydialogs.api.data.DialogData;
import com.fancyinnovations.fancydialogs.api.data.inputs.DialogInputs;
import com.fancyinnovations.fancydialogs.api.data.inputs.DialogTextField;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Login dialog for FancyDialogs integration.
 * Shows a dialog with password input and optional 2FA code input.
 *
 * @author LibreLogin Contributors
 */
public class LoginDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public LoginDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates a login dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @return the created dialog
     */
    public Dialog create(Player player, User user) {
        return create(player, user, null, null);
    }

    /**
     * Creates a login dialog with an optional error message.
     *
     * @param player the player
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, null for default
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String errorMessage, String errorType) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_LOGIN_TITLE.key());
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_LOGIN_BODY.key());
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Create body with error message if present
        List<DialogBodyData> bodyList = new ArrayList<>();
        
        // Add error message at the top if present
        if (errorMessage != null && !errorMessage.isEmpty()) {
            String coloredError;
            if ("warning".equals(errorType)) {
                // Yellow warning
                coloredError = "<yellow>⚠ " + errorMessage + "</yellow>\n";
            } else {
                // Red error (default)
                coloredError = "<red>✖ " + errorMessage + "</red>\n";
            }
            bodyList.add(new DialogBodyData(coloredError));
        }
        
        bodyList.add(new DialogBodyData(body));

        // Create input fields
        List<DialogTextField> textFields = new ArrayList<>();

        // Password field
        String passwordLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_LOGIN_PASSWORD_LABEL.key());
        DialogTextField passwordField = new DialogTextField(
                "password",
                passwordLabel,
                1,
                "",
                128,
                1,
                new HashMap<>(), // requirements
                null // width (use default)
        );
        textFields.add(passwordField);

        // 2FA field (if user has 2FA enabled)
        if (user.getSecret() != null) {
            String twoFALabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_LOGIN_2FA_LABEL.key());
            DialogTextField twoFAField = new DialogTextField(
                    "totp_code",
                    twoFALabel,
                    2,
                    "",
                    6,
                    1,
                    new HashMap<>(), // requirements
                    null // width (use default)
            );
            textFields.add(twoFAField);
        }

        DialogInputs inputs = new DialogInputs(textFields, null, null);

        // Create buttons
        List<DialogButton> buttons = new ArrayList<>();

        // Login button
        String loginButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_LOGIN.key());
        List<DialogButton.DialogAction> loginActions = new ArrayList<>();
        // 使用占位符，FancyDialogs 会自动替换为用户输入
        // 格式: password:totp_code (如果没有2FA，totp_code会是空字符串)
        String dataTemplate = user.getSecret() != null ? "{password}:{totp_code}" : "{password}";
        loginActions.add(new DialogButton.DialogAction("librelogin_login", dataTemplate));
        DialogButton loginButton = new DialogButton(
                loginButtonText, 
                null, 
                loginActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(loginButton);

        // Forgot password button (if email is configured)
        if (user.getEmail() != null) {
            String forgotPasswordText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_FORGOT_PASSWORD.key());
            List<DialogButton.DialogAction> forgotActions = new ArrayList<>();
            forgotActions.add(new DialogButton.DialogAction("librelogin_forgot_password", "librelogin_forgot_password"));
            DialogButton forgotButton = new DialogButton(
                    forgotPasswordText, 
                    null, 
                    forgotActions, 
                    new HashMap<>(), // requirements
                    150 // width
            );
            buttons.add(forgotButton);
        }

        // Disconnect button
        String disconnectButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_DISCONNECT.key());
        List<DialogButton.DialogAction> disconnectActions = new ArrayList<>();
        disconnectActions.add(new DialogButton.DialogAction("librelogin_disconnect", "librelogin_disconnect"));
        DialogButton disconnectButton = new DialogButton(
                disconnectButtonText, 
                null, 
                disconnectActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(disconnectButton);

        // Create dialog data
        DialogData data = new DialogData(
                "librelogin_login",
                title,
                canCloseWithEscape,
                bodyList,
                inputs,
                buttons,
                null, // exitAction
                null  // columns (use default)
        );

        return manager.getFancyDialogs().createDialog(data);
    }
}

