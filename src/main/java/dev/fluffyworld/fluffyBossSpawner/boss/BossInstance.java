package dev.fluffyworld.fluffyBossSpawner.boss;

import dev.fluffyworld.fluffyBossSpawner.FluffyBossSpawner;
import dev.fluffyworld.fluffyBossSpawner.utils.TimeUtils;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZonedDateTime;
import java.util.List;

public class BossInstance {
    private final FluffyBossSpawner plugin;
    private final BossConfig config;
    private BukkitTask expireTask;
    private BukkitTask leashTask;
    
    private ActiveMob currentBoss;
    private ZonedDateTime nextSpawnTime;
    private ZonedDateTime expireTime;
    private Location spawnOrigin;
    
    public BossInstance(FluffyBossSpawner plugin, BossConfig config) {
        this.plugin = plugin;
        this.config = config;
    }
    
    public void calculateNextSpawn() {
        List<String> times = config.getScheduleTimes();
        if (times.isEmpty()) {
            nextSpawnTime = null;
            return;
        }
        
        ZonedDateTime earliest = null;
        
        for (String timeStr : times) {
            ZonedDateTime scheduled = TimeUtils.getNextScheduledTime(timeStr, config.getTimeZone());
            
            if (earliest == null || scheduled.isBefore(earliest)) {
                earliest = scheduled;
            }
        }
        
        nextSpawnTime = earliest;
    }
    
    public boolean shouldSpawn() {
        if (nextSpawnTime == null) return false;
        ZonedDateTime now = ZonedDateTime.now(config.getTimeZone());
        return !now.isBefore(nextSpawnTime);
    }
    
    public void spawn() {
        despawn();
        
        Location loc = config.getSpawnLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("Invalid spawn location for boss: " + config.getId());
            return;
        }
        
        if (config.isLoadChunks()) {
            Chunk chunk = loc.getChunk();
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
        }
        
        try {
            ActiveMob mob = MythicBukkit.inst().getMobManager().spawnMob(
                config.getMythicMobId(),
                loc
            );
            
            if (mob != null) {
                currentBoss = mob;
                spawnOrigin = loc.clone();
                expireTime = ZonedDateTime.now(config.getTimeZone())
                    .plusSeconds(config.getExpireSeconds());
                
                startExpireTimer();
                
                if (config.getLeashRadius() > 0) {
                    startLeashCheck();
                }
                
                plugin.getLogger().info("Boss spawned: " + config.getMythicMobId() + " (" + config.getId() + ")");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn boss " + config.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void startExpireTimer() {
        if (expireTask != null) {
            expireTask.cancel();
        }
        
        expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (config.isClearOnExpire()) {
                despawn();
                plugin.getLogger().info("Boss expired and removed: " + config.getId());
            }
            expireTime = null;
        }, config.getExpireSeconds() * 20L);
    }
    
    private void startLeashCheck() {
        if (leashTask != null) {
            leashTask.cancel();
        }
        
        leashTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isAlive() || spawnOrigin == null) {
                if (leashTask != null) {
                    leashTask.cancel();
                    leashTask = null;
                }
                return;
            }
            
            Entity entity = currentBoss.getEntity().getBukkitEntity();
            Location bossLoc = entity.getLocation();
            
            double distance = bossLoc.distance(spawnOrigin);
            int leashRadius = config.getLeashRadius();
            
            if (distance > leashRadius) {
                entity.teleport(spawnOrigin);
            }
        }, 20L, 20L);
    }
    
    public void despawn() {
        if (currentBoss != null && currentBoss.getEntity() != null) {
            Entity entity = currentBoss.getEntity().getBukkitEntity();
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            currentBoss = null;
        }
        
        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        
        if (leashTask != null) {
            leashTask.cancel();
            leashTask = null;
        }
        
        expireTime = null;
        spawnOrigin = null;
    }
    
    public void stop() {
        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        
        if (leashTask != null) {
            leashTask.cancel();
            leashTask = null;
        }
    }
    
    public boolean isAlive() {
        if (currentBoss == null) return false;
        
        Entity entity = currentBoss.getEntity().getBukkitEntity();
        return entity != null && entity.isValid() && !entity.isDead();
    }
    
    public BossConfig getConfig() {
        return config;
    }
    
    public ActiveMob getCurrentBoss() {
        return currentBoss;
    }
    
    public ZonedDateTime getNextSpawnTime() {
        return nextSpawnTime;
    }
    
    public ZonedDateTime getExpireTime() {
        return expireTime;
    }
    
    public long getSecondsUntilNext() {
        if (nextSpawnTime == null) return -1;
        return TimeUtils.getSecondsUntil(nextSpawnTime);
    }
    
    public String getNextSpawnFormatted() {
        if (nextSpawnTime == null) return "N/A";
        return TimeUtils.formatTime(nextSpawnTime);
    }
    
    public long getSecondsUntilExpire() {
        if (expireTime == null || !isAlive()) return -1;
        return TimeUtils.getSecondsUntil(expireTime);
    }
}
