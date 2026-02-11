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
 * Tracks player damage dealt to boss entities.
 * Uses MONITOR priority to capture final post-armor damage.
 */
public class DamageTracker implements Listener {
    private final SLBossSpawner plugin;

    // Outer: boss entity UUID -> Inner: player UUID -> cumulative damage
    private final Map<UUID, Map<UUID, Double>> damageMap = new HashMap<>();
    // Boss entity UUID -> player name cache
    private final Map<UUID, Map<UUID, String>> nameCache = new HashMap<>();
    // Boss entity UUID -> last hit player UUID
    private final Map<UUID, UUID> lastHitMap = new HashMap<>();
    // Set of tracked boss entity UUIDs
    private final Set<UUID> trackedBosses = new HashSet<>();

    public DamageTracker(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    public void startTracking(UUID bossEntityUuid) {
        trackedBosses.add(bossEntityUuid);
        damageMap.put(bossEntityUuid, new HashMap<>());
        nameCache.put(bossEntityUuid, new HashMap<>());
        plugin.debug("Started tracking damage for boss entity: " + bossEntityUuid);
    }

    public void stopTracking(UUID bossEntityUuid) {
        trackedBosses.remove(bossEntityUuid);
        damageMap.remove(bossEntityUuid);
        nameCache.remove(bossEntityUuid);
        lastHitMap.remove(bossEntityUuid);
    }

    public boolean isTracking(UUID bossEntityUuid) {
        return trackedBosses.contains(bossEntityUuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID victimUuid = event.getEntity().getUniqueId();
        if (!trackedBosses.contains(victimUuid))
            return;

        Player player = unwrapPlayer(event.getDamager());
        if (player == null)
            return;

        double damage = event.getFinalDamage();
        UUID playerUuid = player.getUniqueId();

        damageMap.computeIfAbsent(victimUuid, k -> new HashMap<>())
                .merge(playerUuid, damage, (a, b) -> a + b);

        nameCache.computeIfAbsent(victimUuid, k -> new HashMap<>())
                .put(playerUuid, player.getName());

        lastHitMap.put(victimUuid, playerUuid);
    }

    /**
     * Unwrap indirect damage to the originating player.
     * Handles: Projectile, Tameable, TNTPrimed, AreaEffectCloud.
     */
    private Player unwrapPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player player) {
                return player;
            }
        }

        if (damager instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player player) {
                return player;
            }
        }

        if (damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player player) {
                return player;
            }
        }

        if (damager instanceof AreaEffectCloud cloud) {
            if (cloud.getSource() instanceof Player player) {
                return player;
            }
        }

        return null;
    }

    /**
     * Build sorted DamageResult for a boss entity.
     * Call before cleanup to capture final state.
     */
    public DamageResult buildResult(UUID bossEntityUuid) {
        Map<UUID, Double> damages = damageMap.get(bossEntityUuid);
        Map<UUID, String> names = nameCache.get(bossEntityUuid);

        if (damages == null || damages.isEmpty()) {
            return DamageResult.empty();
        }

        double totalDamage = damages.values().stream().mapToDouble(Double::doubleValue).sum();

        List<DamageResult.PlayerDamageEntry> rankings = damages.entrySet().stream()
                .map(e -> new DamageResult.PlayerDamageEntry(
                        e.getKey(),
                        names != null ? names.getOrDefault(e.getKey(), "Unknown") : "Unknown",
                        e.getValue()))
                .sorted(Comparator.comparingDouble(DamageResult.PlayerDamageEntry::damage).reversed())
                .collect(Collectors.toList());

        UUID lastHit = lastHitMap.get(bossEntityUuid);

        return new DamageResult(rankings, lastHit, totalDamage);
    }

    public void clearAll() {
        trackedBosses.clear();
        damageMap.clear();
        nameCache.clear();
        lastHitMap.clear();
    }
}
