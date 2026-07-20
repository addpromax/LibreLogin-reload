/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.dialogs;

import xyz.kyngs.librelogin.common.config.MessageKeys;
import xyz.kyngs.librelogin.paper.PaperLibreLogin;

/** Shared rendering helpers for configurable dialog content. */
final class DialogContent {

    private DialogContent() {
    }

    static String status(PaperLibreLogin plugin, String message, String type) {
        if (message == null || message.isEmpty()) return null;

        String key = switch (type == null ? "error" : type) {
            case "warning" -> MessageKeys.DIALOG_COMMON_WARNING_FORMAT.key();
            case "success" -> MessageKeys.DIALOG_COMMON_SUCCESS_FORMAT.key();
            default -> MessageKeys.DIALOG_COMMON_ERROR_FORMAT.key();
        };
        return plugin.getMessages().getRawMessage(key).replace("%message%", message);
    }
}
