package dev.fluffyworld.fluffyBossSpawner.boss;

import dev.fluffyworld.fluffyBossSpawner.FluffyBossSpawner;
import dev.fluffyworld.fluffyBossSpawner.config.ScheduleManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class BossScheduler {
    private final FluffyBossSpawner plugin;
    private final ScheduleManager scheduleManager;
    private BukkitTask checkTask;
    private final Map<String, BossInstance> bossInstances = new HashMap<>();
    
    public BossScheduler(FluffyBossSpawner plugin, ScheduleManager scheduleManager) {
        this.plugin = plugin;
        this.scheduleManager = scheduleManager;
        initializeBosses();
    }
    
    private void initializeBosses() {
        bossInstances.clear();
        
        Map<String, BossConfig> configs = scheduleManager.getAllBossConfigs();
        for (Map.Entry<String, BossConfig> entry : configs.entrySet()) {
            BossInstance instance = new BossInstance(plugin, entry.getValue());
            instance.calculateNextSpawn();
            bossInstances.put(entry.getKey(), instance);
        }
    }
    
    public void start() {
        stop();
        
        for (BossInstance instance : bossInstances.values()) {
            instance.calculateNextSpawn();
        }
        
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSpawns, 20L, 20L);
    }
    
    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        
        for (BossInstance instance : bossInstances.values()) {
            instance.stop();
        }
    }
    
    private void checkSpawns() {
        for (BossInstance instance : bossInstances.values()) {
            if (instance.shouldSpawn()) {
                instance.spawn();
                instance.calculateNextSpawn();
            }
        }
    }
    
    public void forceSpawn(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance != null) {
            instance.spawn();
        } else {
            plugin.getLogger().warning("Boss not found: " + bossId);
        }
    }
    
    public void forceSpawnAll() {
        for (BossInstance instance : bossInstances.values()) {
            instance.spawn();
        }
    }
    
    public void despawn(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance != null) {
            instance.despawn();
        }
    }
    
    public void despawnAll() {
        for (BossInstance instance : bossInstances.values()) {
            instance.despawn();
        }
    }
    
    public Map<String, BossInstance> getAllBossInstances() {
        return new HashMap<>(bossInstances);
    }
    
    public BossInstance getBossInstance(String bossId) {
        return bossInstances.get(bossId);
    }
    
    public boolean hasBoss(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        return instance != null && instance.isAlive();
    }
    
    public String getCurrentBossName(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance == null || !instance.isAlive()) return "None";
        return instance.getConfig().getMythicMobId();
    }
}
