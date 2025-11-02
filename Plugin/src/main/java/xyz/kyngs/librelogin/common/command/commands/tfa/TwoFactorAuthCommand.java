/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.tfa;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import net.kyori.adventure.audience.Audience;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.Command;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

import java.util.concurrent.CompletionStage;

@CommandAlias("2fa|2fauth|2fauthcode")
public class TwoFactorAuthCommand<P> extends Command<P> {
    public TwoFactorAuthCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    @Default
    public CompletionStage<Void> onTwoFactorAuth(Audience sender, P player) {
        return runAsync(() -> {
            checkAuthorized(player);
            var user = getUser(player);
            var auth = plugin.getAuthorizationProvider();

            if (auth.isAwaiting2FA(player)) {
                throw new InvalidCommandArgument(getMessage("totp-show-info"));
            }

            // Check if VirtualMapProjector is available for map display
            boolean hasVirtualMapProjector = false;
            if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin && player instanceof org.bukkit.entity.Player bukkitPlayer) {
                var virtualMapProjector = paperPlugin.getVirtualMapProjector();
                hasVirtualMapProjector = virtualMapProjector != null && virtualMapProjector.canProject(bukkitPlayer);
            }
            
            if (!hasVirtualMapProjector) {
                throw new InvalidCommandArgument(getMessage("totp-wrong-version",
                        "%low%", "1.13",
                        "%high%", "1.21.1"
                ));
            }

            sender.sendMessage(getMessage("totp-generating"));

            var data = plugin.getTOTPProvider().generate(user);

            auth.beginTwoFactorAuth(user, player, data);

            // New flow: Virtual Map -> BossBar instruction -> Dialog input
            // Step 1: Display QR code using VirtualMapProjector
            if (plugin instanceof xyz.kyngs.librelogin.paper.PaperLibreLogin paperPlugin && player instanceof org.bukkit.entity.Player bukkitPlayer) {
                var virtualMapProjector = paperPlugin.getVirtualMapProjector();
                if (virtualMapProjector != null) {
                    plugin.cancelOnExit(plugin.delay(() -> {
                        virtualMapProjector.project(data.qr(), bukkitPlayer);
                        sender.sendMessage(getMessage("totp-show-info"));
                        
                        // Show BossBar instruction (同样的逻辑如重新扫码)
                        net.kyori.adventure.bossbar.BossBar bossBar = net.kyori.adventure.bossbar.BossBar.bossBar(
                            net.kyori.adventure.text.Component.text("§a§l设置双因素验证 §7| §e请扫描二维码，然后丢弃地图打开验证框"),
                            1.0f,
                            net.kyori.adventure.bossbar.BossBar.Color.GREEN,
                            net.kyori.adventure.bossbar.BossBar.Overlay.PROGRESS
                        );
                        var dialogManager = paperPlugin.getDialogManager();
                        if (dialogManager != null) {
                            dialogManager.showBossBar(bukkitPlayer, bossBar, 15000);
                        }
                        
                        // Step 2: Wait a bit, then show title instruction
                        plugin.delay(() -> {
                            bukkitPlayer.showTitle(
                                net.kyori.adventure.title.Title.title(
                                    net.kyori.adventure.text.Component.text("§a扫描完成"),
                                    net.kyori.adventure.text.Component.text("§7请丢弃地图，然后输入验证码"),
                                    net.kyori.adventure.title.Title.Times.times(
                                        java.time.Duration.ofMillis(500),
                                        java.time.Duration.ofMillis(5000),
                                        java.time.Duration.ofMillis(1000)
                                    )
                                )
                            );
                            
                            // Step 3: Check for DialogManager and show input dialog
                            if (dialogManager != null && dialogManager.isAvailable()) {
                                // Wait for player to drop the map (give them time to read title)
                                plugin.delay(() -> {
                                    dialogManager.showTwoFactorSetupDialog(bukkitPlayer, user, data);
                                }, 3000); // 3 second delay
                            }
                        }, 5000); // 5 seconds after QR code display
                    }, plugin.getConfiguration().get(ConfigurationKeys.TOTP_DELAY)), player);
                }
            }
        });
    }
}
