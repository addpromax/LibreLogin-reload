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
import xyz.kyngs.librelogin.api.totp.TOTPData;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-Factor Authentication input dialog for FancyDialogs integration.
 * Only displays verification code input (QR code is shown via map).
 *
 * @author LibreLogin Contributors
 */
public class TwoFactorSetupDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public TwoFactorSetupDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates a 2FA verification dialog for the specified player.
     * This dialog only contains the input field for entering the 6-digit code.
     * The QR code is displayed separately via map item.
     *
     * @param player the player
     * @param user   the user data
     * @param totpData the TOTP data containing the secret
     * @return the created dialog
     */
    public Dialog create(Player player, User user, TOTPData totpData) {
        return create(player, user, totpData, null, null);
    }

    /**
     * Creates a 2FA verification dialog with an optional error message.
     *
     * @param player the player
     * @param user   the user data
     * @param totpData the TOTP data containing the secret
     * @param errorMessage the error message to display (null for no error)
     * @param errorType the type of error: "error" for red, "warning" for yellow, null for default
     * @return the created dialog
     */
    public Dialog create(Player player, User user, TOTPData totpData, String errorMessage, String errorType) {
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Creating 2FA verification dialog for player: " + player.getName());
            plugin.getLogger().debug("TOTP Secret: " + totpData.secret());
        }
        
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_2FA_SETUP_TITLE.key());
        String bodyTemplate = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_2FA_SETUP_BODY.key());
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Replace secret placeholder
        String body = bodyTemplate.replace("%secret%", totpData.secret());

        // Create body list - only show instructions, no QR code
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
        bodyList.add(new DialogBodyData("\n<gray>请使用Google Authenticator扫描地图上的二维码</gray>\n"));

        // Create input fields
        List<DialogTextField> textFields = new ArrayList<>();

        // Verification code field
        String codeLabel = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_2FA_SETUP_CODE_LABEL.key());
        DialogTextField codeField = new DialogTextField(
                "code",
                codeLabel,
                1,
                "",
                6,  // 6-digit code
                1
        );
        textFields.add(codeField);

        DialogInputs inputs = new DialogInputs(textFields, null, null);

        // Create buttons - MUST have at least one button
        List<DialogButton> buttons = new ArrayList<>();

        // Confirm button - ALWAYS add this
        String confirmButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_CONFIRM_2FA.key());
        List<DialogButton.DialogAction> confirmActions = new ArrayList<>();
        // Pass the secret along with the code for verification
        confirmActions.add(new DialogButton.DialogAction("librelogin_2fa_setup", totpData.secret() + ":{code}"));
        
        // Create button with proper style
        DialogButton confirmButton = new DialogButton(
                confirmButtonText,
                null,  // No icon
                confirmActions
        );
        buttons.add(confirmButton);
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Added confirm button with action: librelogin_2fa_setup");
        }

        // Rescan QR code button - always add this button
        String rescanButtonText = "重新扫码";  // TODO: Add to MessageKeys if needed
        List<DialogButton.DialogAction> rescanActions = new ArrayList<>();
        // 🔧 修复：直接传递secret作为data，action名称包含前缀用于路由识别
        rescanActions.add(new DialogButton.DialogAction("librelogin_2fa_rescan", "librelogin_2fa_rescan:" + totpData.secret()));
        DialogButton rescanButton = new DialogButton(rescanButtonText, null, rescanActions);
        buttons.add(rescanButton);
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Added rescan button");
        }

        // Skip button (only if player doesn't have force-2fa permission)
        if (!player.hasPermission("librelogin.force-2fa")) {
            String skipButtonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_SKIP_2FA.key());
            List<DialogButton.DialogAction> skipActions = new ArrayList<>();
            skipActions.add(new DialogButton.DialogAction("librelogin_2fa_skip", "librelogin_2fa_skip"));
            DialogButton skipButton = new DialogButton(skipButtonText, null, skipActions);
            buttons.add(skipButton);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Added skip button");
            }
        }

        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Total buttons created: " + buttons.size());
        }

        // Create dialog data
        DialogData data = new DialogData(
                "librelogin_2fa_setup",  // Dialog ID
                title,                    // Title
                canCloseWithEscape,       // Can close with ESC
                bodyList,                 // Body content
                inputs,                   // Input fields
                buttons                   // Action buttons
        );

        Dialog dialog = manager.getFancyDialogs().createDialog(data);
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Dialog created successfully for player: " + player.getName());
        }
        
        return dialog;
    }
}

