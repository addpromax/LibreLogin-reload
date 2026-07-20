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
 * Password reset dialog for FancyDialogs integration.
 * Shows a dialog for resetting password with email verification code.
 *
 * @author LibreLogin Contributors
 */
public class PasswordResetDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public PasswordResetDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates a password reset dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @return the created dialog
     */
    public Dialog create(Player player, User user) {
        return create(player, user, null, null);
    }

    /**
     * Creates a password reset dialog with an optional error message.
     *
     * @param player the player
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, null for default
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String errorMessage, String errorType) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_PASSWORD_RESET_TITLE.key());
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_PASSWORD_RESET_BODY.key());
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Create body with error message if present
        List<DialogBodyData> bodyList = new ArrayList<>();
        
        // Add error message at the top if present
        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodyList.add(new DialogBodyData(DialogContent.status(plugin, errorMessage, errorType)));
        }
        
        bodyList.add(new DialogBodyData(body));

        // Create input fields
        List<DialogTextField> textFields = new ArrayList<>();

        // Reset token field
        String tokenLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_PASSWORD_RESET_TOKEN_LABEL.key());
        DialogTextField tokenField = new DialogTextField(
                "reset_token",
                tokenLabel,
                1,
                "",
                16,
                1,
                new HashMap<>(), // requirements
                null // width (use default)
        );
        textFields.add(tokenField);

        // New password field
        String passwordLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_PASSWORD_RESET_PASSWORD_LABEL.key());
        DialogTextField passwordField = new DialogTextField(
                "new_password",
                passwordLabel,
                2,
                "",
                128,
                1,
                new HashMap<>(), // requirements
                null // width (use default)
        );
        textFields.add(passwordField);

        // Password confirmation field
        String confirmLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_PASSWORD_RESET_CONFIRM_LABEL.key());
        DialogTextField confirmField = new DialogTextField(
                "new_password_confirm",
                confirmLabel,
                3,
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

        // Reset password button
        String resetButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_RESET_PASSWORD.key());
        List<DialogButton.DialogAction> resetActions = new ArrayList<>();
        // 使用占位符，FancyDialogs 会自动替换为用户输入
        // 格式: reset_token:new_password:new_password_confirm
        resetActions.add(new DialogButton.DialogAction("librelogin_reset_password", "{reset_token}:{new_password}:{new_password_confirm}"));
        DialogButton resetButton = new DialogButton(
                resetButtonText, 
                null, 
                resetActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(resetButton);

        // Back to login button
        String backButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_BACK_TO_LOGIN.key());
        List<DialogButton.DialogAction> backActions = new ArrayList<>();
        backActions.add(new DialogButton.DialogAction("librelogin_back_to_login", "librelogin_back_to_login"));
        DialogButton backButton = new DialogButton(
                backButtonText, 
                null, 
                backActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(backButton);

        // Create dialog data
        DialogData data = new DialogData(
                "librelogin_password_reset",
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

