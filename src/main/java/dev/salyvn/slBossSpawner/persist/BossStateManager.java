package dev.salyvn.slBossSpawner.persist;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossInstance;
import dev.salyvn.slBossSpawner.boss.BossScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saves/loads alive boss state to boss-state.yml for server-restart survival.
 *
 * State files are small and updated only on boss lifecycle changes. Synchronous
 * writes keep successive read-modify-write operations in order.
 */
public class BossStateManager {
    private final SLBossSpawner plugin;
    private final File stateFile;
    private final Set<UUID> warnedUnverifiable = new HashSet<>();
    private final Set<String> warnedMissingWorlds = new HashSet<>();
    private volatile boolean disabled = false;

    public BossStateManager(SLBossSpawner plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "boss-state.yml");
    }

    /** Set to true during shutdown to prevent async task scheduling. */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /**
     * Save all currently alive bosses — always called synchronously (shutdown /
     * reload).
     */
    public void saveAll() {
        BossScheduler scheduler = plugin.getBossScheduler();
        YamlConfiguration yaml = new YamlConfiguration();

        // A MythicMob may register after the initial restore window. Retain its
        // UUID/location until it can be found or its expiry is verified. Also
        // retain entries for schedules removed while the server was offline:
        // restoreFromState must verify removal before their UUIDs are forgotten.
        if (stateFile.exists()) {
            YamlConfiguration previous = YamlConfiguration.loadConfiguration(stateFile);
            for (String bossId : previous.getKeys(false)) {
                if (!scheduler.isRestoreReserved(bossId)
                        && scheduler.getBossInstance(bossId) != null) continue;
                ConfigurationSection section = previous.getConfigurationSection(bossId);
                if (section == null) continue;
                for (String path : section.getKeys(true)) {
                    if (!section.isConfigurationSection(path)) {
                        yaml.set(bossId + "." + path, section.get(path));
                    }
                }
            }
        }

        for (Map.Entry<String, BossInstance> entry : scheduler.getAllBossInstances().entrySet()) {
            BossInstance instance = entry.getValue();
            if (!instance.isAlive() || instance.getEntityUuid() == null)
                continue;

            String bossId = entry.getKey();
            putBossState(yaml, bossId, instance);
        }

        writeSync(yaml);
    }

    /**
     * Save a single boss entry without racing another boss's state update.
     */
    public void saveSingle(BossInstance instance) {
        // Read existing YAML on main thread to build the snapshot
        YamlConfiguration yaml = stateFile.exists()
                ? YamlConfiguration.loadConfiguration(stateFile)
                : new YamlConfiguration();

        String bossId = instance.getConfig().getId();
        if (instance.isAlive() && instance.getEntityUuid() != null) {
            putBossState(yaml, bossId, instance);
        }

        writeSync(yaml);
    }

    /**
     * Remove a boss entry from the state file.
     */
    public void remove(String bossId) {
        if (disabled || !stateFile.exists())
            return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        yaml.set(bossId, null);

        writeSync(yaml);
    }

    /** Verify and remove an unresolved saved boss before an admin replaces it. */
    public boolean canDiscardReservedBoss(String bossId) {
        if (!stateFile.exists()) return true;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection section = yaml.getConfigurationSection(bossId);
        if (section == null) return true;
        try {
            UUID uuid = UUID.fromString(section.getString("entity-uuid"));
            if (Bukkit.getEntity(uuid) != null) return true;
            Location location = readSavedLocation(section);
            if (location == null || location.getWorld() == null) return false;
            location.getWorld().getChunkAt(location.getBlockX() >> 4,
                    location.getBlockZ() >> 4);
            return Bukkit.getEntity(uuid) != null;
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot verify saved boss '" + bossId
                    + "' before reload: " + e.getMessage());
            return false;
        }
    }

    /** Verify and remove an unresolved saved boss before an admin replaces it. */
    public boolean discardReservedBoss(String bossId) {
        if (!stateFile.exists()) return true;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection section = yaml.getConfigurationSection(bossId);
        if (section == null) return true;

        String rawUuid = section.getString("entity-uuid");
        UUID savedUuid;
        try {
            savedUuid = UUID.fromString(rawUuid);
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot verify saved boss '" + bossId
                    + "': invalid saved entity UUID");
            return false;
        }

        try {
            Entity entity = Bukkit.getEntity(savedUuid);
            if (entity == null) {
                Location location = readSavedLocation(section);
                if (location == null || location.getWorld() == null) {
                    plugin.getLogger().warning("Cannot replace saved boss '" + bossId
                            + "': exact saved location is unavailable");
                    return false;
                }
                location.getWorld().getChunkAt(location.getBlockX() >> 4,
                        location.getBlockZ() >> 4);
                entity = Bukkit.getEntity(savedUuid);
            }
            if (entity == null) {
                plugin.getLogger().warning("Cannot replace saved boss '" + bossId
                        + "': UUID " + savedUuid + " was not found in its last saved chunk."
                        + " Use /slboss state forget after checking the world.");
                return false;
            }
            entity.remove();
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot replace saved boss '" + bossId
                    + "': " + e.getMessage());
            return false;
        }

        yaml.set(bossId, null);
        return writeSync(yaml);
    }

    /**
     * Explicit admin recovery for old state rows with no saved location. The
     * operator must verify that an unloaded mob will not reappear afterward.
     */
    public boolean forgetSavedBoss(String bossId) {
        if (!stateFile.exists()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection section = yaml.getConfigurationSection(bossId);
        if (section == null) return false;

        String rawUuid = section.getString("entity-uuid");
        try {
            UUID uuid = UUID.fromString(rawUuid);
            Entity loaded = Bukkit.getEntity(uuid);
            if (loaded != null) {
                loaded.remove();
            } else {
                plugin.getLogger().warning("Forgetting saved boss '" + bossId + "' UUID "
                        + uuid + " without finding a loaded entity; verify it will not reappear");
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            plugin.getLogger().warning("Forgetting saved boss '" + bossId
                    + "' with an invalid UUID");
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot forget saved boss '" + bossId
                    + "': " + e.getMessage());
            return false;
        }

        yaml.set(bossId, null);
        return writeSync(yaml);
    }

    /** Synchronous save used at shutdown. */
    public void saveSync() {
        saveAll();
    }

    /** Restore bosses from state file after server restart. */
    public void restoreFromState(BossScheduler scheduler) {
        restoreFromState(scheduler, 0);
    }

    private void restoreFromState(BossScheduler scheduler, int attempt) {
        if (plugin.getBossScheduler() != scheduler) return;
        if (!stateFile.exists())
            return;

        YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(stateFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupted boss-state.yml, skipping restore: " + e.getMessage());
            return;
        }

        int restored = 0;
        int expired = 0;
        int missing = 0;
        boolean removedOrphan = false;

        for (String bossId : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(bossId);
            if (section == null)
                continue;

            BossInstance instance = scheduler.getBossInstance(bossId);
            if (instance == null) {
                UUID orphanUuid;
                try {
                    orphanUuid = UUID.fromString(section.getString("entity-uuid"));
                } catch (Exception e) {
                    if (attempt == 3) {
                        plugin.getLogger().warning("Cannot verify removed boss '" + bossId
                                + "': invalid saved entity UUID. Use /slboss state forget "
                                + bossId + " after checking the world.");
                    }
                    missing++;
                    continue;
                }
                if (removeExpiredEntity(orphanUuid, readSavedLocation(section))) {
                    yaml.set(bossId, null);
                    removedOrphan = true;
                    expired++;
                } else {
                    if (attempt == 3) {
                        plugin.getLogger().warning("Cannot verify removed boss '" + bossId
                                + "'; keeping its state. Use /slboss state forget "
                                + bossId + " after checking the world.");
                    }
                    missing++;
                }
                continue;
            }
            if (attempt > 0 && !scheduler.isRestoreReserved(bossId)) {
                // An admin may have replaced or despawned this boss while a
                // delayed restore retry was waiting.
                continue;
            }

            String uuidStr = section.getString("entity-uuid");
            long spawnTime = section.getLong("spawn-time", 0);
            long expireTime = section.getLong("expire-time", 0);
            if (uuidStr == null || spawnTime == 0 || expireTime == 0)
                continue;

            UUID entityUuid;
            try {
                entityUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in state file for boss " + bossId);
                continue;
            }

            if (instance.getEntityUuid() != null) {
                if (entityUuid.equals(instance.getEntityUuid())) {
                    restored++; // An earlier attempt already restored this boss.
                    scheduler.clearRestoreReservation(bossId);
                } else {
                    plugin.getLogger().warning("Boss '" + bossId
                            + "' has a different live entity; skipping stale saved UUID");
                }
                continue;
            }

            if (Instant.now().getEpochSecond() >= expireTime
                    && instance.getConfig().isClearOnExpire()) {
                if (removeExpiredEntity(entityUuid, readSavedLocation(section))) {
                    instance.stop();
                    scheduler.clearRestoreReservation(bossId);
                    expired++;
                } else {
                    scheduler.reserveRestore(bossId);
                    missing++;
                    if (attempt == 3 && readSavedLocation(section) == null) {
                        plugin.getLogger().warning("Expired boss '" + bossId
                                + "' has old state without a saved location. If its mob is gone,"
                                + " use /slboss state forget " + bossId
                                + " after verifying the world.");
                    }
                }
                continue;
            }

            scheduler.reserveRestore(bossId);
            Location savedLocation = readSavedLocation(section);
            if (instance.restore(entityUuid, spawnTime, expireTime,
                    section.getString("current-mythicmob"), savedLocation)) {
                restored++;
                scheduler.clearRestoreReservation(bossId);
            } else {
                missing++;
                if (attempt == 3 && savedLocation == null) {
                    plugin.getLogger().warning("Boss '" + bossId
                            + "' has old state without a saved location. If its mob is gone,"
                            + " use /slboss state forget " + bossId + " after verifying the world.");
                }
            }
        }

        if (removedOrphan) writeSync(yaml);

        if (missing > 0) {
            if (attempt == 3) {
                plugin.getLogger().warning("Boss state restore still waiting for " + missing
                        + " MythicMobs entities; their scheduled spawns are paused");
            }
            // Retry quickly before the first spawn check, then poll less often.
            long delay = attempt < 3 ? 5L : attempt < 6 ? 100L : 600L;
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> restoreFromState(scheduler, Math.min(attempt + 1, 6)), delay);
            return;
        }

        plugin.getLogger().info("Boss state restore: " + restored
                + " restored, " + expired + " expired, " + missing + " missing");

        saveAll();
    }

    private boolean removeExpiredEntity(UUID entityUuid, Location savedLocation) {
        Entity alreadyLoaded = Bukkit.getEntity(entityUuid);
        if (alreadyLoaded != null) {
            alreadyLoaded.remove();
            warnedUnverifiable.remove(entityUuid);
            return true;
        }
        if (savedLocation == null || savedLocation.getWorld() == null) {
            if (warnedUnverifiable.add(entityUuid)) {
                plugin.getLogger().warning("Cannot verify saved boss " + entityUuid
                        + ": exact saved location is unavailable; keeping its state");
            }
            return false;
        }
        try {
            savedLocation.getWorld().getChunkAt(savedLocation.getBlockX() >> 4,
                    savedLocation.getBlockZ() >> 4);
            Entity entity = Bukkit.getEntity(entityUuid);
            if (entity == null) {
                if (warnedUnverifiable.add(entityUuid)) {
                    plugin.getLogger().warning("Cannot verify saved boss " + entityUuid
                            + ": UUID was not found in its last saved chunk; keeping state");
                }
                return false;
            }
            entity.remove();
            warnedUnverifiable.remove(entityUuid);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Cannot verify expired boss " + entityUuid
                    + ": " + e.getMessage());
            return false;
        }
    }

    public void clear() {
        if (stateFile.exists())
            stateFile.delete();
    }

    // --- Private helpers ---

    private void putBossState(YamlConfiguration yaml, String bossId, BossInstance instance) {
        yaml.set(bossId + ".entity-uuid", instance.getEntityUuid().toString());
        yaml.set(bossId + ".spawn-time", instance.getSpawnEpoch());
        yaml.set(bossId + ".expire-time", instance.getExpireEpoch());
        yaml.set(bossId + ".boss-config-id", bossId);
        yaml.set(bossId + ".current-mythicmob", instance.getCurrentMythicMobId());

        Location location = instance.getLastKnownEntityLocation();
        if (location != null && location.getWorld() != null) {
            yaml.set(bossId + ".location.world", location.getWorld().getName());
            yaml.set(bossId + ".location.x", location.getX());
            yaml.set(bossId + ".location.y", location.getY());
            yaml.set(bossId + ".location.z", location.getZ());
        }
    }

    private Location readSavedLocation(ConfigurationSection section) {
        String worldName = section.getString("location.world");
        if (worldName == null || worldName.isBlank()) return null;
        Object rawX = section.get("location.x");
        Object rawY = section.get("location.y");
        Object rawZ = section.get("location.z");
        if (!(rawX instanceof Number x) || !(rawY instanceof Number y)
                || !(rawZ instanceof Number z)) return null;
        if (!Double.isFinite(x.doubleValue()) || !Double.isFinite(y.doubleValue())
                || !Double.isFinite(z.doubleValue())) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (warnedMissingWorlds.add(worldName)) {
                plugin.getLogger().warning("Saved boss world '" + worldName + "' is not loaded");
            }
            return null;
        }
        warnedMissingWorlds.remove(worldName);
        return new Location(world, x.doubleValue(), y.doubleValue(), z.doubleValue());
    }

    private boolean writeSync(YamlConfiguration yaml) {
        try {
            yaml.save(stateFile);
            plugin.debug("Saved boss state to " + stateFile.getName());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save boss state: " + e.getMessage());
            return false;
        }
    }

}
