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
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for displaying server announcements.
 * Players will see this dialog after successful authentication.
 *
 * @author LibreLogin Contributors
 */
public class AnnouncementDialog {

    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public AnnouncementDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    /**
     * Creates an announcement dialog for the player.
     *
     * @param player the player to show the announcement to
     * @return the created dialog
     * @throws Exception if dialog creation fails
     */
    public Dialog create(Player player) throws Exception {
        return create(player, null, null);
    }

    /**
     * Creates an announcement dialog with optional error message.
     *
     * @param player the player to show the announcement to
     * @param errorMessage optional error message to display
     * @param errorType the type of error (for coloring)
     * @return the created dialog
     * @throws Exception if dialog creation fails
     */
    public Dialog create(Player player, String errorMessage, String errorType) throws Exception {
        var fancyDialogs = manager.getFancyDialogs();
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Creating announcement dialog for player: " + player.getName());
        }

        // Get messages
        String buttonText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_CONFIRM_ANNOUNCEMENT.key());
        boolean canCloseWithEscape = plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE);

        // Get announcement manager
        var announcementManager = plugin.getAnnouncementManager();
        if (announcementManager == null) {
            throw new Exception("AnnouncementManager not available");
        }

        // announcement.yml supplies the data; announcement.conf controls how
        // that data is presented in the dialog.
        String announcementTitle = announcementManager.getAnnouncementTitle();
        String announcementContent = announcementManager.getAnnouncementContent();
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_ANNOUNCEMENT_TITLE.key())
                .replace("%title%", announcementTitle == null ? "" : announcementTitle);
        String bodyText = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_ANNOUNCEMENT_BODY.key())
                .replace("%content%", announcementContent == null
                        ? plugin.getMessages().getRawMessage(MessageKeys.DIALOG_COMMON_ANNOUNCEMENT_UNAVAILABLE.key())
                        : announcementContent);
        
        // Add error message if present
        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodyText = DialogContent.status(plugin, errorMessage, errorType) + "\n\n" + bodyText;
        }

        // Create body list
        List<DialogBodyData> bodyList = new ArrayList<>();
        bodyList.add(new DialogBodyData(bodyText));

        // Create buttons list
        List<DialogButton> buttons = new ArrayList<>();
        buttons.add(new DialogButton(
                buttonText, 
                null, 
                List.of(new DialogButton.DialogAction("librelogin_announcement_confirm", "librelogin_announcement_confirm")),
                new HashMap<>(), // requirements
                150 // width
        ));

        // Create dialog data
        DialogData dialogData = new DialogData(
            "librelogin_announcement",
            title,
            canCloseWithEscape,
            bodyList,
            new DialogInputs(List.of(), List.of(), List.of()),
            buttons,
            null, // exitAction
            null  // columns (use default)
        );

        // Create dialog
        Dialog dialog = fancyDialogs.createDialog(dialogData);
        
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Successfully created announcement dialog for player: " + player.getName());
        }
        
        return dialog;
    }
}
