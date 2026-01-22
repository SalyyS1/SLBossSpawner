package dev.fluffyworld.fluffyBossSpawner.config;

import dev.fluffyworld.fluffyBossSpawner.FluffyBossSpawner;
import dev.fluffyworld.fluffyBossSpawner.utils.ColorUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private final FluffyBossSpawner plugin;
    private FileConfiguration messageConfig;
    private File messageFile;
    private final Map<String, String> messages = new HashMap<>();
    
    public MessageManager(FluffyBossSpawner plugin) {
        this.plugin = plugin;
        loadMessages();
    }
    
    public void loadMessages() {
        messageFile = new File(plugin.getDataFolder(), "message.yml");
        
        if (!messageFile.exists()) {
            plugin.saveResource("message.yml", false);
        }
        
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        messages.clear();
        
        for (String key : messageConfig.getKeys(false)) {
            messages.put(key, messageConfig.getString(key, ""));
        }
    }
    
    public String getMessage(String key) {
        return ColorUtils.colorize(messages.getOrDefault(key, key));
    }
    
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = messages.getOrDefault(key, key);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return ColorUtils.colorize(message);
    }
    
    public void reload() {
        loadMessages();
    }
}
