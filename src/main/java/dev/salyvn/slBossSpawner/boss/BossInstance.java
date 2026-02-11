package dev.salyvn.slBossSpawner.boss;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.utils.TimeUtils;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public class BossInstance {
    private final SLBossSpawner plugin;
    private final BossConfig config;
    private BukkitTask expireTask;
    private BukkitTask leashTask;

    private ActiveMob currentBoss;
    private UUID entityUuid;
    private ZonedDateTime nextSpawnTime;
    private ZonedDateTime expireTime;
    private Location spawnOrigin;
    private long spawnEpoch;
    private long expireEpoch;

    public BossInstance(SLBossSpawner plugin, BossConfig config) {
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
        if (nextSpawnTime == null || isAlive()) return false;
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
                config.getMythicMobId(), loc
            );

            if (mob != null) {
                currentBoss = mob;
                spawnOrigin = loc.clone();

                Entity entity = BossEntityHelper.getBukkitEntity(mob);
                entityUuid = (entity != null) ? entity.getUniqueId() : null;

                long now = Instant.now().getEpochSecond();
                spawnEpoch = now;
                expireEpoch = now + config.getExpireSeconds();
                expireTime = ZonedDateTime.now(config.getTimeZone())
                    .plusSeconds(config.getExpireSeconds());

                startExpireTimer(config.getExpireSeconds());

                if (config.getLeashRadius() > 0) {
                    startLeashCheck();
                }

                plugin.debug("Boss spawned: " + config.getMythicMobId() + " (" + config.getId() + ") UUID=" + entityUuid);
                plugin.getLogger().info("Boss spawned: " + config.getMythicMobId() + " (" + config.getId() + ")");

                // Start tracking for this boss (damage, tank, support)
                if (entityUuid != null) {
                    plugin.getDamageTracker().startTracking(entityUuid);
                    plugin.getTankTracker().startTracking(entityUuid);
                    plugin.getSupportTracker().startTracking(entityUuid);
                }

                // Save state
                plugin.getBossStateManager().saveSingle(this);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn boss " + config.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Restore a boss from persisted state after server restart.
     */
    public boolean restore(UUID savedUuid, long savedSpawnEpoch, long savedExpireEpoch) {
        long now = Instant.now().getEpochSecond();
        if (now >= savedExpireEpoch) {
            plugin.debug("Boss " + config.getId() + " expired during downtime, skipping restore");
            return false;
        }

        // Find the ActiveMob by entity UUID from MythicMobs registry
        ActiveMob found = null;
        for (ActiveMob am : MythicBukkit.inst().getMobManager().getActiveMobs()) {
            Entity e = BossEntityHelper.getBukkitEntity(am);
            if (e != null && e.getUniqueId().equals(savedUuid)) {
                found = am;
                break;
            }
        }

        if (found == null) {
            plugin.debug("Boss " + config.getId() + " entity not found for UUID " + savedUuid + ", skipping restore");
            return false;
        }

        Entity entity = BossEntityHelper.getBukkitEntity(found);
        if (entity == null) {
            return false;
        }

        currentBoss = found;
        entityUuid = savedUuid;
        spawnEpoch = savedSpawnEpoch;
        expireEpoch = savedExpireEpoch;
        spawnOrigin = entity.getLocation().clone();
        expireTime = ZonedDateTime.now(config.getTimeZone())
            .plusSeconds(savedExpireEpoch - now);

        long remaining = savedExpireEpoch - now;
        startExpireTimer(remaining);

        if (config.getLeashRadius() > 0) {
            startLeashCheck();
        }

        plugin.getLogger().info("Boss restored: " + config.getId() + " (remaining: " + remaining + "s)");

        // Start tracking for restored boss (damage, tank, support)
        plugin.getDamageTracker().startTracking(entityUuid);
        plugin.getTankTracker().startTracking(entityUuid);
        plugin.getSupportTracker().startTracking(entityUuid);

        return true;
    }

    private void startExpireTimer(long seconds) {
        if (expireTask != null) {
            expireTask.cancel();
        }

        expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (config.isClearOnExpire()) {
                despawn();
                plugin.getLogger().info("Boss expired and removed: " + config.getId());
            }
            expireTime = null;
        }, seconds * 20L);
    }

    private void startLeashCheck() {
        if (leashTask != null) {
            leashTask.cancel();
        }

        leashTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentBoss == null || spawnOrigin == null) {
                if (leashTask != null) {
                    leashTask.cancel();
                    leashTask = null;
                }
                return;
            }

            Entity entity = BossEntityHelper.getBukkitEntity(currentBoss);
            if (entity == null) {
                plugin.debug("Boss entity lost during leash check: " + config.getId());
                cleanup();
                return;
            }

            // Cross-world or unloaded world check
            if (spawnOrigin.getWorld() == null || !entity.getWorld().equals(spawnOrigin.getWorld())) {
                if (spawnOrigin.getWorld() != null) {
                    entity.teleport(spawnOrigin);
                }
                return;
            }

            double distance = entity.getLocation().distance(spawnOrigin);
            if (distance > config.getLeashRadius()) {
                entity.teleport(spawnOrigin);
            }
        }, 20L, 20L);
    }

    /**
     * Called by BossDeathListener when boss dies via MythicMobDeathEvent.
     */
    public void onDeath() {
        plugin.getLogger().info("Boss died: " + config.getMythicMobId() + " (" + config.getId() + ")");
        cleanup();
    }

    public void cleanup() {
        // Stop trackers to prevent memory leak
        if (entityUuid != null) {
            plugin.getDamageTracker().stopTracking(entityUuid);
            plugin.getTankTracker().stopTracking(entityUuid);
            plugin.getSupportTracker().stopTracking(entityUuid);
        }

        currentBoss = null;
        entityUuid = null;
        spawnOrigin = null;
        expireTime = null;
        spawnEpoch = 0;
        expireEpoch = 0;

        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }

        if (leashTask != null) {
            leashTask.cancel();
            leashTask = null;
        }
    }

    public void despawn() {
        if (currentBoss != null) {
            Entity entity = BossEntityHelper.getBukkitEntity(currentBoss);
            if (entity != null) {
                entity.remove();
            }
        }
        cleanup();
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
        return BossEntityHelper.getBukkitEntity(currentBoss) != null;
    }

    public BossConfig getConfig() { return config; }
    public ActiveMob getCurrentBoss() { return currentBoss; }
    public UUID getEntityUuid() { return entityUuid; }
    public ZonedDateTime getNextSpawnTime() { return nextSpawnTime; }
    public ZonedDateTime getExpireTime() { return expireTime; }
    public long getSpawnEpoch() { return spawnEpoch; }
    public long getExpireEpoch() { return expireEpoch; }

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
