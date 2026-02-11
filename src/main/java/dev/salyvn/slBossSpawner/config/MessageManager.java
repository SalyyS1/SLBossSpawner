package dev.salyvn.slBossSpawner.config;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.utils.ColorUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MessageManager {
    private final SLBossSpawner plugin;
    private FileConfiguration messageConfig;
    private File messageFile;
    private final Map<String, String> messages = new HashMap<>();

    // Keys that should NOT have prefix prepended (decorative/structural)
    private static final Set<String> NO_PREFIX_KEYS = Set.of(
        "help-header", "help-footer", "help-usage",
        "info-header", "info-footer",
        "list-header", "list-footer", "ranking-header", "ranking-footer"
    );

    public MessageManager(SLBossSpawner plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        // Determine message file based on language config
        String lang = plugin.getConfigManager().getLanguage();
        String fileName = "en".equalsIgnoreCase(lang) ? "message.yml" : "message-" + lang + ".yml";

        // Ensure resource is extracted
        if (!new File(plugin.getDataFolder(), fileName).exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException e) {
                // Language file not bundled, fall back to English
                plugin.getLogger().warning("Message file '" + fileName + "' not found, using message.yml");
                fileName = "message.yml";
                if (!new File(plugin.getDataFolder(), fileName).exists()) {
                    plugin.saveResource(fileName, false);
                }
            }
        }

        messageFile = new File(plugin.getDataFolder(), fileName);
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        messages.clear();

        for (String key : messageConfig.getKeys(false)) {
            messages.put(key, messageConfig.getString(key, ""));
        }
    }

    /**
     * Get message with prefix prepended (unless it's a header/footer key).
     */
    public String getMessage(String key) {
        String raw = messages.getOrDefault(key, key);
        if (!NO_PREFIX_KEYS.contains(key)) {
            String prefix = plugin.getConfigManager().getPrefix();
            raw = prefix + raw;
        }
        return ColorUtils.colorize(raw);
    }

    /**
     * Get message with prefix + placeholder replacement.
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String raw = messages.getOrDefault(key, key);
        if (!NO_PREFIX_KEYS.contains(key)) {
            String prefix = plugin.getConfigManager().getPrefix();
            raw = prefix + raw;
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return ColorUtils.colorize(raw);
    }

    /**
     * Get raw message without prefix (for broadcast titles, subtitles, etc.).
     */
    public String getRawMessage(String key) {
        return ColorUtils.colorize(messages.getOrDefault(key, key));
    }

    /**
     * Get raw message with placeholder replacement, no prefix.
     */
    public String getRawMessage(String key, Map<String, String> placeholders) {
        String raw = messages.getOrDefault(key, key);

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }

        return ColorUtils.colorize(raw);
    }

    public void reload() {
        loadMessages();
    }
}
