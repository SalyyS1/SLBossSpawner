package dev.salyvn.slBossSpawner.config;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final SLBossSpawner plugin;
    private FileConfiguration config;

    private String prefix;
    private boolean debug;
    private String language;

    // Statistics config
    private boolean statisticsEnabled;
    private int statisticsFlushSeconds;
    private int leaderboardSize;

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

        // Statistics
        statisticsEnabled = config.getBoolean("statistics.enabled", true);
        statisticsFlushSeconds = clamp(config.getInt("statistics.flush-seconds", 2), 1, 60);
        // Keep the old cache limit as a harmless compatibility fallback while
        // ignoring all legacy database connection settings.
        leaderboardSize = clamp(config.getInt("statistics.leaderboard-size",
                config.getInt("statistics.cache.leaderboard-size", 100)), 1, 500);

        if (config.isConfigurationSection("statistics.database")) {
            plugin.getLogger().warning("The legacy statistics.database section is ignored. "
                    + "Statistics are now stored in statistics.yml.");
        }
    }

    public String getPrefix() { return prefix; }
    public boolean isDebug() { return debug; }
    public String getLanguage() { return language; }

    public boolean isStatisticsEnabled() { return statisticsEnabled; }
    public int getStatisticsFlushSeconds() { return statisticsFlushSeconds; }
    public int getLeaderboardSize() { return leaderboardSize; }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public void reload() {
        loadConfig();
    }
}
