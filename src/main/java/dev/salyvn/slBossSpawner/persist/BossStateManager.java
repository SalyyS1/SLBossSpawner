package dev.salyvn.slBossSpawner.persist;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossInstance;
import dev.salyvn.slBossSpawner.boss.BossScheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Saves/loads alive boss state to boss-state.yml for server-restart survival.
 *
 * H11 FIX: saveSingle() and remove() snapshot the YAML on the main thread then
 * write the file on an async thread, avoiding I/O stalls on the tick loop.
 * saveSync() is kept for shutdown where async would race with process exit.
 */
public class BossStateManager {
    private final SLBossSpawner plugin;
    private final File stateFile;

    public BossStateManager(SLBossSpawner plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "boss-state.yml");
    }

    /** Save all currently alive bosses — always called synchronously (shutdown / reload). */
    public void saveAll() {
        BossScheduler scheduler = plugin.getBossScheduler();
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, BossInstance> entry : scheduler.getAllBossInstances().entrySet()) {
            BossInstance instance = entry.getValue();
            if (!instance.isAlive() || instance.getEntityUuid() == null) continue;

            String bossId = entry.getKey();
            yaml.set(bossId + ".entity-uuid", instance.getEntityUuid().toString());
            yaml.set(bossId + ".spawn-time", instance.getSpawnEpoch());
            yaml.set(bossId + ".expire-time", instance.getExpireEpoch());
            yaml.set(bossId + ".boss-config-id", bossId);
        }

        writeSync(yaml);
    }

    /**
     * H11: Save a single boss entry.
     * Snapshot on main thread, write file async.
     */
    public void saveSingle(BossInstance instance) {
        // Read existing YAML on main thread to build the snapshot
        YamlConfiguration yaml = stateFile.exists()
                ? YamlConfiguration.loadConfiguration(stateFile)
                : new YamlConfiguration();

        String bossId = instance.getConfig().getId();
        if (instance.isAlive() && instance.getEntityUuid() != null) {
            yaml.set(bossId + ".entity-uuid", instance.getEntityUuid().toString());
            yaml.set(bossId + ".spawn-time", instance.getSpawnEpoch());
            yaml.set(bossId + ".expire-time", instance.getExpireEpoch());
            yaml.set(bossId + ".boss-config-id", bossId);
        }

        // Write off the main thread
        writeAsync(yaml, "save boss state for " + bossId);
    }

    /**
     * H11: Remove a boss entry from the state file.
     * Snapshot on main thread, write file async.
     */
    public void remove(String bossId) {
        if (!stateFile.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        yaml.set(bossId, null);

        writeAsync(yaml, "remove boss state for " + bossId);
    }

    /** Synchronous save used at shutdown to prevent async/process-exit race. */
    public void saveSync() {
        saveAll();
    }

    /** Restore bosses from state file after server restart. */
    public void restoreFromState(BossScheduler scheduler) {
        if (!stateFile.exists()) return;

        YamlConfiguration yaml;
        try {
            yaml = YamlConfiguration.loadConfiguration(stateFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupted boss-state.yml, skipping restore: " + e.getMessage());
            return;
        }

        int restored = 0;
        int expired = 0;

        for (String bossId : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(bossId);
            if (section == null) continue;

            BossInstance instance = scheduler.getBossInstance(bossId);
            if (instance == null) {
                plugin.getLogger().warning("Boss '" + bossId
                        + "' in state file not found in schedules, skipping");
                continue;
            }

            String uuidStr = section.getString("entity-uuid");
            long spawnTime = section.getLong("spawn-time", 0);
            long expireTime = section.getLong("expire-time", 0);
            if (uuidStr == null || spawnTime == 0 || expireTime == 0) continue;

            UUID entityUuid;
            try {
                entityUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in state file for boss " + bossId);
                continue;
            }

            if (instance.restore(entityUuid, spawnTime, expireTime)) {
                restored++;
            } else {
                expired++;
            }
        }

        plugin.getLogger().info("Boss state restore: " + restored
                + " restored, " + expired + " expired/missing");

        clear();
        saveAll();
    }

    public void clear() {
        if (stateFile.exists()) stateFile.delete();
    }

    // --- Private helpers ---

    private void writeSync(YamlConfiguration yaml) {
        try {
            yaml.save(stateFile);
            plugin.debug("Saved boss state to " + stateFile.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save boss state: " + e.getMessage());
        }
    }

    private void writeAsync(YamlConfiguration yaml, String context) {
        // Serialise to string on calling (main) thread before handing off
        String yamlString = yaml.saveToString();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!stateFile.getParentFile().exists()) stateFile.getParentFile().mkdirs();
                java.nio.file.Files.writeString(stateFile.toPath(), yamlString);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to async-write boss state (" + context + "): "
                        + e.getMessage());
            }
        });
    }
}
