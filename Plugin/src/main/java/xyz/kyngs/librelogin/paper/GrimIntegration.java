/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;

/**
 * Keeps configuration-phase authentication inside Grim's transaction timeout.
 * Grim starts tracking after LOGIN_SUCCESS, but cannot exchange PLAY pongs while
 * Paper is still in the configuration phase.
 */
final class GrimIntegration {

    private static final int DEFAULT_GRIM_TIMEOUT_SECONDS = 60;
    private static final long MAX_SAFETY_MARGIN_MILLIS = 5_000L;
    private static final long MIN_SAFETY_MARGIN_MILLIS = 250L;
    private static final long MIN_TIMEOUT_MILLIS = 250L;

    private final PaperLibreLogin plugin;
    private final GrimAbstractAPI api;
    private boolean capLogged;

    private GrimIntegration(PaperLibreLogin plugin, GrimAbstractAPI api) {
        this.plugin = plugin;
        this.api = api;
    }

    static GrimIntegration create(PaperLibreLogin plugin) {
        try {
            GrimIntegration integration = new GrimIntegration(plugin, GrimAPIProvider.get());
            plugin.getLogger().info("GrimAC integration enabled");
            return integration;
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warn("Unable to initialize GrimAC integration: " + error.getMessage());
            return null;
        }
    }

    long limitConfigurationPhaseTimeout(long configuredMillis) {
        int grimTimeoutSeconds = Math.max(1, api.getConfigManager()
                .getIntElse("max-transaction-time", DEFAULT_GRIM_TIMEOUT_SECONDS));
        long grimTimeoutMillis = grimTimeoutSeconds * 1000L;
        long safetyMargin = Math.min(MAX_SAFETY_MARGIN_MILLIS,
                Math.max(MIN_SAFETY_MARGIN_MILLIS, grimTimeoutMillis / 10L));
        long safeTimeoutMillis = Math.max(MIN_TIMEOUT_MILLIS, grimTimeoutMillis - safetyMargin);
        long timeoutMillis = Math.min(configuredMillis, safeTimeoutMillis);

        if (timeoutMillis < configuredMillis && !capLogged) {
            capLogged = true;
            plugin.getLogger().warn("Configuration-phase authentication timeout was limited to "
                    + timeoutMillis + " ms to stay below GrimAC's " + grimTimeoutMillis
                    + " ms transaction timeout.");
        }
        return timeoutMillis;
    }
}
