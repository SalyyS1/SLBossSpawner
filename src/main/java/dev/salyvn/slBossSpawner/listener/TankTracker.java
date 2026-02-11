package dev.salyvn.slBossSpawner.listener;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks damage received by players FROM boss entities (tank tracking).
 * Uses MONITOR priority to capture final post-armor damage.
 */
public class TankTracker implements Listener {
    private final SLBossSpawner plugin;

    // Outer: boss entity UUID -> Inner: player UUID -> cumulative damage taken
    private final Map<UUID, Map<UUID, Double>> tankMap = new HashMap<>();
    // Boss entity UUID -> player name cache
    private final Map<UUID, Map<UUID, String>> nameCache = new HashMap<>();
    // Set of tracked boss entity UUIDs
    private final Set<UUID> trackedBosses = new HashSet<>();

    public TankTracker(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    public void startTracking(UUID bossEntityUuid) {
        trackedBosses.add(bossEntityUuid);
        tankMap.put(bossEntityUuid, new HashMap<>());
        nameCache.put(bossEntityUuid, new HashMap<>());
        plugin.debug("Started tank tracking for boss entity: " + bossEntityUuid);
    }

    public void stopTracking(UUID bossEntityUuid) {
        trackedBosses.remove(bossEntityUuid);
        tankMap.remove(bossEntityUuid);
        nameCache.remove(bossEntityUuid);
    }

    public boolean isTracking(UUID bossEntityUuid) {
        return trackedBosses.contains(bossEntityUuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // We need: damager is the boss, victim is a player
        if (!(event.getEntity() instanceof Player player))
            return;

        UUID bossUuid = unwrapBossUuid(event.getDamager());
        if (bossUuid == null || !trackedBosses.contains(bossUuid))
            return;

        double damage = event.getFinalDamage();
        UUID playerUuid = player.getUniqueId();

        tankMap.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .merge(playerUuid, damage, (a, b) -> a + b);

        nameCache.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .put(playerUuid, player.getName());
    }

    /**
     * Unwrap indirect damage from boss to find the boss entity UUID.
     * Handles: direct hit, Projectile, AreaEffectCloud.
     */
    private UUID unwrapBossUuid(Entity damager) {
        UUID directUuid = damager.getUniqueId();
        if (trackedBosses.contains(directUuid)) {
            return directUuid;
        }

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Entity source) {
                UUID sourceUuid = source.getUniqueId();
                if (trackedBosses.contains(sourceUuid)) {
                    return sourceUuid;
                }
            }
        }

        if (damager instanceof AreaEffectCloud cloud) {
            if (cloud.getSource() instanceof Entity source) {
                UUID sourceUuid = source.getUniqueId();
                if (trackedBosses.contains(sourceUuid)) {
                    return sourceUuid;
                }
            }
        }

        return null;
    }

    /**
     * Build sorted TankResult for a boss entity.
     * Call before cleanup to capture final state.
     */
    public TankResult buildResult(UUID bossEntityUuid) {
        Map<UUID, Double> damages = tankMap.get(bossEntityUuid);
        Map<UUID, String> names = nameCache.get(bossEntityUuid);

        if (damages == null || damages.isEmpty()) {
            return TankResult.empty();
        }

        double totalDamage = damages.values().stream().mapToDouble(Double::doubleValue).sum();

        List<TankResult.PlayerTankEntry> rankings = damages.entrySet().stream()
                .map(e -> new TankResult.PlayerTankEntry(
                        e.getKey(),
                        names != null ? names.getOrDefault(e.getKey(), "Unknown") : "Unknown",
                        e.getValue()))
                .sorted(Comparator.comparingDouble(TankResult.PlayerTankEntry::damageTaken).reversed())
                .collect(Collectors.toList());

        return new TankResult(rankings, totalDamage);
    }

    public void clearAll() {
        trackedBosses.clear();
        tankMap.clear();
        nameCache.clear();
    }
}
