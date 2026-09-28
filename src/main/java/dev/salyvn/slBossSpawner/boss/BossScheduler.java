package dev.salyvn.slBossSpawner.boss;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.broadcast.SpawnWarningTask;
import dev.salyvn.slBossSpawner.config.ScheduleManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private boolean running;
    private final Map<String, BossInstance> bossInstances = new HashMap<>();
    private final Map<String, SpawnWarningTask> warningTasks = new HashMap<>();
    private final Map<String, BukkitTask> pendingSpawnTasks = new HashMap<>();
    private final Set<String> restoreReservations = new HashSet<>();

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
        running = true;
        for (BossInstance instance : bossInstances.values()) {
            instance.calculateNextSpawn();
        }
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSpawns, 20L, 20L);
    }

    public void stop() {
        running = false;
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        for (SpawnWarningTask task : warningTasks.values()) task.stop();
        warningTasks.clear();
        for (BukkitTask task : pendingSpawnTasks.values()) task.cancel();
        pendingSpawnTasks.clear();
        for (BossInstance instance : bossInstances.values()) instance.stop();
    }

    private void checkSpawns() {
        if (!running || plugin.getBossScheduler() != this) return;
        int spawnDelay = 0;
        for (Map.Entry<String, BossInstance> entry : bossInstances.entrySet()) {
            String bossId = entry.getKey();
            BossInstance instance = entry.getValue();
            BossConfig config = instance.getConfig();
            if (restoreReservations.contains(bossId) || pendingSpawnTasks.containsKey(bossId)) continue;

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
                scheduleSpawn(bossId, instance, spawnDelay);
                spawnDelay += 4;
            }
        }
    }

    /** Returns true when a boss spawned now or its delayed spawn was queued. */
    private boolean scheduleSpawn(String bossId, BossInstance instance, int delay) {
        if (delay == 0) {
            instance.spawn();
            instance.calculateNextSpawn();
            return instance.isAlive();
        }

        // Mark the boss pending before the next one-second schedule check.
        BukkitTask[] scheduled = new BukkitTask[1];
        scheduled[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingSpawnTasks.get(bossId) != scheduled[0]) return;
            pendingSpawnTasks.remove(bossId);
            if (!running || plugin.getBossScheduler() != this
                    || restoreReservations.contains(bossId)
                    || bossInstances.get(bossId) != instance) return;
            instance.spawn();
            instance.calculateNextSpawn();
        }, delay);
        pendingSpawnTasks.put(bossId, scheduled[0]);
        return true;
    }

    private void cancelPendingSpawn(String bossId) {
        BukkitTask pending = pendingSpawnTasks.remove(bossId);
        if (pending != null) pending.cancel();
    }

    /** Hold scheduled spawning while a saved MythicMob is still restoring. */
    public void reserveRestore(String bossId) {
        if (!bossInstances.containsKey(bossId)) return;
        restoreReservations.add(bossId);
        cancelPendingSpawn(bossId);
        SpawnWarningTask warning = warningTasks.remove(bossId);
        if (warning != null) warning.stop();
    }

    public void clearRestoreReservation(String bossId) {
        restoreReservations.remove(bossId);
    }

    public boolean isRestoreReserved(String bossId) {
        return restoreReservations.contains(bossId);
    }

    /** Explicitly release an unresolved saved UUID after an operator checks the world. */
    public boolean forgetSavedBossState(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance != null && instance.isAlive()) return false;
        if (!plugin.getBossStateManager().forgetSavedBoss(bossId)) return false;
        if (instance != null) instance.cleanup();
        clearRestoreReservation(bossId);
        return true;
    }

    /** Remove a saved mob before a manual command replaces its reserved slot. */
    private boolean prepareManualOverride(String bossId, BossInstance instance) {
        if (!isRestoreReserved(bossId)) return true;
        if (!plugin.getBossStateManager().discardReservedBoss(bossId)) {
            plugin.getLogger().warning("Manual action for boss '" + bossId
                    + "' was cancelled: its saved entity could not be checked safely.");
            return false;
        }
        instance.cleanup(); // Release ticket and stale tracking after verified removal.
        clearRestoreReservation(bossId);
        return true;
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

    public boolean forceSpawn(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance != null) {
            if (!prepareManualOverride(bossId, instance)) return false;
            cancelPendingSpawn(bossId);
            SpawnWarningTask warningTask = warningTasks.remove(bossId);
            if (warningTask != null) warningTask.stop();
            instance.spawn();
            instance.calculateNextSpawn();
            return instance.isAlive();
        } else {
            plugin.getLogger().warning("Boss not found: " + bossId);
            return false;
        }
    }

    /** Returns bosses spawned now or accepted into the short staggered queue. */
    public int forceSpawnAll() {
        int delay = 0;
        int accepted = 0;
        for (Map.Entry<String, BossInstance> entry : bossInstances.entrySet()) {
            String bossId = entry.getKey();
            BossInstance instance = entry.getValue();

            if (!prepareManualOverride(bossId, instance)) continue;
            cancelPendingSpawn(bossId);
            SpawnWarningTask warningTask = warningTasks.remove(bossId);
            if (warningTask != null) warningTask.stop();

            // Stagger spawns by 4 ticks (200ms) each to give MythicMobs time
            // to fully initialize each entity — prevents race condition where
            // getBukkitEntity() returns null for the 4th+ boss
            if (scheduleSpawn(bossId, instance, delay)) accepted++;
            delay += 4;
        }
        return accepted;
    }

    public boolean despawn(String bossId) {
        BossInstance instance = bossInstances.get(bossId);
        if (instance != null) {
            if (!prepareManualOverride(bossId, instance)) return false;
            cancelPendingSpawn(bossId);
            return instance.despawn();
        }
        return false;
    }

    /** Returns the number of boss IDs processed; reserved IDs can be skipped. */
    public int despawnAll() {
        int processed = 0;
        for (Map.Entry<String, BossInstance> entry : bossInstances.entrySet()) {
            if (!prepareManualOverride(entry.getKey(), entry.getValue())) continue;
            cancelPendingSpawn(entry.getKey());
            if (entry.getValue().despawn()) processed++;
        }
        return processed;
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
        String mobId = instance.getCurrentMythicMobId();
        return mobId != null ? mobId : instance.getConfig().getMythicMobId();
    }
}
