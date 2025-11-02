/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.api.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages the announcement.yml file and handles MD5 hash verification.
 * This class creates, reads, and monitors changes to the announcement configuration.
 *
 * @author LibreLogin Contributors
 */
public class AnnouncementManager {

    private final AuthenticLibreLogin<?, ?> plugin;
    private final Logger logger;
    private final File dataFolder;
    private final File announcementFile;
    private String currentContent;
    private String currentHash;

    public AnnouncementManager(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFolder = plugin.getDataFolder();
        this.announcementFile = new File(dataFolder, "announcement.yml");
    }

    /**
     * Initializes the announcement manager and creates the announcement.yml file if needed.
     *
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            // Create data folder if it doesn't exist
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // Create default announcement file if it doesn't exist
            if (!announcementFile.exists()) {
                createDefaultAnnouncementFile();
            }

            // Load current content and calculate hash
            loadAnnouncementContent();
            
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                logger.debug("AnnouncementManager initialized. Current hash: " + currentHash);
            }

            return true;
        } catch (Exception e) {
            logger.error("Failed to initialize AnnouncementManager: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Creates the default announcement.yml file with template content.
     *
     * @throws IOException if file creation fails
     */
    private void createDefaultAnnouncementFile() throws IOException {
        if (!plugin.getConfiguration().get(ConfigurationKeys.ANNOUNCEMENT_AUTO_CREATE_FILE)) {
            logger.info("Auto-create announcement file is disabled. Skipping creation of announcement.yml");
            return;
        }

        logger.info("Creating default announcement.yml file...");

        // Create YAML structure with comments
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("# LibreLogin 服务器公告配置文件", null);
        config.put("# 修改此文件后使用 /librelogin reload 来重新加载公告", null);
        config.put("# 支持 MiniMessage 格式的富文本", null);
        config.put("", null); // Empty line
        
        Map<String, Object> announcement = new LinkedHashMap<>();
        announcement.put("enabled", true);
        announcement.put("title", "<gradient:gold:yellow><b>服务器公告</b></gradient>");
        announcement.put("content", "<gradient:gold:yellow><bold>欢迎来到服务器!</bold></gradient>\n\n<white>这里是服务器公告内容。\n您可以在 announcement.yml 文件中修改此内容。\n\n修改后使用 /librelogin reload 重新加载。</white>");
        
        config.put("announcement", announcement);

        // Write YAML file with proper formatting
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setAllowUnicode(true);

        Yaml yaml = new Yaml(options);
        
        try (FileWriter writer = new FileWriter(announcementFile, StandardCharsets.UTF_8)) {
            writer.write("# LibreLogin 服务器公告配置文件\n");
            writer.write("# 修改此文件后使用 /librelogin reload 来重新加载公告\n");
            writer.write("# 支持 MiniMessage 格式的富文本\n\n");
            
            yaml.dump(announcement, writer);
        }

        logger.info("Default announcement.yml file created successfully!");
    }

    /**
     * Loads the announcement content from the YAML file and calculates its MD5 hash.
     *
     * @throws IOException if file reading fails
     */
    private void loadAnnouncementContent() throws IOException {
        if (!announcementFile.exists()) {
            currentContent = "";
            currentHash = calculateMD5("");
            return;
        }

        // Read the entire file content
        byte[] fileBytes = Files.readAllBytes(announcementFile.toPath());
        currentContent = new String(fileBytes, StandardCharsets.UTF_8);
        currentHash = calculateMD5(currentContent);

        if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
            logger.debug("Loaded announcement content, MD5: " + currentHash);
        }
    }

    /**
     * Calculates the MD5 hash of the given content.
     *
     * @param content the content to hash
     * @return the MD5 hash as a hex string
     */
    private String calculateMD5(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5 algorithm not available: " + e.getMessage());
            return String.valueOf(content.hashCode()); // Fallback to hashCode
        }
    }

    /**
     * Reloads the announcement configuration and recalculates the hash.
     * This should be called when the server is reloaded.
     *
     * @return true if the announcement content has changed
     */
    public boolean reload() {
        try {
            String previousHash = currentHash;
            loadAnnouncementContent();
            
            boolean hasChanged = !currentHash.equals(previousHash);
            
            if (hasChanged) {
                logger.info("Announcement content has changed. New hash: " + currentHash);
            } else {
                if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                    logger.debug("Announcement content unchanged. Hash: " + currentHash);
                }
            }
            
            return hasChanged;
        } catch (Exception e) {
            logger.error("Failed to reload announcement: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Gets the current announcement content hash.
     *
     * @return the current MD5 hash of the announcement content
     */
    public String getCurrentHash() {
        return currentHash;
    }

    /**
     * Gets the parsed announcement configuration from the YAML file.
     *
     * @return the announcement configuration map, or null if parsing fails
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAnnouncementConfig() {
        if (!announcementFile.exists()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(announcementFile)) {
            Yaml yaml = new Yaml();
            return (Map<String, Object>) yaml.load(fis);
        } catch (Exception e) {
            logger.error("Failed to parse announcement.yml: " + e.getMessage());
            if (plugin.getConfiguration().get(ConfigurationKeys.DEBUG)) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Gets the announcement content from the configuration.
     *
     * @return the announcement content, or null if not available
     */
    public String getAnnouncementContent() {
        Map<String, Object> config = getAnnouncementConfig();
        if (config == null) {
            return null;
        }

        return (String) config.get("content");
    }

    /**
     * Gets the announcement title from the configuration.
     *
     * @return the announcement title, or null if not available
     */
    public String getAnnouncementTitle() {
        Map<String, Object> config = getAnnouncementConfig();
        if (config == null) {
            return null;
        }

        return (String) config.get("title");
    }

    /**
     * Checks if announcements are enabled in the configuration.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isAnnouncementEnabled() {
        Map<String, Object> config = getAnnouncementConfig();
        if (config == null) {
            return false;
        }

        Boolean enabled = (Boolean) config.get("enabled");
        return enabled != null && enabled;
    }

    /**
     * Gets the announcement file for external access.
     *
     * @return the announcement.yml file
     */
    public File getAnnouncementFile() {
        return announcementFile;
    }
}
