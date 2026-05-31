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
 * Register dialog for FancyDialogs integration.
 * Shows a dialog with password and password confirmation inputs.
 *
 * @author LibreLogin Contributors
 */
public class RegisterDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public RegisterDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates a register dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @return the created dialog
     */
    public Dialog create(Player player, User user) {
        return create(player, user, null, null);
    }

    /**
     * Creates a register dialog with an optional error message.
     *
     * @param player the player
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, null for default
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String errorMessage, String errorType) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_REGISTER_TITLE.key());
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_REGISTER_BODY.key());
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
        String passwordLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_REGISTER_PASSWORD_LABEL.key());
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

        // Password confirmation field
        String confirmLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_REGISTER_CONFIRM_LABEL.key());
        DialogTextField confirmField = new DialogTextField(
                "password_confirm",
                confirmLabel,
                2,
                "",
                128,
                1,
                new HashMap<>(), // requirements
                null // width (use default)
        );
        textFields.add(confirmField);

        DialogInputs inputs = new DialogInputs(textFields, null, null);

        // Create buttons
        List<DialogButton> buttons = new ArrayList<>();

        // Check email registration configuration
        boolean emailRegisterEnabled = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_ENABLED);
        boolean emailRegisterForced = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_FORCE);
        boolean mailEnabled = plugin.getConfiguration().get(ConfigurationKeys.MAIL_ENABLED);
        
        // Only show email registration if both mail and email registration are enabled
        boolean showEmailRegister = emailRegisterEnabled && mailEnabled;
        
        // Regular register button (hidden if email registration is forced)
        if (!emailRegisterForced || !showEmailRegister) {
            String registerButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_REGISTER.key());
            List<DialogButton.DialogAction> registerActions = new ArrayList<>();
            // 使用占位符，FancyDialogs 会自动替换为用户输入
            // 格式: NORMAL:password:password_confirm 来标识这是普通注册按钮
            registerActions.add(new DialogButton.DialogAction("librelogin_register", "NORMAL:{password}:{password_confirm}"));
            DialogButton registerButton = new DialogButton(
                    registerButtonText, 
                    null, 
                    registerActions, 
                    new HashMap<>(), // requirements
                    150 // width
            );
            buttons.add(registerButton);
        }

        // Email registration button (only shown if enabled)
        if (showEmailRegister) {
            String emailRegisterButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_EMAIL_REGISTER.key());
            List<DialogButton.DialogAction> emailRegisterActions = new ArrayList<>();
            // 传递密码数据给邮箱注册处理器，格式: EMAIL:password:password_confirm 来标识这是邮箱注册按钮
            emailRegisterActions.add(new DialogButton.DialogAction("librelogin_register", "EMAIL:{password}:{password_confirm}"));
            DialogButton emailRegisterButton = new DialogButton(
                    emailRegisterButtonText, 
                    null, 
                    emailRegisterActions, 
                    new HashMap<>(), // requirements
                    150 // width
            );
            buttons.add(emailRegisterButton);
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
                "librelogin_register",
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

