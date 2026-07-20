package xyz.kyngs.librelogin.paper.dialogs;

import com.fancyinnovations.fancydialogs.api.Dialog;
import com.fancyinnovations.fancydialogs.api.data.DialogBodyData;
import com.fancyinnovations.fancydialogs.api.data.DialogButton;
import com.fancyinnovations.fancydialogs.api.data.DialogData;
import com.fancyinnovations.fancydialogs.api.data.inputs.DialogInputs;
import com.fancyinnovations.fancydialogs.api.data.inputs.DialogTextField;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Password reset form displayed after a HuHoBot code has been verified. */
public class HuHoBotPasswordResetDialog {
    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public HuHoBotPasswordResetDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public Dialog create(Player player) {
        return create(player, null, null);
    }

    public Dialog create(Player player, String errorMessage, String errorType) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_HUHOBOT_RESET_TITLE.key());
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_HUHOBOT_RESET_BODY.key());
        List<DialogBodyData> bodies = new ArrayList<>();
        if (errorMessage != null && !errorMessage.isEmpty()) {
            bodies.add(new DialogBodyData(DialogContent.status(plugin, errorMessage, errorType)));
        }
        bodies.add(new DialogBodyData(body));

        List<DialogTextField> fields = new ArrayList<>();
        fields.add(new DialogTextField(
                "new_password",
                plugin.getMessages().getRawMessage(MessageKeys.DIALOG_HUHOBOT_RESET_PASSWORD_LABEL.key()),
                1, "", 128, 1, new HashMap<>(), null));
        fields.add(new DialogTextField(
                "new_password_confirm",
                plugin.getMessages().getRawMessage(MessageKeys.DIALOG_HUHOBOT_RESET_CONFIRM_LABEL.key()),
                2, "", 128, 1, new HashMap<>(), null));

        List<DialogButton> buttons = new ArrayList<>();
        List<DialogButton.DialogAction> resetActions = new ArrayList<>();
        resetActions.add(new DialogButton.DialogAction(
                "librelogin_huhobot_password_reset", "{new_password}:{new_password_confirm}"));
        buttons.add(new DialogButton(
                plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_HUHOBOT_RESET_SUBMIT.key()),
                null, resetActions, new HashMap<>(), 150));

        List<DialogButton.DialogAction> backActions = new ArrayList<>();
        backActions.add(new DialogButton.DialogAction("librelogin_back_to_login", "librelogin_back_to_login"));
        buttons.add(new DialogButton(
                plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_HUHOBOT_RESET_BACK.key()),
                null, backActions, new HashMap<>(), 150));

        return manager.getFancyDialogs().createDialog(new DialogData(
                "librelogin_huhobot_password_reset",
                title,
                plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE),
                bodies,
                new DialogInputs(fields, null, null),
                buttons,
                null,
                null));
    }
}
