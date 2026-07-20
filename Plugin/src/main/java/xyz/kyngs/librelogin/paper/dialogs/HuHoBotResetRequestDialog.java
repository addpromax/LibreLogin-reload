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

/** Shows the one-time HuHoBot recovery code inside a FancyDialogs screen. */
public class HuHoBotResetRequestDialog {
    private final DialogManager manager;
    private final PaperLibreLogin plugin;

    public HuHoBotResetRequestDialog(DialogManager manager, PaperLibreLogin plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public Dialog create(Player player, String code, long expiresInMinutes) {
        String title = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_HUHOBOT_RESET_REQUEST_TITLE.key());
        String body = plugin.getMessages().getRawMessage(MessageKeys.DIALOG_HUHOBOT_RESET_INSTRUCTION.key())
                .replace("%code%", code)
                .replace("%minutes%", String.valueOf(expiresInMinutes));
        List<DialogButton> buttons = new ArrayList<>();
        buttons.add(new DialogButton(
                plugin.getMessages().getRawMessage(MessageKeys.DIALOG_BUTTON_HUHOBOT_REQUEST_BACK.key()),
                null,
                List.of(new DialogButton.DialogAction("librelogin_back_to_login", "librelogin_back_to_login")),
                new HashMap<>(),
                150));
        return manager.getFancyDialogs().createDialog(new DialogData(
                "librelogin_huhobot_reset_request",
                title,
                plugin.getConfiguration().get(ConfigurationKeys.FANCYDIALOGS_CLOSE_WITH_ESCAPE),
                List.of(new DialogBodyData(body)),
                new DialogInputs(new ArrayList<>(), null, null),
                buttons,
                null,
                null));
    }
}
