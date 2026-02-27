package dev.salyvn.slBossSpawner.listener;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Base class for boss fight trackers (damage, tank, support).
 * Provides shared start/stop/clear lifecycle and the data maps used by all trackers.
 */
public abstract class AbstractBossTracker<E> implements Listener {
    protected final SLBossSpawner plugin;
    protected final Set<UUID> trackedBosses = new HashSet<>();
    // boss UUID -> player UUID -> cumulative value
    protected final Map<UUID, Map<UUID, Double>> dataMap = new HashMap<>();
    // boss UUID -> player UUID -> player name
    protected final Map<UUID, Map<UUID, String>> nameCache = new HashMap<>();

    protected AbstractBossTracker(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    public void startTracking(UUID bossEntityUuid) {
        trackedBosses.add(bossEntityUuid);
        dataMap.put(bossEntityUuid, new HashMap<>());
        nameCache.put(bossEntityUuid, new HashMap<>());
        plugin.debug("Started " + getTrackerName() + " tracking for boss entity: " + bossEntityUuid);
    }

    public void stopTracking(UUID bossEntityUuid) {
        trackedBosses.remove(bossEntityUuid);
        dataMap.remove(bossEntityUuid);
        nameCache.remove(bossEntityUuid);
        onStopTracking(bossEntityUuid);
    }

    public boolean isTracking(UUID bossEntityUuid) {
        return trackedBosses.contains(bossEntityUuid);
    }

    public void clearAll() {
        trackedBosses.clear();
        dataMap.clear();
        nameCache.clear();
        onClearAll();
    }

    /** Hook: subclasses clean up extra state (e.g. lastHitMap). */
    protected void onStopTracking(UUID bossEntityUuid) {}

    /** Hook: subclasses clear extra state on full reset. */
    protected void onClearAll() {}

    protected abstract String getTrackerName();

    public abstract TrackingResult<E> buildResult(UUID bossEntityUuid);
}
