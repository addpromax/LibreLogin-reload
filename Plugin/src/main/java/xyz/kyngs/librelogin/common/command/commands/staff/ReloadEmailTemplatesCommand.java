/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.staff;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import net.kyori.adventure.audience.Audience;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.mail.AuthenticEMailHandler;

import java.util.concurrent.CompletionStage;

/**
 * Command to reload email templates.
 * Allows administrators to reload HTML email templates without restarting the server.
 * 
 * @author LibreLogin Contributors
 */
@CommandAlias("librelogin|ll")
public class ReloadEmailTemplatesCommand<P> extends StaffCommand<P> {

    public ReloadEmailTemplatesCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    @Default
    @CommandAlias("reloademails|reload-emails|reloademailtemplates|reload-email-templates")
    @CommandPermission("librelogin.admin.reload")
    @Description("Reloads HTML email templates from the plugin directory")
    public CompletionStage<Void> onReloadEmailTemplates(Audience audience) {
        return onReloadEmailTemplates(audience, false);
    }
    
    @CommandAlias("reloademails|reload-emails|reloademailtemplates|reload-email-templates")
    @CommandPermission("librelogin.admin.reload")
    @Description("Reloads HTML email templates with optional force release")
    public CompletionStage<Void> onReloadEmailTemplates(Audience audience, boolean forceRelease) {
        return runAsync(() -> {
            var emailHandler = plugin.getEmailHandler();
            
            if (emailHandler == null) {
                audience.sendMessage(getMessage("error-email-not-enabled"));
                return;
            }
            
            if (!(emailHandler instanceof AuthenticEMailHandler authenticHandler)) {
                audience.sendMessage(getMessage("error-email-handler-not-supported"));
                return;
            }
            
            try {
                var templateManager = authenticHandler.getTemplateManager();
                
                if (forceRelease) {
                    // Force release templates (overwrite existing)
                    templateManager.releaseEmailTemplates(true);
                    audience.sendMessage(getMessage("info-email-templates-force-released"));
                } else {
                    // Normal reload
                    templateManager.reloadTemplates();
                    audience.sendMessage(getMessage("info-email-templates-reloaded"));
                }
                
                // Log template directory path for admin reference
                String templatePath = templateManager.getTemplateDirectory().toString();
                audience.sendMessage(getMessage("info-email-templates-path", "%path%", templatePath));
                
            } catch (Exception e) {
                plugin.getLogger().error("Failed to reload email templates: " + e.getMessage());
                if (plugin.getConfiguration().get(xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG)) {
                    e.printStackTrace();
                }
                audience.sendMessage(getMessage("error-email-templates-reload-failed"));
            }
        });
    }
}
