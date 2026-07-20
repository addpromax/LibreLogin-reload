/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import xyz.kyngs.librelogin.api.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads FancyDialogs text from {@code plugins/LibreLogin/dialogs/*.conf}.
 *
 * <p>The keys intentionally keep the old {@code dialog.*} names so existing
 * code and messages.conf installations remain compatible. Each file contains
 * only the portion belonging to one dialog (plus a shared buttons.conf file).
 * Missing values are written using the value from messages.conf as a default.</p>
 */
public final class DialogsConfiguration {

    private record Entry(String file, String path) {}

    private final File directory;
    private final Logger logger;
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, String> fallbackValues = new HashMap<>();

    public DialogsConfiguration(File dataFolder, Logger logger) {
        this.directory = new File(dataFolder, "dialogs");
        this.logger = logger;
    }

    /** Reloads all dialog files and creates missing files/default entries. */
    public void reload(Map<String, String> fallbacks) {
        values.clear();
        fallbackValues.clear();
        fallbackValues.putAll(fallbacks);

        if (!directory.exists() && !directory.mkdirs()) {
            logger.warn("Could not create LibreLogin dialogs configuration directory: " + directory);
            return;
        }

        Map<String, Map<String, String>> files = new HashMap<>();
        for (Map.Entry<String, String> fallback : fallbacks.entrySet()) {
            Entry entry = resolveEntry(fallback.getKey());
            if (entry == null) continue;
            files.computeIfAbsent(entry.file(), ignored -> new HashMap<>())
                    .put(fallback.getKey(), fallback.getValue());
        }
        files.forEach(this::loadFile);
    }

    public String get(String key) {
        return values.getOrDefault(key, fallbackValues.get(key));
    }

    private void loadFile(String fileName, Map<String, String> fallbacks) {
        File file = new File(directory, fileName);
        try {
            if (!file.exists() && !file.createNewFile()) {
                throw new IOException("Could not create file");
            }
            HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                    .file(file)
                    .defaultOptions(ConfigurationOptions.defaults().header(
                            "LibreLogin FancyDialogs content. MiniMessage and legacy & color codes are supported."))
                    .prettyPrinting(true)
                    .build();
            CommentedConfigurationNode root = loader.load();
            boolean changed = false;
            for (Map.Entry<String, String> fallback : fallbacks.entrySet()) {
                Entry entry = resolveEntry(fallback.getKey());
                if (entry == null) continue;
                var node = root.node((Object[]) entry.path().split("\\."));
                if (node.raw() == null && fallback.getValue() != null) {
                    node.set(fallback.getValue());
                    changed = true;
                }
                String value = node.getString(fallback.getValue());
                if (value != null) values.put(fallback.getKey(), value);
            }
            if (changed) loader.save(root);
        } catch (IOException | RuntimeException ex) {
            logger.warn("Could not load dialog configuration " + file.getName() + ": " + ex.getMessage());
            fallbacks.forEach((key, fallback) -> {
                if (fallback != null) values.put(key, fallback);
            });
        }
    }

    private static Entry resolveEntry(String key) {
        if (!key.startsWith("dialog.")) return null;
        String file;
        String prefix;
        if (key.startsWith("dialog.button.")) {
            prefix = "dialog.button.";
            file = "buttons.conf";
        } else {
            String[] parts = key.split("\\.", 3);
            if (parts.length < 3) return null;
            String dialog = parts[1];
            prefix = "dialog." + dialog + ".";
            file = switch (dialog) {
                case "register-confirmation" -> "register-confirmation.conf";
                case "password-reset" -> "password-reset.conf";
                case "email-status" -> "email-status.conf";
                case "email-register" -> "email-register.conf";
                case "email-verification" -> "email-verification.conf";
                case "email-input" -> "email-input.conf";
                case "huhobot-reset" -> "huhobot-reset.conf";
                case "2fa-setup" -> "two-factor-setup.conf";
                default -> dialog + ".conf";
            };
        }
        return new Entry(file, key.substring(prefix.length()));
    }
}
