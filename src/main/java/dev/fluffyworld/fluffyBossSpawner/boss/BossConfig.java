package dev.fluffyworld.fluffyBossSpawner.boss;

import org.bukkit.Location;

import java.time.ZoneId;
import java.util.List;

public class BossConfig {
    private final String id;
    private final String mythicMobId;
    private final int expireSeconds;
    private final boolean clearOnExpire;
    private final List<String> scheduleTimes;
    private final ZoneId timeZone;
    private final Location spawnLocation;
    private final int leashRadius;
    private final boolean loadChunks;
    
    public BossConfig(String id, String mythicMobId, int expireSeconds, boolean clearOnExpire,
                      List<String> scheduleTimes, ZoneId timeZone, Location spawnLocation,
                      int leashRadius, boolean loadChunks) {
        this.id = id;
        this.mythicMobId = mythicMobId;
        this.expireSeconds = expireSeconds;
        this.clearOnExpire = clearOnExpire;
        this.scheduleTimes = scheduleTimes;
        this.timeZone = timeZone;
        this.spawnLocation = spawnLocation;
        this.leashRadius = leashRadius;
        this.loadChunks = loadChunks;
    }
    
    public String getId() {
        return id;
    }
    
    public String getMythicMobId() {
        return mythicMobId;
    }
    
    public int getExpireSeconds() {
        return expireSeconds;
    }
    
    public boolean isClearOnExpire() {
        return clearOnExpire;
    }
    
    public List<String> getScheduleTimes() {
        return scheduleTimes;
    }
    
    public ZoneId getTimeZone() {
        return timeZone;
    }
    
    public Location getSpawnLocation() {
        return spawnLocation;
    }
    
    public int getLeashRadius() {
        return leashRadius;
    }
    
    public boolean isLoadChunks() {
        return loadChunks;
    }
}
