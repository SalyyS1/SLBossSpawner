package dev.fluffyworld.fluffyBossSpawner.config;

import dev.fluffyworld.fluffyBossSpawner.FluffyBossSpawner;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final FluffyBossSpawner plugin;
    private FileConfiguration config;
    
    private String prefix;
    private boolean debug;
    
    public ConfigManager(FluffyBossSpawner plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        prefix = config.getString("prefix", "&#FF6B6B[&#FFD93DFluffyBossSpawner&#FF6B6B] ");
        debug = config.getBoolean("debug", false);
    }
    
    public String getPrefix() {
        return prefix;
    }
    
    public boolean isDebug() {
        return debug;
    }
    
    public void reload() {
        loadConfig();
    }
}
