/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for email validation and domain checking.
 * Handles email format validation, domain whitelist/blacklist checking,
 * and email uniqueness verification.
 * 
 * @author LibreLogin Contributors
 */
public class EmailValidationUtil {
    
    private final AuthenticLibreLogin<?, ?> plugin;
    
    // More comprehensive email validation regex
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?@[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?\\.[a-zA-Z]{2,}$"
    );
    
    // Pattern for validating domain names
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?\\.[a-zA-Z]{2,}$"
    );
    
    public EmailValidationUtil(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Validates an email address with comprehensive checks.
     * 
     * @param email the email to validate
     * @return ValidationResult containing validation status and error message
     */
    public ValidationResult validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return ValidationResult.invalid("error-empty-input");
        }
        
        email = email.trim().toLowerCase();
        
        // Check email format
        if (!isValidEmailFormat(email)) {
            return ValidationResult.invalid("error-email-invalid-format");
        }
        
        // Check email length (RFC 5321 limits)
        if (email.length() > 254) {
            return ValidationResult.invalid("error-email-invalid-format");
        }
        
        // Extract domain
        String domain = extractDomain(email);
        if (domain == null) {
            return ValidationResult.invalid("error-email-invalid-format");
        }
        
        // Check domain blacklist
        if (isDomainBlacklisted(domain)) {
            return ValidationResult.invalid("error-email-domain-blocked");
        }
        
        // Check domain whitelist (if configured)
        if (!isDomainWhitelisted(domain)) {
            return ValidationResult.invalid("error-email-domain-not-allowed");
        }
        
        // Check if email is already in use (if enabled)
        boolean checkDuplicates = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_REGISTER_CHECK_DUPLICATES);
        if (checkDuplicates && isEmailAlreadyUsed(email)) {
            return ValidationResult.invalid("error-email-already-used");
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validates email format using regex pattern.
     * 
     * @param email the email to check
     * @return true if format is valid
     */
    public boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        email = email.trim();
        
        // Basic length and structure checks
        if (email.length() < 3 || email.length() > 254) {
            return false;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex >= email.length() - 1) {
            return false;
        }
        
        // Check for multiple @ symbols
        if (email.indexOf('@', atIndex + 1) != -1) {
            return false;
        }
        
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        
        // Validate local part (before @)
        if (localPart.length() > 64 || localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            return false;
        }
        
        // Validate domain part
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            return false;
        }
        
        // Full pattern match
        return EMAIL_PATTERN.matcher(email).matches();
    }
    
    /**
     * Extracts domain from email address.
     * 
     * @param email the email address
     * @return the domain part, or null if invalid
     */
    private String extractDomain(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex >= email.length() - 1) {
            return null;
        }
        
        return email.substring(atIndex + 1).toLowerCase();
    }
    
    /**
     * Checks if domain is in the blacklist.
     * 
     * @param domain the domain to check
     * @return true if domain is blacklisted
     */
    private boolean isDomainBlacklisted(String domain) {
        List<String> blacklist = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_DOMAIN_BLACKLIST);
        return blacklist.stream().anyMatch(blocked -> domain.equals(blocked.toLowerCase()));
    }
    
    /**
     * Checks if domain is allowed by whitelist.
     * 
     * @param domain the domain to check
     * @return true if domain is allowed (whitelist empty or domain in whitelist)
     */
    private boolean isDomainWhitelisted(String domain) {
        List<String> whitelist = plugin.getConfiguration().get(ConfigurationKeys.EMAIL_DOMAIN_WHITELIST);
        
        // If whitelist is empty, allow all domains (except blacklisted)
        if (whitelist.isEmpty()) {
            return true;
        }
        
        // Check if domain is in whitelist
        return whitelist.stream().anyMatch(allowed -> domain.equals(allowed.toLowerCase()));
    }
    
    /**
     * Checks if email is already used by another account.
     * 
     * @param email the email to check
     * @return true if email is already in use
     */
    private boolean isEmailAlreadyUsed(String email) {
        try {
            // Query database to check for existing email
            var users = plugin.getDatabaseProvider().getByEmail(email);
            return !users.isEmpty();
        } catch (Exception e) {
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Error checking email uniqueness: " + e.getMessage());
                e.printStackTrace();
            }
            // In case of error, allow registration to proceed
            return false;
        }
    }
    
    /**
     * Normalizes email address for storage and comparison.
     * 
     * @param email the email to normalize
     * @return normalized email address
     */
    public String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
    
    /**
     * Result of email validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessageKey;
        
        private ValidationResult(boolean valid, String errorMessageKey) {
            this.valid = valid;
            this.errorMessageKey = errorMessageKey;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessageKey() {
            return errorMessageKey;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorMessageKey) {
            return new ValidationResult(false, errorMessageKey);
        }
    }
}
