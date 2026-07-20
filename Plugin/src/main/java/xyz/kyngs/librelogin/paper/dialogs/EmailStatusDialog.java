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
import java.util.Map;

/**
 * Email status dialog for showing email binding status before password reset.
 * Shows masked email if bound, or warning if not bound.
 *
 * @author LibreLogin Contributors
 */
public class EmailStatusDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public EmailStatusDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates an email status dialog for the specified player.
     *
     * @param player the player
     * @param user   the user data
     * @return the created dialog
     */
    public Dialog create(Player player, User user) {
        return create(player, user, null, null);
    }

    /**
     * Creates an email status dialog with an optional error message.
     *
     * @param player the player
     * @param user   the user data
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, "success" for green, null for default
     * @return the created dialog
     */
    public Dialog create(Player player, User user, String errorMessage, String errorType) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_STATUS_TITLE.key());
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Create body content
        List<DialogBodyData> bodyList = new ArrayList<>();
        
        // Add error message at the top if present
        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodyList.add(new DialogBodyData(DialogContent.status(plugin, errorMessage, errorType)));
        }

        // Check if user has email
        boolean hasEmail = user.getEmail() != null && !user.getEmail().trim().isEmpty();
        
        if (hasEmail) {
            // User has email - show masked email and continue option
            String maskedEmail = maskEmail(user.getEmail());
            String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_STATUS_BODY_HAS_EMAIL.key())
                    .replace("%email%", maskedEmail);
            bodyList.add(new DialogBodyData(body));
        } else {
            // User has no email - show warning
            String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_EMAIL_STATUS_BODY_NO_EMAIL.key());
            bodyList.add(new DialogBodyData(body));
        }

        // No input fields for this dialog
        DialogInputs inputs = new DialogInputs(new ArrayList<>(), null, null);

        // Create buttons
        List<DialogButton> buttons = new ArrayList<>();

        if (hasEmail) {
            // Continue with password reset button
            String continueButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_CONTINUE_RESET.key());
            List<DialogButton.DialogAction> continueActions = new ArrayList<>();
            continueActions.add(new DialogButton.DialogAction("librelogin_continue_password_reset", "librelogin_continue_password_reset"));
            DialogButton continueButton = new DialogButton(
                    continueButtonText, 
                    null, 
                    continueActions, 
                    new HashMap<>(), // requirements
                    150 // width
            );
            buttons.add(continueButton);
        }

        // Back to login button (always present)
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
                "librelogin_email_status",
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

    /**
     * Masks an email address for display.
     * Format: first 3 chars + ** + last 3 chars before @ + @ + domain
     *
     * @param email the email to mask
     * @return the masked email
     */
    private String maskEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "***@***.***";
        }

        email = email.trim();
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***@***.***";
        }

        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        // If local part is too short, just mask it completely
        if (localPart.length() <= 6) {
            return "***@" + domain;
        }

        // Show first 3 and last 3 characters, mask the middle
        String firstThree = localPart.substring(0, 3);
        String lastThree = localPart.substring(localPart.length() - 3);
        
        return firstThree + "**" + lastThree + domain;
    }
}
