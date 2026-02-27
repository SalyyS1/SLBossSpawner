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
    private String databaseType;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private String tablePrefix;
    private int mysqlPoolSize;
    private int cacheRefreshSeconds;
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
        databaseType = config.getString("statistics.database.type", "sqlite");
        mysqlHost = config.getString("statistics.database.mysql.host", "localhost");
        mysqlPort = config.getInt("statistics.database.mysql.port", 3306);
        mysqlDatabase = config.getString("statistics.database.mysql.database", "minecraft");
        mysqlUsername = config.getString("statistics.database.mysql.username", "root");
        mysqlPassword = config.getString("statistics.database.mysql.password", "");
        tablePrefix = config.getString("statistics.database.table-prefix", "slbs_");
        mysqlPoolSize = config.getInt("statistics.database.mysql.pool-size", 5);
        cacheRefreshSeconds = config.getInt("statistics.cache.refresh-seconds", 60);
        leaderboardSize = config.getInt("statistics.cache.leaderboard-size", 100);
    }

    public String getPrefix() { return prefix; }
    public boolean isDebug() { return debug; }
    public String getLanguage() { return language; }

    public boolean isStatisticsEnabled() { return statisticsEnabled; }
    public String getDatabaseType() { return databaseType; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public String getTablePrefix() { return tablePrefix; }
    public int getMysqlPoolSize() { return mysqlPoolSize; }
    public int getCacheRefreshSeconds() { return cacheRefreshSeconds; }
    public int getLeaderboardSize() { return leaderboardSize; }

    public void reload() {
        loadConfig();
    }
}
