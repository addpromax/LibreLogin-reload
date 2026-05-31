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
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Register confirmation dialog for FancyDialogs integration.
 * Shows a warning about not being able to recover password without email.
 *
 * @author LibreLogin Contributors
 */
public class RegisterConfirmationDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public RegisterConfirmationDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates a register confirmation dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @param password the already validated password
     * @param passwordConfirm the password confirmation
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String password, String passwordConfirm) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_REGISTER_CONFIRMATION_TITLE.key());
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_REGISTER_CONFIRMATION_BODY.key());
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Create body with warning
        List<DialogBodyData> bodyList = new ArrayList<>();
        bodyList.add(new DialogBodyData(body));

        // No input fields needed for confirmation dialog
        DialogInputs inputs = new DialogInputs(null, null, null);

        // Create buttons
        List<DialogButton> buttons = new ArrayList<>();

        // Confirm continue button (proceed without email)
        String confirmButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_CONFIRM_CONTINUE.key());
        List<DialogButton.DialogAction> confirmActions = new ArrayList<>();
        // 传递密码数据以便直接注册
        confirmActions.add(new DialogButton.DialogAction("librelogin_register_confirm_continue", password + ":" + passwordConfirm));
        DialogButton confirmButton = new DialogButton(
                confirmButtonText, 
                null, 
                confirmActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(confirmButton);

        // Go to email registration button
        String emailRegisterButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_GO_EMAIL_REGISTER.key());
        List<DialogButton.DialogAction> emailRegisterActions = new ArrayList<>();
        // 传递密码数据到邮箱注册流程
        emailRegisterActions.add(new DialogButton.DialogAction("librelogin_go_email_register", password + ":" + passwordConfirm));
        DialogButton emailRegisterButton = new DialogButton(
                emailRegisterButtonText, 
                null, 
                emailRegisterActions, 
                new HashMap<>(), // requirements
                150 // width
        );
        buttons.add(emailRegisterButton);

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

        // Create dialog data
        DialogData data = new DialogData(
                "librelogin_register_confirmation",
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
