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

/**
 * Email input dialog for FancyDialogs integration.
 * Shows a dialog with only email input field (password already provided).
 *
 * @author LibreLogin Contributors
 */
public class EmailInputDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public EmailInputDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates an email input dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @param password the already validated password
     * @param passwordConfirm the password confirmation
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String password, String passwordConfirm) {
        return create(player, user, password, passwordConfirm, null, null);
    }

    /**
     * Creates an email input dialog with an optional error message.
     *
     * @param player the player
     * @param user   the user data
     * @param password the already validated password
     * @param passwordConfirm the password confirmation
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, null for default
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String password, String passwordConfirm, String errorMessage, String errorType) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_INPUT_TITLE.key());
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_INPUT_BODY.key());
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Create body with error message if present
        List<DialogBodyData> bodyList = new ArrayList<>();
        
        // Add error message at the top if present
        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodyList.add(new DialogBodyData(DialogContent.status(plugin, errorMessage, errorType)));
        }
        
        bodyList.add(new DialogBodyData(body));

        // Create input fields - only email field
        List<DialogTextField> textFields = new ArrayList<>();

        // Email field
        String emailLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_INPUT_EMAIL_LABEL.key());
        DialogTextField emailField = new DialogTextField(
                "email",
                emailLabel,
                1,
                "",
                254, // RFC 5321 maximum email address length
                1,
                new HashMap<>(), // requirements
                null // width (use default)
        );
        textFields.add(emailField);

        DialogInputs inputs = new DialogInputs(textFields, null, null);

        // Create buttons
        List<DialogButton> buttons = new ArrayList<>();

        // Submit registration button
        String submitButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_EMAIL_SUBMIT.key());
        List<DialogButton.DialogAction> submitActions = new ArrayList<>();
        // 将密码和邮箱一起传递: password:password_confirm:email
        // 注意：这里的password和passwordConfirm是已经验证过的固定值，email来自用户输入
        // 使用dialog ID作为第一个参数，因为这是表单提交按钮
        submitActions.add(new DialogButton.DialogAction("librelogin_email_input", password + ":" + passwordConfirm + ":{email}"));
        DialogButton submitButton = new DialogButton(
                submitButtonText, 
                null, 
                submitActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(submitButton);

        // Back to register button
        String backButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_BACK_TO_REGISTER.key());
        List<DialogButton.DialogAction> backActions = new ArrayList<>();
        backActions.add(new DialogButton.DialogAction("librelogin_back_to_register", "librelogin_back_to_register"));
        DialogButton backButton = new DialogButton(
                backButtonText, 
                null, 
                backActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(backButton);

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
                "librelogin_email_input",
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
