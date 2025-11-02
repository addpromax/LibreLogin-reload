/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.mail;

import org.apache.commons.mail.EmailConstants;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import xyz.kyngs.librelogin.api.mail.EmailHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

public class AuthenticEMailHandler implements EmailHandler {

    private final AuthenticLibreLogin<?, ?> plugin;
    private final EmailTemplateManager templateManager;

    public AuthenticEMailHandler(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
        this.templateManager = new EmailTemplateManager(plugin);
    }

    @Override
    public void sendEmail(String email, String subject, String content) {
        boolean debug = plugin.getConfiguration().get(ConfigurationKeys.DEBUG);
        
        if (debug) {
            plugin.getLogger().debug("=== Email Sending Debug Process Start ===");
            plugin.getLogger().debug("Target Email: " + email);
            plugin.getLogger().debug("Subject: " + subject);
            plugin.getLogger().debug("Content Length: " + (content != null ? content.length() : 0) + " characters");
        }
        
        try {
            var config = plugin.getConfiguration();
            var port = config.get(ConfigurationKeys.MAIL_PORT);
            var host = config.get(ConfigurationKeys.MAIL_HOST);
            var username = config.get(ConfigurationKeys.MAIL_USERNAME);
            var fromEmail = config.get(ConfigurationKeys.MAIL_EMAIL);
            var sender = config.get(ConfigurationKeys.MAIL_SENDER);

            if (debug) {
                plugin.getLogger().debug("=== SMTP Configuration ===");
                plugin.getLogger().debug("Host: " + host);
                plugin.getLogger().debug("Port: " + port);
                plugin.getLogger().debug("Username: " + username);
                plugin.getLogger().debug("From Email: " + fromEmail);
                plugin.getLogger().debug("Sender Name: " + sender);
            }

            var mail = new HtmlEmail();

            if (debug) {
                plugin.getLogger().debug("=== Setting Email Properties ===");
            }
            
            mail.setCharset(EmailConstants.UTF_8);
            if (debug) plugin.getLogger().debug("Charset set to UTF-8");
            
            mail.setHostName(host);
            if (debug) plugin.getLogger().debug("Hostname set to: " + host);
            
            mail.setSmtpPort(port);
            if (debug) plugin.getLogger().debug("SMTP port set to: " + port);
            
            mail.setSubject(subject);
            if (debug) plugin.getLogger().debug("Subject set successfully");
            
            mail.setAuthentication(username, config.get(ConfigurationKeys.MAIL_PASSWORD));
            if (debug) plugin.getLogger().debug("Authentication configured for user: " + username);
            
            mail.addTo(email);
            if (debug) plugin.getLogger().debug("Recipient added: " + email);
            
            mail.setFrom(fromEmail, sender);
            if (debug) plugin.getLogger().debug("From address set: " + fromEmail + " (" + sender + ")");

            // Get SSL/TLS configuration from config
            boolean sslEnabled = config.get(ConfigurationKeys.MAIL_SSL_ENABLED);
            boolean startTlsEnabled = config.get(ConfigurationKeys.MAIL_STARTTLS_ENABLED);
            boolean startTlsRequired = config.get(ConfigurationKeys.MAIL_STARTTLS_REQUIRED);
            boolean sslCheckServerIdentity = config.get(ConfigurationKeys.MAIL_SSL_CHECK_SERVER_IDENTITY);
            int connectionTimeout = config.get(ConfigurationKeys.MAIL_CONNECTION_TIMEOUT);
            int readTimeout = config.get(ConfigurationKeys.MAIL_READ_TIMEOUT);
            
            if (debug) {
                plugin.getLogger().debug("=== SSL/TLS Configuration ===");
                plugin.getLogger().debug("Port: " + port);
                plugin.getLogger().debug("SSL Enabled: " + sslEnabled);
                plugin.getLogger().debug("StartTLS Enabled: " + startTlsEnabled);
                plugin.getLogger().debug("StartTLS Required: " + startTlsRequired);
                plugin.getLogger().debug("SSL Check Server Identity: " + sslCheckServerIdentity);
                plugin.getLogger().debug("Connection Timeout: " + connectionTimeout + "ms");
                plugin.getLogger().debug("Read Timeout: " + readTimeout + "ms");
            }
            
            // Configure SSL/TLS based on configuration
            if (sslEnabled) {
                mail.setSslSmtpPort(String.valueOf(port));
                mail.setSSLOnConnect(true);
                if (debug) plugin.getLogger().debug("SSL connection enabled");
            }
            
            if (startTlsEnabled) {
                mail.setStartTLSEnabled(true);
                mail.setStartTLSRequired(startTlsRequired);
                if (debug) plugin.getLogger().debug("StartTLS enabled, required: " + startTlsRequired);
            }
            
            if (sslCheckServerIdentity) {
                mail.setSSLCheckServerIdentity(true);
                if (debug) plugin.getLogger().debug("SSL server identity check enabled");
            }
            
            // Set timeouts to prevent hanging connections
            if (connectionTimeout > 0) {
                mail.setSocketConnectionTimeout(connectionTimeout);
                if (debug) plugin.getLogger().debug("Connection timeout set to: " + connectionTimeout + "ms");
            }
            
            if (readTimeout > 0) {
                mail.setSocketTimeout(readTimeout);
                if (debug) plugin.getLogger().debug("Read timeout set to: " + readTimeout + "ms");
            }

            if (debug) plugin.getLogger().debug("Setting HTML content...");
            mail.setHtmlMsg(content);
            if (debug) plugin.getLogger().debug("HTML content set successfully");
            
            if (debug) plugin.getLogger().debug("Setting context class loader...");
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            if (debug) plugin.getLogger().debug("Context class loader set");
            
            if (debug) plugin.getLogger().debug("=== Attempting to send email ===");
            long startTime = System.currentTimeMillis();
            
            mail.send();
            
            long endTime = System.currentTimeMillis();
            if (debug) {
                plugin.getLogger().debug("Email sent successfully!");
                plugin.getLogger().debug("Send time: " + (endTime - startTime) + "ms");
                plugin.getLogger().debug("=== Email Sending Debug Process Complete ===");
            }
            
        } catch (EmailException e) {
            if (debug) {
                plugin.getLogger().debug("=== Email Sending Failed ===");
                plugin.getLogger().debug("EmailException occurred during email sending:");
                plugin.getLogger().debug("Exception message: " + e.getMessage());
                plugin.getLogger().debug("Exception class: " + e.getClass().getSimpleName());
                
                // Check for nested causes
                Throwable cause = e.getCause();
                int level = 1;
                while (cause != null && level <= 3) {
                    plugin.getLogger().debug("Cause level " + level + ": " + cause.getClass().getSimpleName() + " - " + cause.getMessage());
                    cause = cause.getCause();
                    level++;
                }
                
                plugin.getLogger().debug("Full stack trace will be shown in main error log");
                plugin.getLogger().debug("=== Email Sending Debug Process Failed ===");
            }
            throw new RuntimeException(e);
        }

    }

    @Override
    public void sendTestMail(String email) {
        sendEmail(email, "LibreLogin test mail", """
                Congratulations! You have successfully configured email sending in LibreLogin!<br>
                Now, your users can reset their passwords.<br>
                <i>If you have no idea what this means, block the sender.</i>
                """);
    }

    @Override
    public void sendPasswordResetMail(String email, String token, String username, String ip) {
        String subject = plugin.getMessages().getRawMessage("email-password-reset-subject")
                .replace("%server%", plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER));
        
        String content;
        
        // Try to use HTML template first if enabled
        if (templateManager.isHtmlTemplateEnabled()) {
            String htmlContent = templateManager.generatePasswordResetEmail(
                    plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER),
                    username,
                    token,
                    ip
            );
            
            if (htmlContent != null) {
                content = htmlContent;
            } else {
                // Fallback to plain text if HTML template fails
                plugin.getLogger().warn("HTML template for password reset failed, falling back to plain text");
                content = getPlainTextPasswordResetContent(token, username, ip);
            }
        } else {
            // Use plain text content
            content = getPlainTextPasswordResetContent(token, username, ip);
        }
        
        sendEmail(email, subject, content);
    }
    
    /**
     * Gets plain text content for password reset email.
     */
    private String getPlainTextPasswordResetContent(String token, String username, String ip) {
        return plugin.getMessages().getRawMessage("email-password-reset-content")
                .replace("%server%", plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER))
                .replace("%code%", token)
                .replace("%ip%", ip)
                .replace("%name%", username);
    }

    @Override
    public void sendVerificationMail(String email, String token, String username) {
        String subject = plugin.getMessages().getRawMessage("email-verification-subject")
                .replace("%server%", plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER));
        
        String content;
        
        // Try to use HTML template first if enabled
        if (templateManager.isHtmlTemplateEnabled()) {
            String htmlContent = templateManager.generateEmailVerificationEmail(
                    plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER),
                    username,
                    token
            );
            
            if (htmlContent != null) {
                content = htmlContent;
            } else {
                // Fallback to plain text if HTML template fails
                plugin.getLogger().warn("HTML template for email verification failed, falling back to plain text");
                content = getPlainTextEmailVerificationContent(token, username);
            }
        } else {
            // Use plain text content
            content = getPlainTextEmailVerificationContent(token, username);
        }
        
        sendEmail(email, subject, content);
    }
    
    /**
     * Gets plain text content for email verification email.
     */
    private String getPlainTextEmailVerificationContent(String token, String username) {
        return plugin.getMessages().getRawMessage("email-verification-content")
                .replace("%name%", username)
                .replace("%server%", plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER))
                .replace("%code%", token);
    }
    
    /**
     * Sends email registration verification mail with HTML template support.
     * 
     * @param email the recipient email address
     * @param token the verification code
     * @param username the player username
     * @param timeoutMinutes timeout in minutes for the verification code
     */
    public void sendEmailRegisterVerificationMail(String email, String token, String username, int timeoutMinutes) {
        boolean debug = plugin.getConfiguration().get(ConfigurationKeys.DEBUG);
        
        if (debug) {
            plugin.getLogger().debug("=== Email Registration Verification Mail Debug ===");
            plugin.getLogger().debug("Email: " + email);
            plugin.getLogger().debug("Token: " + token);
            plugin.getLogger().debug("Username: " + username);
            plugin.getLogger().debug("Timeout: " + timeoutMinutes + " minutes");
        }
        
        String subject = plugin.getMessages().getRawMessage("email-register-verification-subject")
                .replace("%server%", plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER));
        
        if (debug) {
            plugin.getLogger().debug("Subject generated: " + subject);
        }
        
        String content;
        
        // Try to use HTML template first if enabled
        boolean htmlTemplateEnabled = templateManager.isHtmlTemplateEnabled();
        if (debug) {
            plugin.getLogger().debug("HTML template enabled: " + htmlTemplateEnabled);
        }
        
        if (htmlTemplateEnabled) {
            if (debug) {
                plugin.getLogger().debug("Attempting to generate HTML content using template...");
            }
            
            String htmlContent = templateManager.generateEmailRegisterVerificationEmail(
                    plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER),
                    username,
                    token,
                    timeoutMinutes
            );
            
            if (htmlContent != null) {
                content = htmlContent;
                if (debug) {
                    plugin.getLogger().debug("HTML template generated successfully, content length: " + htmlContent.length());
                }
            } else {
                // Fallback to plain text if HTML template fails
                plugin.getLogger().warn("HTML template for email registration verification failed, falling back to plain text");
                content = getPlainTextEmailRegisterVerificationContent(token, username, timeoutMinutes);
                if (debug) {
                    plugin.getLogger().debug("Using plain text fallback, content length: " + content.length());
                }
            }
        } else {
            // Use plain text content
            if (debug) {
                plugin.getLogger().debug("Using plain text content (HTML templates disabled)");
            }
            content = getPlainTextEmailRegisterVerificationContent(token, username, timeoutMinutes);
            if (debug) {
                plugin.getLogger().debug("Plain text content generated, length: " + content.length());
            }
        }
        
        if (debug) {
            plugin.getLogger().debug("Calling sendEmail method...");
        }
        
        sendEmail(email, subject, content);
        
        if (debug) {
            plugin.getLogger().debug("=== Email Registration Verification Mail Debug End ===");
        }
    }
    
    /**
     * Gets plain text content for email registration verification email.
     */
    private String getPlainTextEmailRegisterVerificationContent(String token, String username, int timeoutMinutes) {
        return plugin.getMessages().getRawMessage("email-register-verification-content")
                .replace("%name%", username)
                .replace("%server%", plugin.getConfiguration().get(ConfigurationKeys.MAIL_SENDER))
                .replace("%code%", token)
                .replace("%timeout%", String.valueOf(timeoutMinutes));
    }
    
    /**
     * Gets the template manager for external access.
     * 
     * @return the email template manager
     */
    public EmailTemplateManager getTemplateManager() {
        return templateManager;
    }
}
