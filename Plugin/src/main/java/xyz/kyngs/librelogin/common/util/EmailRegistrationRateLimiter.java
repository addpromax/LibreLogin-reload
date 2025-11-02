/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive rate limiter for email registration attempts.
 * Limits registration attempts based on IP address, UUID, and email address
 * to prevent abuse and spam.
 * 
 * @author LibreLogin Contributors
 */
public class EmailRegistrationRateLimiter {
    
    private final AuthenticLibreLogin<?, ?> plugin;
    private final RateLimiter<String> ipLimiter;
    private final RateLimiter<UUID> uuidLimiter;
    private final RateLimiter<String> emailLimiter;
    
    public EmailRegistrationRateLimiter(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
        
        // Get rate limit time from configuration (in minutes)
        int rateLimitMinutes = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_RATE_LIMIT_MINUTES);
        
        // Create rate limiters for different criteria
        this.ipLimiter = new RateLimiter<>(rateLimitMinutes, TimeUnit.MINUTES);
        this.uuidLimiter = new RateLimiter<>(rateLimitMinutes, TimeUnit.MINUTES);
        this.emailLimiter = new RateLimiter<>(rateLimitMinutes, TimeUnit.MINUTES);
    }
    
    /**
     * Gets the remaining time until the rate limit expires for a specific key.
     * 
     * @param limiter the rate limiter to check
     * @param key the key to check
     * @return remaining time in minutes, or 0 if not limited
     */
    private long getRemainingMinutes(RateLimiter<?> limiter, Object key) {
        // Unfortunately, Caffeine cache doesn't expose remaining TTL directly
        // We'll estimate based on configuration
        int rateLimitMinutes = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_RATE_LIMIT_MINUTES);
        return rateLimitMinutes; // This is an approximation
    }

    /**
     * Checks if email registration is rate limited for the given parameters.
     * 
     * @param playerUuid the UUID of the player attempting registration
     * @param ipAddress the IP address of the player
     * @param email the email address being used for registration
     * @return RateLimitResult containing the limit status and which limit was hit
     */
    public RateLimitResult checkRateLimit(UUID playerUuid, String ipAddress, String email) {
        // Normalize inputs
        if (ipAddress == null) ipAddress = "unknown";
        if (email != null) email = email.toLowerCase().trim();
        
        // Check IP rate limit
        if (ipLimiter.tryAndLimit(ipAddress)) {
            long remainingMinutes = getRemainingMinutes(ipLimiter, ipAddress);
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Email registration rate limited by IP: " + ipAddress + ", remaining: " + remainingMinutes + " minutes");
            }
            return RateLimitResult.limited(RateLimitType.IP, ipAddress, remainingMinutes);
        }
        
        // Check UUID rate limit
        if (uuidLimiter.tryAndLimit(playerUuid)) {
            long remainingMinutes = getRemainingMinutes(uuidLimiter, playerUuid);
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Email registration rate limited by UUID: " + playerUuid + ", remaining: " + remainingMinutes + " minutes");
            }
            return RateLimitResult.limited(RateLimitType.PLAYER, playerUuid.toString(), remainingMinutes);
        }
        
        // Check email rate limit (if email is provided)
        if (email != null && !email.isEmpty() && emailLimiter.tryAndLimit(email)) {
            long remainingMinutes = getRemainingMinutes(emailLimiter, email);
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Email registration rate limited by email: " + email + ", remaining: " + remainingMinutes + " minutes");
            }
            return RateLimitResult.limited(RateLimitType.EMAIL, email, remainingMinutes);
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * Result of a rate limit check.
     */
    public static class RateLimitResult {
        private final boolean limited;
        private final RateLimitType limitType;
        private final String limitedValue;
        private final long remainingMinutes;
        
        private RateLimitResult(boolean limited, RateLimitType limitType, String limitedValue, long remainingMinutes) {
            this.limited = limited;
            this.limitType = limitType;
            this.limitedValue = limitedValue;
            this.remainingMinutes = remainingMinutes;
        }
        
        public boolean isLimited() {
            return limited;
        }
        
        public RateLimitType getLimitType() {
            return limitType;
        }
        
        public String getLimitedValue() {
            return limitedValue;
        }
        
        public long getRemainingMinutes() {
            return remainingMinutes;
        }
        
        public static RateLimitResult allowed() {
            return new RateLimitResult(false, null, null, 0);
        }
        
        public static RateLimitResult limited(RateLimitType type, String value, long remainingMinutes) {
            return new RateLimitResult(true, type, value, remainingMinutes);
        }
    }
    
    /**
     * Type of rate limit that was triggered.
     */
    public enum RateLimitType {
        IP("error-email-register-rate-limit-ip"),
        PLAYER("error-email-register-rate-limit-player"), 
        EMAIL("error-email-register-rate-limit-email");
        
        private final String messageKey;
        
        RateLimitType(String messageKey) {
            this.messageKey = messageKey;
        }
        
        public String getMessageKey() {
            return messageKey;
        }
    }
}
