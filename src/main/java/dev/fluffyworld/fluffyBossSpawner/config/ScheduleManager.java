package dev.fluffyworld.fluffyBossSpawner.config;

import dev.fluffyworld.fluffyBossSpawner.FluffyBossSpawner;
import dev.fluffyworld.fluffyBossSpawner.boss.BossConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleManager {
    private final FluffyBossSpawner plugin;
    private FileConfiguration scheduleConfig;
    private File scheduleFile;
    private final Map<String, BossConfig> bossConfigs = new HashMap<>();
    
    public ScheduleManager(FluffyBossSpawner plugin) {
        this.plugin = plugin;
        loadSchedule();
    }
    
    public void loadSchedule() {
        scheduleFile = new File(plugin.getDataFolder(), "schedules.yml");
        
        if (!scheduleFile.exists()) {
            plugin.saveResource("schedules.yml", false);
        }
        
        scheduleConfig = YamlConfiguration.loadConfiguration(scheduleFile);
        bossConfigs.clear();
        
        for (String bossId : scheduleConfig.getKeys(false)) {
            ConfigurationSection bossSection = scheduleConfig.getConfigurationSection(bossId);
            if (bossSection == null) continue;
            
            String mythicMobId = bossSection.getString("mythicmob", "SkeletonKing");
            int expireSeconds = bossSection.getInt("expire", 3600);
            boolean clearOnExpire = bossSection.getBoolean("clearOnExpire", true);
            List<String> scheduleTimes = bossSection.getStringList("time-settings.time");
            
            String zoneStr = bossSection.getString("time-settings.zone", "Asia/Bangkok");
            ZoneId timeZone = ZoneId.of(zoneStr);
            
            String worldName = bossSection.getString("location.world", "world");
            World world = Bukkit.getWorld(worldName);
            Location spawnLocation = null;
            
            if (world != null) {
                double x = bossSection.getDouble("location.x");
                double y = bossSection.getDouble("location.y");
                double z = bossSection.getDouble("location.z");
                spawnLocation = new Location(world, x, y, z);
            }
            
            int leashRadius = bossSection.getInt("location.leashRadius", 10);
            boolean loadChunks = bossSection.getBoolean("loadChunks", true);
            
            BossConfig config = new BossConfig(
                bossId, mythicMobId, expireSeconds, clearOnExpire,
                scheduleTimes, timeZone, spawnLocation, leashRadius, loadChunks
            );
            
            bossConfigs.put(bossId, config);
        }
        
        plugin.getLogger().info("Loaded " + bossConfigs.size() + " boss configurations");
    }
    
    public Map<String, BossConfig> getAllBossConfigs() {
        return new HashMap<>(bossConfigs);
    }
    
    public BossConfig getBossConfig(String bossId) {
        return bossConfigs.get(bossId);
    }
    
    public void reload() {
        loadSchedule();
    }
}
