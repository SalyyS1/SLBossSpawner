package dev.salyvn.slBossSpawner.boss;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.utils.TimeUtils;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class BossInstance {
    private final SLBossSpawner plugin;
    private final BossConfig config;
    private BukkitTask expireTask;
    private BukkitTask presenceTask;
    private BukkitTask leashTask;
    private BukkitTask noPlayerTask;
    private BukkitTask leashStartTask;
    private BukkitTask noPlayerStartTask;
    private BukkitTask spawnVerifyTask;
    private BukkitTask stateLocationSaveTask;
    private boolean stopped;
    private int emptySeconds = 0;
    private World ticketWorld;
    private int ticketChunkX;
    private int ticketChunkZ;
    private Location lastKnownEntityLocation;
    private boolean unloadWarningLogged;
    private boolean missingReservation;

    /** Consecutive missing seconds before pausing scheduled replacement. */
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
        if (!despawn()) {
            plugin.getLogger().warning("[SPAWN] FAIL: old boss '" + config.getId()
                    + "' could not be removed safely; keeping its saved UUID");
            return;
        }
        stopped = false;

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

        plugin.getLogger().info("[SPAWN] " + config.getId() + " → world="
                + loc.getWorld().getName() + " x=" + loc.getBlockX()
                + " y=" + loc.getBlockY() + " z=" + loc.getBlockZ()
                + (config.isRandomSpawn() ? " (random: " + mobToSpawn + ")" : "")
                + (pickedMob.expireMinutes() > 0 ? " expire=" + pickedMob.expireMinutes() + "min" : ""));

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        if (!config.isLoadChunks() && !loc.getWorld().isChunkLoaded(chunkX, chunkZ)) {
            plugin.getLogger().warning("[SPAWN] FAIL: chunk " + chunkX + "," + chunkZ
                    + " in " + loc.getWorld().getName() + " is unloaded for boss '"
                    + config.getId() + "'. Set loadChunks: true or keep the chunk loaded before spawning.");
            return;
        }

        try {
            if (config.isLoadChunks()) {
                BossChunkTickets.acquire(plugin, loc.getWorld(), chunkX, chunkZ);
                ticketWorld = loc.getWorld();
                ticketChunkX = chunkX;
                ticketChunkZ = chunkZ;
                plugin.getLogger().info("[SPAWN] Plugin chunk ticket acquired at "
                        + chunkX + "," + chunkZ);
            } else {
                plugin.getLogger().warning("[SPAWN] loadChunks=false for boss '" + config.getId()
                        + "': the loaded chunk may unload later; set loadChunks: true to keep this boss active.");
            }

            plugin.getLogger().info("[SPAWN] Calling MythicMobs spawnMob('" + mobToSpawn + "')...");
            ActiveMob mob = MythicBukkit.inst().getMobManager().spawnMob(
                    mobToSpawn, loc);

            if (mob != null) {
                Entity entity = BossEntityHelper.getBukkitEntity(mob);
                if (entity == null) {
                    plugin.getLogger().warning("[SPAWN] FAIL: MythicMobs returned '" + mobToSpawn
                            + "' without a valid Bukkit entity; the spawn was not registered.");
                    mob.remove();
                    releaseChunkTicket();
                    return;
                }

                currentBoss = mob;
                currentMythicMobId = mobToSpawn;
                spawnOrigin = loc.clone();
                entityUuid = entity.getUniqueId();
                keepEntityPersistent(entity);
                lastKnownEntityLocation = entity.getLocation().clone();

                plugin.getLogger().info("[SPAWN] SUCCESS: " + mobToSpawn
                        + " UUID=" + entityUuid
                        + " entity=" + entity.getType());
                verifySpawnNextSecond(entityUuid);

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
                startPresenceCheck();

                // Delay leash and noPlayer checks by 3 seconds to allow MythicMobs
                // entity initialization to fully complete (prevents race condition
                // with getBukkitEntity() returning null for 4+ simultaneous spawns)
                if (config.getLeashRadius() > 0) {
                    UUID expectedUuid = entityUuid;
                    leashStartTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        leashStartTask = null;
                        if (!stopped && expectedUuid.equals(entityUuid)) startLeashCheck();
                    }, 60L);
                }

                // M16: start no-player idle despawn check (delayed)
                if (config.getNoPlayerDespawnSeconds() > 0) {
                    UUID expectedUuid = entityUuid;
                    noPlayerStartTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        noPlayerStartTask = null;
                        if (!stopped && expectedUuid.equals(entityUuid)) startNoPlayerCheck();
                    }, 60L);
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
                releaseChunkTicket();
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "[SPAWN] EXCEPTION: " + config.getId(), e);
            if (currentBoss != null) {
                despawn();
            } else {
                releaseChunkTicket();
            }
        }
    }

    /**
     * Restore a boss from persisted state after server restart.
     */
    public boolean restore(UUID savedUuid, long savedSpawnEpoch, long savedExpireEpoch,
                            String savedMythicMobId, Location savedLocation) {
        long now = Instant.now().getEpochSecond();
        if (now >= savedExpireEpoch && config.isClearOnExpire()) {
            plugin.debug("Boss " + config.getId() + " expired during downtime, skipping restore");
            releaseChunkTicket();
            return false;
        }

        if (config.isLoadChunks()) {
            Location preload = savedLocation != null && savedLocation.getWorld() != null
                    ? savedLocation : config.resolveLocation();
            if (preload == null || preload.getWorld() == null) {
                plugin.getLogger().warning("Cannot restore boss '" + config.getId()
                        + "': saved world and configured spawn world are unavailable");
                releaseChunkTicket();
                return false;
            }
            try {
                retainChunkTicketAt(preload);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Cannot load saved chunk for boss '" + config.getId() + "'", e);
                releaseChunkTicket();
                return false;
            }
        }

        try {
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
                        + savedUuid + ", waiting for MythicMobs to register it");
                return false;
            }

            Entity entity = BossEntityHelper.getBukkitEntity(found);
            if (entity == null) {
                plugin.debug("Boss " + config.getId() + " has no Bukkit entity yet; retrying restore");
                return false;
            }

            Location entityLocation = entity.getLocation();
            updateTicketForEntity(entityLocation);
            keepEntityPersistent(entity);

            currentBoss = found;
            stopped = false;
            entityUuid = savedUuid;
            currentMythicMobId = savedMythicMobId != null ? savedMythicMobId : config.getMythicMobId();
            spawnEpoch = savedSpawnEpoch;
            expireEpoch = savedExpireEpoch;
            spawnOrigin = entityLocation.clone();
            lastKnownEntityLocation = entityLocation.clone();
            long remaining = Math.max(0, savedExpireEpoch - now);
            expireTime = remaining > 0
                    ? ZonedDateTime.now(config.getTimeZone()).plusSeconds(remaining)
                    : null;

            // H10 FIX: register in entity index after restore
            plugin.getBossScheduler().registerEntity(entityUuid, this);

            if (remaining > 0) startExpireTimer(remaining);
            startPresenceCheck();

            if (config.getLeashRadius() > 0) {
                startLeashCheck();
            }

            // M16: start no-player idle despawn check on restore too
            if (config.getNoPlayerDespawnSeconds() > 0) {
                startNoPlayerCheck();
            }

            plugin.getLogger().info("Boss restored: " + config.getId()
                    + (remaining > 0 ? " (remaining: " + remaining + "s)"
                    : " (expiry timer elapsed; clearOnExpire=false)"));

            plugin.getDamageTracker().startTracking(entityUuid);
            plugin.getTankTracker().startTracking(entityUuid);
            plugin.getSupportTracker().startTracking(entityUuid);

            // The saved location may be from an older release or a previous
            // chunk. Record the entity's current position once it is attached.
            plugin.getBossStateManager().saveSingle(this);

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Could not restore boss '" + config.getId() + "'", e);
            if (currentBoss != null) {
                cleanup(false);
            } else {
                releaseChunkTicket();
            }
            return false;
        }
    }

    private void startExpireTimer(long seconds) {
        if (expireTask != null)
            expireTask.cancel();

        expireTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (config.isClearOnExpire()) {
                if (despawn()) {
                    plugin.getLogger().info("Boss expired and removed: " + config.getId());
                } else {
                    // Keep the saved UUID and retry once the entity can be
                    // verified; an expired mob must not become untracked.
                    startExpireTimer(5);
                    return;
                }
            }
            expireTime = null;
        }, seconds * 20L);
    }

    /** Keep tracking mobs even when no leash or idle-despawn check is enabled. */
    private void startPresenceCheck() {
        if (presenceTask != null) presenceTask.cancel();
        presenceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (stopped || currentBoss == null) {
                if (presenceTask != null) {
                    presenceTask.cancel();
                    presenceTask = null;
                }
                return;
            }
            if (getTrackedEntity() == null) {
                handleMissingEntity("presence check");
            } else {
                entityNullCount = 0;
            }
        }, 20L, 20L);
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

            Entity entity = getTrackedEntity();
            if (entity == null) return;

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
        cleanup(true);
    }

    private void cleanup(boolean removeSavedState) {
        stopped = true;
        cancelDelayedTasks();
        if (missingReservation) {
            plugin.getBossScheduler().clearRestoreReservation(config.getId());
            missingReservation = false;
        }
        // Stop trackers to prevent memory leak
        if (entityUuid != null) {
            plugin.getDamageTracker().stopTracking(entityUuid);
            plugin.getTankTracker().stopTracking(entityUuid);
            plugin.getSupportTracker().stopTracking(entityUuid);
            // H10 FIX: deregister from entity index
            plugin.getBossScheduler().unregisterEntity(entityUuid);
        }

        if (removeSavedState) {
            plugin.getBossStateManager().remove(config.getId());
        }

        releaseChunkTicket();

        currentBoss = null;
        entityUuid = null;
        currentMythicMobId = null;
        spawnOrigin = null;
        expireTime = null;
        spawnEpoch = 0;
        expireEpoch = 0;
        emptySeconds = 0;
        entityNullCount = 0;
        lastKnownEntityLocation = null;
        unloadWarningLogged = false;

        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        if (presenceTask != null) {
            presenceTask.cancel();
            presenceTask = null;
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

    /** Check whether a live entity can be reached before a multi-boss reload. */
    public boolean canDespawn() {
        if (currentBoss == null) return true;
        try {
            Entity entity = getTrackedEntity();
            if (entity == null && entityUuid != null && isLastKnownChunkUnloaded()) {
                Location known = lastKnownEntityLocation;
                known.getWorld().getChunkAt(known.getBlockX() >> 4, known.getBlockZ() >> 4);
                entity = getTrackedEntity();
            }
            return entity != null;
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot verify boss '" + config.getId()
                    + "' before reload: " + e.getMessage());
            return false;
        }
    }

    public boolean despawn() {
        if (currentBoss != null) {
            try {
                Entity entity = getTrackedEntity();
                if (entity == null && entityUuid != null && isLastKnownChunkUnloaded()) {
                    // An explicit despawn must remove a persistent boss even when
                    // loadChunks=false allowed its chunk to unload.
                    Location known = lastKnownEntityLocation;
                    known.getWorld().getChunkAt(known.getBlockX() >> 4, known.getBlockZ() >> 4);
                    entity = getTrackedEntity();
                }
                if (entity == null) {
                    plugin.getLogger().warning("Could not verify removal of boss '" + config.getId()
                            + "' entity " + entityUuid + " at "
                            + describeLocation(lastKnownEntityLocation)
                            + "; keeping its saved state");
                    return false;
                }
                entity.remove();
            } catch (Exception e) {
                plugin.getLogger().warning("Could not remove boss '" + config.getId()
                        + "' entity " + entityUuid + ": " + e.getMessage()
                        + "; keeping its saved state");
                return false;
            }
        }
        cleanup();
        return true;
    }

    public void stop() {
        stopped = true;
        cancelDelayedTasks();
        if (expireTask != null) {
            expireTask.cancel();
            expireTask = null;
        }
        if (presenceTask != null) {
            presenceTask.cancel();
            presenceTask = null;
        }
        if (leashTask != null) {
            leashTask.cancel();
            leashTask = null;
        }
        if (noPlayerTask != null) {
            noPlayerTask.cancel();
            noPlayerTask = null;
        }
        // Shutdown preserves the mob in the world; plugin tickets must still be
        // removed so they do not outlive this scheduler instance.
        releaseChunkTicket();
    }

    public boolean isAlive() {
        if (currentBoss == null)
            return false;
        return getTrackedEntity() != null || isLastKnownChunkUnloaded();
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

    /** Last observed position, retained while a non-ticketed chunk is unloaded. */
    public Location getLastKnownEntityLocation() {
        return lastKnownEntityLocation == null ? null : lastKnownEntityLocation.clone();
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

            Entity entity = getTrackedEntity();
            if (entity == null) return;

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
                    if (despawn()) {
                        plugin.getLogger().info("Boss " + config.getId()
                                + " despawned — no players nearby for " + threshold + "s");
                    }
                }
            }
        }, 20L, 20L); // check every 1 second
    }

    /**
     * Play a configurable sound to all online players when a boss spawns.
     * Accepts a sound key or a legacy enum-style name from existing configs.
     * Falls back silently if the sound name is invalid.
     */
    private void playSpawnSound(Location spawnLoc) {
        if (!config.isSpawnSoundEnabled()) return;

        String configured = config.getSpawnSound().trim();
        NamespacedKey soundKey = NamespacedKey.fromString(configured.toLowerCase(Locale.ROOT));
        Sound sound = soundKey == null ? null : Registry.SOUND_EVENT.get(soundKey);
        if (sound == null) {
            // Preserve existing ENTITY_ENDER_DRAGON_GROWL style config values.
            for (Sound candidate : Registry.SOUND_EVENT) {
                NamespacedKey candidateKey = Registry.SOUND_EVENT.getKey(candidate);
                if (candidateKey != null && candidateKey.getKey().replace('.', '_')
                        .equalsIgnoreCase(configured)) {
                    sound = candidate;
                    soundKey = candidateKey;
                    break;
                }
            }
        }
        if (sound == null) {
            plugin.getLogger().warning("[SOUND] Invalid sound name '"
                    + config.getSpawnSound() + "' for boss '" + config.getId()
                    + "'. Use a sound key (e.g. minecraft:entity.ender_dragon.growl)");
            return;
        }

        float volume = config.getSpawnSoundVolume();
        float pitch = config.getSpawnSoundPitch();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, volume, pitch);
        }

        plugin.debug("Played spawn sound " + soundKey
                + " vol=" + volume + " pitch=" + pitch
                + " for boss " + config.getId());
    }

    private void keepEntityPersistent(Entity entity) {
        entity.setPersistent(true);
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
        }
    }

    /** Resolve the current entity even if MythicMobs replaced its adapter on chunk load. */
    private Entity getTrackedEntity() {
        Entity entity = BossEntityHelper.getBukkitEntity(currentBoss);
        if (entity == null && entityUuid != null) {
            Entity loaded = Bukkit.getEntity(entityUuid);
            if (loaded != null && loaded.isValid() && !loaded.isDead()) {
                entity = loaded;
            }
        }
        if (entity != null) {
            Location currentLocation = entity.getLocation();
            Location previousLocation = lastKnownEntityLocation;
            lastKnownEntityLocation = currentLocation.clone();
            updateTicketForEntity(currentLocation);
            if (!stopped && entityUuid != null && previousLocation != null
                    && (previousLocation.getWorld() != currentLocation.getWorld()
                    || (previousLocation.getBlockX() >> 4) != (currentLocation.getBlockX() >> 4)
                    || (previousLocation.getBlockZ() >> 4) != (currentLocation.getBlockZ() >> 4))) {
                scheduleStateLocationSave();
            }
            if (unloadWarningLogged) {
                plugin.getLogger().info("[SPAWN] Boss '" + config.getId()
                        + "' is loaded again at " + describeLocation(currentLocation));
                unloadWarningLogged = false;
            }
            if (missingReservation) {
                missingReservation = false;
                plugin.getBossScheduler().clearRestoreReservation(config.getId());
                plugin.getLogger().info("[SPAWN] Boss '" + config.getId()
                        + "' entity " + entityUuid + " became available again");
            }
        }
        return entity;
    }

    /** Keep the ticket with the entity when a Mythic skill moves it to another chunk. */
    private void updateTicketForEntity(Location location) {
        if (ticketWorld == null || location.getWorld() == null) return;
        retainChunkTicketAt(location);
    }

    /** Retain exactly one ticket across restore retries and chunk transfers. */
    private void retainChunkTicketAt(Location location) {
        World world = location.getWorld();
        if (world == null) throw new IllegalArgumentException("Chunk ticket location has no world");
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (world.equals(ticketWorld) && chunkX == ticketChunkX && chunkZ == ticketChunkZ) return;

        BossChunkTickets.acquire(plugin, world, chunkX, chunkZ);
        World previousWorld = ticketWorld;
        int previousX = ticketChunkX;
        int previousZ = ticketChunkZ;
        ticketWorld = world;
        ticketChunkX = chunkX;
        ticketChunkZ = chunkZ;
        if (previousWorld != null) {
            BossChunkTickets.release(plugin, previousWorld, previousX, previousZ);
        }
    }

    private boolean isLastKnownChunkUnloaded() {
        Location known = lastKnownEntityLocation;
        if (known == null || known.getWorld() == null) return false;
        return !known.getWorld().isChunkLoaded(known.getBlockX() >> 4,
                known.getBlockZ() >> 4);
    }

    private void handleMissingEntity(String check) {
        if (isLastKnownChunkUnloaded()) {
            entityNullCount = 0;
            if (!unloadWarningLogged) {
                plugin.getLogger().warning("[SPAWN] Boss '" + config.getId()
                        + "' is temporarily unavailable because its chunk unloaded at "
                        + describeLocation(lastKnownEntityLocation)
                        + ". Set loadChunks: true to keep it active.");
                unloadWarningLogged = true;
            }
            return;
        }

        if (++entityNullCount >= 3 && !missingReservation) {
            plugin.getLogger().warning("[SPAWN] Boss '" + config.getId()
                    + "' entity " + entityUuid + " is missing during " + check
                    + " while its last known chunk is loaded; keeping its UUID"
                    + " and pausing scheduled replacement until it returns or an admin"
                    + " runs /slboss state forget " + config.getId());
            missingReservation = true;
            plugin.getBossScheduler().reserveRestore(config.getId());
        }
    }

    private void verifySpawnNextSecond(UUID expectedUuid) {
        spawnVerifyTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnVerifyTask = null;
            if (stopped || !expectedUuid.equals(entityUuid)) return;

            Entity entity = getTrackedEntity();
            if (entity == null) {
                plugin.getLogger().warning("[SPAWN] VERIFY: boss '" + config.getId()
                        + "' entity " + expectedUuid + " is no longer loaded one second after spawn"
                        + (isLastKnownChunkUnloaded() ? " (chunk unloaded)" : " (chunk loaded)"));
                return;
            }

            Location actual = entity.getLocation();
            plugin.getLogger().info("[SPAWN] VERIFY: boss '" + config.getId()
                    + "' entity " + expectedUuid + " at " + describeLocation(actual));
            if (spawnOrigin != null && (actual.getWorld() != spawnOrigin.getWorld()
                    || actual.distanceSquared(spawnOrigin) > 9.0)) {
                plugin.getLogger().warning("[SPAWN] Boss '" + config.getId()
                        + "' moved over 3 blocks from configured spawn "
                        + describeLocation(spawnOrigin)
                        + "; check terrain, gravity, and MythicMobs skills.");
            }
        }, 20L);
    }

    /** Persist a moved boss's new chunk without writing for every block step. */
    private void scheduleStateLocationSave() {
        if (stateLocationSaveTask != null) return;
        stateLocationSaveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stateLocationSaveTask = null;
            if (!stopped && entityUuid != null
                    && plugin.getBossScheduler().getBossInstance(config.getId()) == this) {
                plugin.getBossStateManager().saveSingle(this);
            }
        }, 1L);
    }

    private void cancelDelayedTasks() {
        if (leashStartTask != null) {
            leashStartTask.cancel();
            leashStartTask = null;
        }
        if (noPlayerStartTask != null) {
            noPlayerStartTask.cancel();
            noPlayerStartTask = null;
        }
        if (spawnVerifyTask != null) {
            spawnVerifyTask.cancel();
            spawnVerifyTask = null;
        }
        if (stateLocationSaveTask != null) {
            stateLocationSaveTask.cancel();
            stateLocationSaveTask = null;
        }
    }

    private static String describeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "unknown location";
        return loc.getWorld().getName() + " x=" + loc.getBlockX()
                + " y=" + loc.getBlockY() + " z=" + loc.getBlockZ();
    }

    /** Release only the ticket owned by this boss, including failed spawns. */
    private void releaseChunkTicket() {
        if (ticketWorld == null) return;
        try {
            BossChunkTickets.release(plugin, ticketWorld, ticketChunkX, ticketChunkZ);
        } finally {
            ticketWorld = null;
        }
    }
}
