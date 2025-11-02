/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.mail;

import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages HTML email templates for LibreLogin.
 * Handles template loading, placeholder replacement, and file management.
 * 
 * @author LibreLogin Contributors
 */
public class EmailTemplateManager {
    
    private final AuthenticLibreLogin<?, ?> plugin;
    private final Path templateDir;
    private final Map<String, String> templateCache;
    
    // Template file names
    public static final String PASSWORD_RESET_TEMPLATE = "password-reset.html";
    public static final String EMAIL_VERIFICATION_TEMPLATE = "email-verification.html";
    public static final String EMAIL_REGISTER_VERIFICATION_TEMPLATE = "email-register-verification.html";
    
    public EmailTemplateManager(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
        this.templateDir = plugin.getDataFolder().toPath().resolve("email-templates");
        this.templateCache = new HashMap<>();
        
        // Automatically release templates on initialization
        releaseEmailTemplates(false); // Don't overwrite existing files
        initializeTemplates();
    }
    
    /**
     * Initializes the template system (templates are already released in constructor).
     */
    private void initializeTemplates() {
        try {
            // Ensure template directory exists
            Files.createDirectories(templateDir);
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Email templates initialized in: " + templateDir.toString());
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to initialize email templates directory: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }
    
    
    /**
     * Creates a basic fallback template if the resource template is not found.
     * 
     * @param templateName the template file name
     * @param templatePath the path where to create the template
     */
    private void createFallbackTemplate(String templateName, Path templatePath) throws IOException {
        String fallbackContent = createBasicTemplateContent(templateName);
        Files.write(templatePath, fallbackContent.getBytes());
        plugin.getLogger().info("Created fallback email template: " + templateName);
    }
    
    /**
     * Creates basic HTML content for fallback templates.
     * 
     * @param templateName the template file name
     * @return basic HTML template content
     */
    private String createBasicTemplateContent(String templateName) {
        String title, content;
        
        switch (templateName) {
            case PASSWORD_RESET_TEMPLATE:
                title = "账户安全验证";
                content = """
                        <h2>账户安全验证</h2>
                        <p>您好，%name%！</p>
                        <p>我们收到了您重置账户访问凭证的请求。</p>
                        <div style="background: #f0f0f0; padding: 20px; margin: 20px 0; text-align: center;">
                            <h3>验证码：<span style="color: #007bff; font-size: 24px;">%code%</span></h3>
                        </div>
                        <p>请在10分钟内使用此验证码完成验证。</p>
                        <p>如果这不是您本人的操作，请忽略此邮件。</p>
                        """;
                break;
                
            case EMAIL_VERIFICATION_TEMPLATE:
                title = "邮箱验证";
                content = """
                        <h2>邮箱验证</h2>
                        <p>欢迎，%name%！</p>
                        <p>请使用以下验证码验证您的邮箱地址：</p>
                        <div style="background: #f0f0f0; padding: 20px; margin: 20px 0; text-align: center;">
                            <h3>验证码：<span style="color: #28a745; font-size: 24px;">%code%</span></h3>
                        </div>
                        <p>验证码有效期：%timeout%分钟</p>
                        """;
                break;
                
            case EMAIL_REGISTER_VERIFICATION_TEMPLATE:
                title = "账户注册验证";
                content = """
                        <h2>欢迎注册 %server%</h2>
                        <p>欢迎，%name%！</p>
                        <p>感谢您选择注册我们的游戏服务器！请使用以下验证码完成账户注册：</p>
                        <div style="background: #f0f0f0; padding: 20px; margin: 20px 0; text-align: center;">
                            <h3>验证码：<span style="color: #dc3545; font-size: 24px;">%code%</span></h3>
                        </div>
                        <p>验证码有效期：%timeout%分钟</p>
                        <p>请返回游戏客户端输入验证码完成注册。</p>
                        """;
                break;
                
            default:
                title = "邮件验证";
                content = """
                        <h2>邮件验证</h2>
                        <p>您好，%name%！</p>
                        <p>您的验证码是：<strong>%code%</strong></p>
                        <p>来自：%server%</p>
                        """;
        }
        
        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>%s</title>
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 20px; }
                        .container { max-width: 600px; margin: 0 auto; }
                        h2 { color: #333; }
                        .footer { margin-top: 30px; font-size: 12px; color: #666; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        %s
                        <div class="footer">
                            <p>此邮件由 %s 游戏服务器自动发送，请勿直接回复。</p>
                            <p>如有疑问，请联系服务器管理员。</p>
                        </div>
                    </div>
                </body>
                </html>
                """, title, content, "%server%");
    }
    
    /**
     * Loads a template from disk with caching.
     * 
     * @param templateName the name of the template file
     * @return the template content, or null if not found
     */
    private String loadTemplate(String templateName) {
        // Check cache first (only in production, not in debug mode)
        if (!plugin.getConfiguration().get(ConfigurationKeys.DEBUG) && templateCache.containsKey(templateName)) {
            return templateCache.get(templateName);
        }
        
        Path templatePath = templateDir.resolve(templateName);
        
        if (!Files.exists(templatePath)) {
            plugin.getLogger().warn("Email template not found: " + templateName);
            return null;
        }
        
        try {
            String content = Files.readString(templatePath);
            
            // Cache the template (unless in debug mode for easier development)
            if (!plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                templateCache.put(templateName, content);
            }
            
            return content;
        } catch (IOException e) {
            plugin.getLogger().error("Failed to read email template " + templateName + ": " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    /**
     * Processes a template by replacing placeholders with actual values.
     * 
     * @param template the template content
     * @param placeholders map of placeholder names to values
     * @return the processed template content
     */
    private String processTemplate(String template, Map<String, String> placeholders) {
        if (template == null) {
            return null;
        }
        
        String processed = template;
        
        // Replace all placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "%" + entry.getKey() + "%";
            String value = entry.getValue() != null ? entry.getValue() : "";
            processed = processed.replace(placeholder, value);
        }
        
        // Add automatic placeholders
        processed = processed.replace("%timestamp%", getCurrentTimestamp());
        
        return processed;
    }
    
    /**
     * Gets the current timestamp in a readable format.
     * 
     * @return formatted timestamp string
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss"));
    }
    
    /**
     * Generates HTML content for password reset email.
     * 
     * @param serverName the server name
     * @param playerName the player name
     * @param verificationCode the verification code
     * @param playerIP the player's IP address
     * @return the processed HTML content, or null if template not available
     */
    public String generatePasswordResetEmail(String serverName, String playerName, String verificationCode, String playerIP) {
        String template = loadTemplate(PASSWORD_RESET_TEMPLATE);
        if (template == null) {
            return null;
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("server", serverName);
        placeholders.put("name", playerName);
        placeholders.put("code", verificationCode);
        placeholders.put("ip", playerIP);
        
        return processTemplate(template, placeholders);
    }
    
    /**
     * Generates HTML content for email verification (email change).
     * 
     * @param serverName the server name
     * @param playerName the player name  
     * @param verificationCode the verification code
     * @return the processed HTML content, or null if template not available
     */
    public String generateEmailVerificationEmail(String serverName, String playerName, String verificationCode) {
        String template = loadTemplate(EMAIL_VERIFICATION_TEMPLATE);
        if (template == null) {
            return null;
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("server", serverName);
        placeholders.put("name", playerName);
        placeholders.put("code", verificationCode);
        placeholders.put("timeout", "10"); // Default timeout for email verification
        
        return processTemplate(template, placeholders);
    }
    
    /**
     * Generates HTML content for email registration verification.
     * 
     * @param serverName the server name
     * @param playerName the player name
     * @param verificationCode the verification code
     * @param timeoutMinutes the timeout in minutes
     * @return the processed HTML content, or null if template not available
     */
    public String generateEmailRegisterVerificationEmail(String serverName, String playerName, String verificationCode, int timeoutMinutes) {
        String template = loadTemplate(EMAIL_REGISTER_VERIFICATION_TEMPLATE);
        if (template == null) {
            return null;
        }
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("server", serverName);
        placeholders.put("name", playerName);
        placeholders.put("code", verificationCode);
        placeholders.put("timeout", String.valueOf(timeoutMinutes));
        
        return processTemplate(template, placeholders);
    }
    
    /**
     * Checks if HTML email templates are enabled and available.
     * 
     * @return true if HTML templates should be used
     */
    public boolean isHtmlTemplateEnabled() {
        return plugin.getConfiguration().get(ConfigurationKeys.MAIL_USE_HTML_TEMPLATES);
    }
    
    /**
     * Clears the template cache. Useful for development or when templates are updated.
     */
    public void clearCache() {
        templateCache.clear();
        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            plugin.getLogger().debug("Email template cache cleared");
        }
    }
    
    /**
     * Reloads all templates by clearing cache and re-releasing templates.
     */
    public void reloadTemplates() {
        clearCache();
        releaseEmailTemplates(false); // Release templates without overwriting existing ones
        plugin.getLogger().info("Email templates reloaded");
    }
    
    /**
     * Releases email templates to the plugin directory, similar to FancyDialogs template release.
     * This method can be called manually to ensure templates are properly extracted.
     * 
     * @param overwriteExisting whether to overwrite existing template files
     */
    public void releaseEmailTemplates(boolean overwriteExisting) {
        try {
            Files.createDirectories(templateDir);
            
            String[] templateFiles = {
                PASSWORD_RESET_TEMPLATE,
                EMAIL_VERIFICATION_TEMPLATE,
                EMAIL_REGISTER_VERIFICATION_TEMPLATE
            };
            
            int releasedCount = 0;
            int skippedCount = 0;
            
            for (String templateFile : templateFiles) {
                Path targetPath = templateDir.resolve(templateFile);
                
                // Check if file exists and whether to overwrite
                if (Files.exists(targetPath) && !overwriteExisting) {
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        plugin.getLogger().debug("Skipping existing email template: " + templateFile);
                    }
                    skippedCount++;
                    continue;
                }
                
                // Try to copy template from resources
                try (InputStream resourceStream = plugin.getResourceAsStream("email-templates/" + templateFile)) {
                    if (resourceStream == null) {
                        plugin.getLogger().warn("Email template resource not found: email-templates/" + templateFile);
                        // Create fallback template
                        createFallbackTemplate(templateFile, targetPath);
                        releasedCount++;
                        continue;
                    }
                    
                    Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    releasedCount++;
                    
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        plugin.getLogger().debug("Released email template: " + templateFile);
                    }
                } catch (IOException e) {
                    plugin.getLogger().error("Failed to release email template " + templateFile + ": " + e.getMessage());
                    if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                        e.printStackTrace();
                    }
                    // Try to create fallback as last resort
                    try {
                        createFallbackTemplate(templateFile, targetPath);
                        releasedCount++;
                    } catch (IOException fallbackError) {
                        plugin.getLogger().error("Failed to create fallback template " + templateFile + ": " + fallbackError.getMessage());
                    }
                }
            }
            
            if (releasedCount > 0) {
                plugin.getLogger().info("Released " + releasedCount + " email template(s) to " + templateDir.toAbsolutePath());
            }
            
            if (skippedCount > 0 && plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                plugin.getLogger().debug("Skipped " + skippedCount + " existing email template(s)");
            }
            
        } catch (IOException e) {
            plugin.getLogger().error("Failed to release email templates: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Gets the path to the templates directory for external access.
     * 
     * @return the path to the templates directory
     */
    public Path getTemplateDirectory() {
        return templateDir;
    }
    
    /**
     * Checks if a specific template file exists.
     * 
     * @param templateName the name of the template file
     * @return true if the template exists
     */
    public boolean templateExists(String templateName) {
        return Files.exists(templateDir.resolve(templateName));
    }
}
