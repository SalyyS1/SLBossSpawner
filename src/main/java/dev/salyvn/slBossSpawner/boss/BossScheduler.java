package dev.salyvn.slBossSpawner.boss;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.broadcast.SpawnWarningTask;
import dev.salyvn.slBossSpawner.config.ScheduleManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all boss instances and their spawn scheduling.
 *
 * H10 FIX: Maintains an entityIndex (entityUUID -> BossInstance) so
 * findByEntityUuid() is O(1) instead of O(n) linear scan.
 */
public class BossScheduler {
    private final SLBossSpawner plugin;
    private final ScheduleManager scheduleManager;
    private BukkitTask checkTask;
    private final Map<String, BossInstance> bossInstances = new HashMap<>();
    private final Map<String, SpawnWarningTask> warningTasks = new HashMap<>();

    // H10: direct entity UUID -> instance index
    private final Map<UUID, BossInstance> entityIndex = new HashMap<>();

    public BossScheduler(SLBossSpawner plugin, ScheduleManager scheduleManager) {
        this.plugin = plugin;
        this.scheduleManager = scheduleManager;
        initializeBosses();
    }

    private void initializeBosses() {
        bossInstances.clear();
        entityIndex.clear();

        for (Map.Entry<String, BossConfig> entry : scheduleManager.getAllBossConfigs().entrySet()) {
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
        for (SpawnWarningTask task : warningTasks.values()) task.stop();
        warningTasks.clear();
        for (BossInstance instance : bossInstances.values()) instance.stop();
    }

    private void checkSpawns() {
        for (Map.Entry<String, BossInstance> entry : bossInstances.entrySet()) {
            String bossId = entry.getKey();
            BossInstance instance = entry.getValue();
            BossConfig config = instance.getConfig();

            // Start warning task if within max warning interval
            if (!instance.isAlive() && instance.getSecondsUntilNext() > 0) {
                long secondsUntilSpawn = instance.getSecondsUntilNext();
                SpawnWarningTask warningTask = warningTasks.get(bossId);

                if (warningTask == null || !warningTask.isRunning()) {
                    int maxInterval = config.getSpawnWarningIntervals().stream()
                            .mapToInt(Integer::intValue).max().orElse(30);
                    long maxWarningSeconds = maxInterval * 60L;

                    if (secondsUntilSpawn <= maxWarningSeconds && secondsUntilSpawn > 0) {
                        SpawnWarningTask newTask = new SpawnWarningTask(
                                plugin, plugin.getBroadcastManager(), config);
                        newTask.start(secondsUntilSpawn);
                        warningTasks.put(bossId, newTask);
                    }
                }
            }

            if (instance.shouldSpawn()) {
                SpawnWarningTask warningTask = warningTasks.remove(bossId);
                if (warningTask != null) warningTask.stop();
                instance.spawn();
                instance.calculateNextSpawn();
            }
        }
    }

    // --- H10: entity index management ---

    /** Called by BossInstance.spawn() and BossInstance.restore() after entity is known. */
    public void registerEntity(UUID entityUuid, BossInstance instance) {
        entityIndex.put(entityUuid, instance);
    }

    /** Called by BossInstance.cleanup() before entityUuid is nulled. */
    public void unregisterEntity(UUID entityUuid) {
        entityIndex.remove(entityUuid);
    }

    /** O(1) lookup — replaces former O(n) linear scan. */
    public BossInstance findByEntityUuid(UUID uuid) {
        return entityIndex.get(uuid);
    }

    // --- Public API ---

    public void forceSpawn(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance != null) {
            SpawnWarningTask warningTask = warningTasks.remove(bossId);
            if (warningTask != null) warningTask.stop();
            instance.spawn();
            instance.calculateNextSpawn();
        } else {
            plugin.getLogger().warning("Boss not found: " + bossId);
        }
    }

    public void forceSpawnAll() {
        for (Map.Entry<String, BossInstance> entry : bossInstances.entrySet()) {
            SpawnWarningTask warningTask = warningTasks.remove(entry.getKey());
            if (warningTask != null) warningTask.stop();
            entry.getValue().spawn();
            entry.getValue().calculateNextSpawn();
        }
    }

    public void despawn(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance != null) instance.despawn();
    }

    public void despawnAll() {
        for (BossInstance instance : bossInstances.values()) instance.despawn();
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
