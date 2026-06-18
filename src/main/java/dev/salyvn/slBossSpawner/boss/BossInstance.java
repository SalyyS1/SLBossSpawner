package dev.salyvn.slBossSpawner.boss;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.utils.TimeUtils;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
    private BukkitTask noPlayerTask;
    private int emptySeconds = 0;
    private boolean chunkForced = false;

    /**
     * Grace period counter: when getBukkitEntity() returns null during periodic
     * checks (leash/noPlayer), we increment this instead of immediately calling
     * cleanup(). Only after 3 consecutive nulls (3 seconds) do we treat the
     * entity as truly lost. This prevents the race condition where MythicMobs
     * entities aren't fully initialized yet when spawning 4+ bosses at once.
     */
    private int entityNullCount = 0;

    private ActiveMob currentBoss;
    private UUID entityUuid;
    private String currentMythicMobId;
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
        if (nextSpawnTime == null || isAlive())
            return false;
        ZonedDateTime now = ZonedDateTime.now(config.getTimeZone());
        return !now.isBefore(nextSpawnTime);
    }

    public void spawn() {
        despawn();

        // H5 FIX: resolve location lazily at spawn time
        Location loc = config.resolveLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("[SPAWN] FAIL: Invalid location for boss '" + config.getId()
                    + "' — world '" + config.getSpawnWorldName() + "' not loaded");
            return;
        }

        // Pick which mob to spawn (random pool or single)
        BossConfig.MobEntry pickedMob = config.pickMob();
        String mobToSpawn = pickedMob.mobId();
        currentMythicMobId = mobToSpawn;

        plugin.getLogger().info("[SPAWN] " + config.getId() + " → world="
                + loc.getWorld().getName() + " x=" + loc.getBlockX()
                + " y=" + loc.getBlockY() + " z=" + loc.getBlockZ()
                + (config.isRandomSpawn() ? " (random: " + mobToSpawn + ")" : "")
                + (pickedMob.expireMinutes() > 0 ? " expire=" + pickedMob.expireMinutes() + "min" : ""));

        // M16: force-load chunk BEFORE spawn to ensure entity persists in unloaded area
        if (config.isLoadChunks()) {
            Chunk chunk = loc.getChunk();
            if (!chunk.isLoaded()) {
                chunk.load(true);
                plugin.getLogger().info("[SPAWN] Chunk loaded (was unloaded)");
            }
            if (!chunk.isForceLoaded()) {
                chunk.setForceLoaded(true);
                chunkForced = true;
                plugin.getLogger().info("[SPAWN] Chunk force-loaded");
            }
        } else {
            plugin.getLogger().info("[SPAWN] loadChunks=false, skipping");
        }

        try {
            plugin.getLogger().info("[SPAWN] Calling MythicMobs spawnMob('" + mobToSpawn + "')...");
            ActiveMob mob = MythicBukkit.inst().getMobManager().spawnMob(
                    mobToSpawn, loc);

            if (mob != null) {
                currentBoss = mob;
                spawnOrigin = loc.clone();

                Entity entity = BossEntityHelper.getBukkitEntity(mob);
                entityUuid = (entity != null) ? entity.getUniqueId() : null;

                plugin.getLogger().info("[SPAWN] SUCCESS: " + mobToSpawn
                        + " UUID=" + entityUuid
                        + " entity=" + (entity != null ? entity.getType() : "NULL"));

                // Use per-mob expire time if configured, otherwise global expire
                int effectiveExpireSeconds = config.getExpireSecondsForMob(pickedMob);

                long now = Instant.now().getEpochSecond();
                spawnEpoch = now;
                expireEpoch = now + effectiveExpireSeconds;
                expireTime = ZonedDateTime.now(config.getTimeZone())
                        .plusSeconds(effectiveExpireSeconds);

                // H10 FIX: register in entity index for O(1) death-event lookup
                if (entityUuid != null) {
                    plugin.getBossScheduler().registerEntity(entityUuid, this);
                }

                startExpireTimer(effectiveExpireSeconds);

                // Delay leash and noPlayer checks by 3 seconds to allow MythicMobs
                // entity initialization to fully complete (prevents race condition
                // with getBukkitEntity() returning null for 4+ simultaneous spawns)
                if (config.getLeashRadius() > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, this::startLeashCheck, 60L);
                }

                // M16: start no-player idle despawn check (delayed)
                if (config.getNoPlayerDespawnSeconds() > 0) {
                    Bukkit.getScheduler().runTaskLater(plugin, this::startNoPlayerCheck, 60L);
                }

                // Play spawn sound globally
                playSpawnSound(loc);

                plugin.debug("Boss spawned: " + mobToSpawn
                        + " (" + config.getId() + ") UUID=" + entityUuid);
                plugin.getLogger().info("Boss spawned: " + mobToSpawn
                        + " (" + config.getId() + ")");

                // Start tracking (damage, tank, support)
                if (entityUuid != null) {
                    plugin.getDamageTracker().startTracking(entityUuid);
                    plugin.getTankTracker().startTracking(entityUuid);
                    plugin.getSupportTracker().startTracking(entityUuid);
                }

                plugin.getBossStateManager().saveSingle(this);
            } else {
                plugin.getLogger()
                        .warning("[SPAWN] FAIL: MythicMobs returned null for '" + mobToSpawn + "'");
                unforceChunk(loc);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[SPAWN] EXCEPTION: " + config.getId() + ": " + e.getMessage());
            e.printStackTrace();
            unforceChunk(loc);
        }
    }

    /**
     * Restore a boss from persisted state after server restart.
     */
    public boolean restore(UUID savedUuid, long savedSpawnEpoch, long savedExpireEpoch,
                            String savedMythicMobId) {
        long now = Instant.now().getEpochSecond();
        if (now >= savedExpireEpoch) {
            plugin.debug("Boss " + config.getId() + " expired during downtime, skipping restore");
            return false;
        }

        ActiveMob found = null;
        for (ActiveMob am : MythicBukkit.inst().getMobManager().getActiveMobs()) {
            Entity e = BossEntityHelper.getBukkitEntity(am);
            if (e != null && e.getUniqueId().equals(savedUuid)) {
                found = am;
                break;
            }
        }

        if (found == null) {
            plugin.debug("Boss " + config.getId() + " entity not found for UUID "
                    + savedUuid + ", skipping restore");
            return false;
        }

        Entity entity = BossEntityHelper.getBukkitEntity(found);
        if (entity == null)
            return false;

        currentBoss = found;
        entityUuid = savedUuid;
        currentMythicMobId = savedMythicMobId != null ? savedMythicMobId : config.getMythicMobId();
        spawnEpoch = savedSpawnEpoch;
        expireEpoch = savedExpireEpoch;
        spawnOrigin = entity.getLocation().clone();
        expireTime = ZonedDateTime.now(config.getTimeZone())
                .plusSeconds(savedExpireEpoch - now);

        // H10 FIX: register in entity index after restore
        plugin.getBossScheduler().registerEntity(entityUuid, this);

        long remaining = savedExpireEpoch - now;
        startExpireTimer(remaining);

        if (config.getLeashRadius() > 0) {
            startLeashCheck();
        }

        // M16: start no-player idle despawn check on restore too
        if (config.getNoPlayerDespawnSeconds() > 0) {
            startNoPlayerCheck();
        }

        // M16: force-load chunk on restore
        if (config.isLoadChunks() && entity.getLocation().isChunkLoaded()) {
            Chunk chunk = entity.getLocation().getChunk();
            if (!chunk.isForceLoaded()) {
                chunk.setForceLoaded(true);
                chunkForced = true;
            }
        }

        plugin.getLogger().info("Boss restored: " + config.getId()
                + " (remaining: " + remaining + "s)");

        plugin.getDamageTracker().startTracking(entityUuid);
        plugin.getTankTracker().startTracking(entityUuid);
        plugin.getSupportTracker().startTracking(entityUuid);

        return true;
    }

    private void startExpireTimer(long seconds) {
        if (expireTask != null)
            expireTask.cancel();

        expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (config.isClearOnExpire()) {
                despawn();
                plugin.getLogger().info("Boss expired and removed: " + config.getId());
            }
            expireTime = null;
        }, seconds * 20L);
    }

    private void startLeashCheck() {
        if (leashTask != null)
            leashTask.cancel();

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
                entityNullCount++;
                if (entityNullCount >= 3) {
                    plugin.debug("Boss entity lost during leash check (" + entityNullCount
                            + " consecutive nulls): " + config.getId());
                    cleanup();
                }
                return;
            }
            entityNullCount = 0;

            if (spawnOrigin.getWorld() == null || !entity.getWorld().equals(spawnOrigin.getWorld())) {
                if (spawnOrigin.getWorld() != null)
                    entity.teleport(spawnOrigin);
                return;
            }

            if (entity.getLocation().distance(spawnOrigin) > config.getLeashRadius()) {
                entity.teleport(spawnOrigin);
            }
        }, 20L, 20L);
    }

    public void onDeath() {
        String mobName = currentMythicMobId != null ? currentMythicMobId : config.getMythicMobId();
        plugin.getLogger().info("Boss died: " + mobName
                + " (" + config.getId() + ")");
        cleanup();
    }

    public void cleanup() {
        // Stop trackers to prevent memory leak
        if (entityUuid != null) {
            plugin.getDamageTracker().stopTracking(entityUuid);
            plugin.getTankTracker().stopTracking(entityUuid);
            plugin.getSupportTracker().stopTracking(entityUuid);
            // H10 FIX: deregister from entity index
            plugin.getBossScheduler().unregisterEntity(entityUuid);
        }

        // M5 FIX: remove from state file on cleanup so dead bosses aren't restored
        plugin.getBossStateManager().remove(config.getId());

        // M16: unforce chunk if we force-loaded it
        if (chunkForced && spawnOrigin != null && spawnOrigin.getWorld() != null) {
            try {
                Chunk chunk = spawnOrigin.getChunk();
                chunk.setForceLoaded(false);
            } catch (Exception ignored) {
            }
            chunkForced = false;
        }

        currentBoss = null;
        entityUuid = null;
        currentMythicMobId = null;
        spawnOrigin = null;
        expireTime = null;
        spawnEpoch = 0;
        expireEpoch = 0;
        emptySeconds = 0;
        entityNullCount = 0;

        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        if (leashTask != null) {
            leashTask.cancel();
            leashTask = null;
        }
        if (noPlayerTask != null) {
            noPlayerTask.cancel();
            noPlayerTask = null;
        }
    }

    public void despawn() {
        if (currentBoss != null) {
            Entity entity = BossEntityHelper.getBukkitEntity(currentBoss);
            if (entity != null)
                entity.remove();
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
        if (noPlayerTask != null) {
            noPlayerTask.cancel();
            noPlayerTask = null;
        }
    }

    public boolean isAlive() {
        if (currentBoss == null)
            return false;
        return BossEntityHelper.getBukkitEntity(currentBoss) != null;
    }

    public BossConfig getConfig() {
        return config;
    }

    public ActiveMob getCurrentBoss() {
        return currentBoss;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    /**
     * Returns the MythicMob ID that was actually spawned (may differ from config
     * default when random pool is used). Returns null if no boss is alive.
     */
    public String getCurrentMythicMobId() {
        return currentMythicMobId;
    }

    public ZonedDateTime getNextSpawnTime() {
        return nextSpawnTime;
    }

    public ZonedDateTime getExpireTime() {
        return expireTime;
    }

    public long getSpawnEpoch() {
        return spawnEpoch;
    }

    public long getExpireEpoch() {
        return expireEpoch;
    }

    public long getSecondsUntilNext() {
        if (nextSpawnTime == null)
            return -1;
        return TimeUtils.getSecondsUntil(nextSpawnTime);
    }

    public String getNextSpawnFormatted() {
        if (nextSpawnTime == null)
            return "N/A";
        return TimeUtils.formatTime(nextSpawnTime);
    }

    public long getSecondsUntilExpire() {
        if (expireTime == null || !isAlive())
            return -1;
        return TimeUtils.getSecondsUntil(expireTime);
    }

    /**
     * M16: Periodically check if any player is within range of the boss.
     * If no player is found for noPlayerDespawnSeconds, despawn the boss.
     */
    private void startNoPlayerCheck() {
        if (noPlayerTask != null)
            noPlayerTask.cancel();
        emptySeconds = 0;

        int checkRadius = Math.max(config.getLeashRadius() * 2, 48);
        int threshold = config.getNoPlayerDespawnSeconds();

        noPlayerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentBoss == null || spawnOrigin == null) {
                if (noPlayerTask != null) {
                    noPlayerTask.cancel();
                    noPlayerTask = null;
                }
                return;
            }

            Entity entity = BossEntityHelper.getBukkitEntity(currentBoss);
            if (entity == null) {
                entityNullCount++;
                if (entityNullCount >= 3) {
                    plugin.debug("Boss entity lost during noPlayer check ("
                            + entityNullCount + " consecutive nulls): " + config.getId());
                    cleanup();
                }
                return;
            }
            entityNullCount = 0;

            boolean playerNearby = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(entity.getWorld())
                        && p.getLocation().distance(entity.getLocation()) <= checkRadius) {
                    playerNearby = true;
                    break;
                }
            }

            if (playerNearby) {
                emptySeconds = 0;
            } else {
                emptySeconds++;
                if (emptySeconds >= threshold) {
                    plugin.getLogger().info("Boss " + config.getId()
                            + " despawned — no players nearby for " + threshold + "s");
                    despawn();
                }
            }
        }, 20L, 20L); // check every 1 second
    }

    /**
     * Play a configurable sound to all online players when a boss spawns.
     * Sound name should match a Bukkit {@link Sound} enum constant.
     * Falls back silently if the sound name is invalid.
     */
    private void playSpawnSound(Location spawnLoc) {
        if (!config.isSpawnSoundEnabled()) return;

        Sound sound;
        try {
            sound = Sound.valueOf(config.getSpawnSound().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[SOUND] Invalid sound name '"
                    + config.getSpawnSound() + "' for boss '" + config.getId()
                    + "'. Use Bukkit Sound enum names (e.g. ENTITY_ENDER_DRAGON_GROWL)");
            return;
        }

        float volume = config.getSpawnSoundVolume();
        float pitch = config.getSpawnSoundPitch();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }

        plugin.debug("Played spawn sound " + sound.name()
                + " vol=" + volume + " pitch=" + pitch
                + " for boss " + config.getId());
    }

    /** Undo force-load if we set it. */
    private void unforceChunk(Location loc) {
        if (chunkForced && loc != null && loc.getWorld() != null) {
            try {
                loc.getChunk().setForceLoaded(false);
            } catch (Exception ignored) {
            }
            chunkForced = false;
        }
    }
}
