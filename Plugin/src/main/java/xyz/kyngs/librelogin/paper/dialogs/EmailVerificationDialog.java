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
import java.util.List;

/**
 * Email verification dialog for FancyDialogs integration.
 * Shows a dialog with verification code input and resend email functionality.
 *
 * @author LibreLogin Contributors
 */
public class EmailVerificationDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public EmailVerificationDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates an email verification dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @param email  the email address being verified
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String email) {
        return create(player, user, email, false, null, null);
    }

    /**
     * Creates an email verification dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @param email  the email address being verified
     * @param showResendButton whether to show the resend email button
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String email, boolean showResendButton) {
        return create(player, user, email, showResendButton, null, null);
    }

    /**
     * Creates an email verification dialog with an optional error message.
     *
     * @param player the player
     * @param user   the user data
     * @param email  the email address being verified
     * @param showResendButton whether to show the resend email button
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, null for default
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String email, boolean showResendButton, String errorMessage, String errorType) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_VERIFICATION_TITLE.key());
        
        // Replace placeholders in body
        int timeoutMinutes = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_VERIFICATION_TIMEOUT) / 60;
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_VERIFICATION_BODY.key())
                .replace("%email%", email != null ? email : "未知")
                .replace("%timeout%", String.valueOf(timeoutMinutes));
        
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Create body with error message if present
        List<DialogBodyData> bodyList = new ArrayList<>();
        
        // Add message at the top if present
        if (errorMessage != null && !errorMessage.isEmpty()) {
            String coloredMessage;
            if ("warning".equals(errorType)) {
                // Yellow warning
                coloredMessage = "<yellow>⚠ " + errorMessage + "</yellow>\n";
            } else if ("success".equals(errorType)) {
                // Green success
                coloredMessage = "<green>✓ " + errorMessage + "</green>\n";
            } else {
                // Red error (default)
                coloredMessage = "<red>✖ " + errorMessage + "</red>\n";
            }
            bodyList.add(new DialogBodyData(coloredMessage));
        }
        
        bodyList.add(new DialogBodyData(body));

        // Create input fields
        List<DialogTextField> textFields = new ArrayList<>();

        // Verification code field
        String codeLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_VERIFICATION_CODE_LABEL.key());
        DialogTextField codeField = new DialogTextField(
                "verification_code",
                codeLabel,
                1,
                "",
                16, // Verification codes are typically short
                1
        );
        textFields.add(codeField);

        DialogInputs inputs = new DialogInputs(textFields, null, null);

        // Create buttons
        List<DialogButton> buttons = new ArrayList<>();

        // Verify button
        String verifyButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_VERIFY_EMAIL.key());
        List<DialogButton.DialogAction> verifyActions = new ArrayList<>();
        // 使用占位符，FancyDialogs 会自动替换为用户输入
        verifyActions.add(new DialogButton.DialogAction("librelogin_email_verify", "{verification_code}"));
        DialogButton verifyButton = new DialogButton(verifyButtonText, null, verifyActions);
        buttons.add(verifyButton);

        // Resend email button (only shown if allowed)
        if (showResendButton) {
            String resendButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_RESEND_EMAIL.key());
            List<DialogButton.DialogAction> resendActions = new ArrayList<>();
            resendActions.add(new DialogButton.DialogAction("librelogin_resend_registration_email", "librelogin_resend_registration_email"));
            DialogButton resendButton = new DialogButton(resendButtonText, null, resendActions);
            buttons.add(resendButton);
        }

        // Back to register button
        String backButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_BACK_TO_LOGIN.key());
        List<DialogButton.DialogAction> backActions = new ArrayList<>();
        backActions.add(new DialogButton.DialogAction("librelogin_back_to_register", "librelogin_back_to_register"));
        DialogButton backButton = new DialogButton(backButtonText, null, backActions);
        buttons.add(backButton);

        // Disconnect button
        String disconnectButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_DISCONNECT.key());
        List<DialogButton.DialogAction> disconnectActions = new ArrayList<>();
        disconnectActions.add(new DialogButton.DialogAction("librelogin_disconnect", "librelogin_disconnect"));
        DialogButton disconnectButton = new DialogButton(disconnectButtonText, null, disconnectActions);
        buttons.add(disconnectButton);

        // Create dialog data
        DialogData data = new DialogData(
                "librelogin_email_verification",
                title,
                canCloseWithEscape,
                bodyList,
                inputs,
                buttons
        );

        return manager.getFancyDialogs().createDialog(data);
    }
}
