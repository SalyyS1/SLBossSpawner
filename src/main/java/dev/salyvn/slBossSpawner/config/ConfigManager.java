package dev.salyvn.slBossSpawner.config;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final SLBossSpawner plugin;
    private FileConfiguration config;

    private String prefix;
    private boolean debug;
    private String language;

    public ConfigManager(SLBossSpawner plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        prefix = config.getString("prefix", "&#FF6B6B[&#FFD93DSLBossSpawner&#FF6B6B] ");
        debug = config.getBoolean("debug", false);
        language = config.getString("language", "en");
    }

    public String getPrefix() { return prefix; }
    public boolean isDebug() { return debug; }
    public String getLanguage() { return language; }

    public void reload() {
        loadConfig();
    }
}
