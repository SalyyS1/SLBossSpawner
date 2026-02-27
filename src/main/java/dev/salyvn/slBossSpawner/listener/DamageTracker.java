package dev.salyvn.slBossSpawner.listener;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks player damage dealt to boss entities.
 * Uses MONITOR priority to capture final post-armor damage values.
 */
public class DamageTracker extends AbstractBossTracker<DamageResult.PlayerDamageEntry> {
    // boss UUID -> last-hit player UUID
    private final Map<UUID, UUID> lastHitMap = new HashMap<>();

    public DamageTracker(SLBossSpawner plugin) {
        super(plugin);
    }

    @Override
    protected String getTrackerName() { return "damage"; }

    @Override
    protected void onStopTracking(UUID bossEntityUuid) {
        lastHitMap.remove(bossEntityUuid);
    }

    @Override
    protected void onClearAll() {
        lastHitMap.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID victimUuid = event.getEntity().getUniqueId();
        if (!trackedBosses.contains(victimUuid)) return;

        Player player = unwrapPlayer(event.getDamager());
        if (player == null) return;

        double damage = event.getFinalDamage();
        UUID playerUuid = player.getUniqueId();

        dataMap.computeIfAbsent(victimUuid, k -> new HashMap<>())
                .merge(playerUuid, damage, Double::sum);
        nameCache.computeIfAbsent(victimUuid, k -> new HashMap<>())
                .put(playerUuid, player.getName());
        lastHitMap.put(victimUuid, playerUuid);
    }

    /**
     * Unwrap indirect damage to the originating player.
     * Handles: direct, Projectile, Tameable, TNTPrimed, AreaEffectCloud.
     */
    private Player unwrapPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile p && p.getShooter() instanceof Player player) return player;
        if (damager instanceof Tameable t && t.getOwner() instanceof Player player) return player;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return player;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return player;
        return null;
    }

    @Override
    public DamageResult buildResult(UUID bossEntityUuid) {
        Map<UUID, Double> damages = dataMap.get(bossEntityUuid);
        Map<UUID, String> names = nameCache.get(bossEntityUuid);
        if (damages == null || damages.isEmpty()) return DamageResult.empty();

        double totalDamage = damages.values().stream().mapToDouble(Double::doubleValue).sum();
        List<DamageResult.PlayerDamageEntry> rankings = damages.entrySet().stream()
                .map(e -> new DamageResult.PlayerDamageEntry(
                        e.getKey(),
                        names != null ? names.getOrDefault(e.getKey(), "Unknown") : "Unknown",
                        e.getValue()))
                .sorted(Comparator.comparingDouble(DamageResult.PlayerDamageEntry::damage).reversed())
                .collect(Collectors.toList());

        return new DamageResult(rankings, lastHitMap.get(bossEntityUuid), totalDamage);
    }

    public UUID getLastHit(UUID bossEntityUuid) {
        return lastHitMap.get(bossEntityUuid);
    }
}
