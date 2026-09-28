package dev.salyvn.slBossSpawner.listener;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks damage received by players FROM boss entities (tank tracking).
 * Uses MONITOR priority to capture final post-armor damage values.
 */
public class TankTracker extends AbstractBossTracker<TankResult.PlayerTankEntry> {

    public TankTracker(SLBossSpawner plugin) {
        super(plugin);
    }

    @Override
    protected String getTrackerName() { return "tank"; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Entity cause = event.getDamageSource().getCausingEntity();
        UUID bossUuid = cause != null ? unwrapBossUuid(cause) : null;
        if (bossUuid == null && event instanceof EntityDamageByEntityEvent byEntity) {
            bossUuid = unwrapBossUuid(byEntity.getDamager());
        }
        if (bossUuid == null || !trackedBosses.contains(bossUuid)) return;

        double damage = event.getFinalDamage();
        if (damage <= 0) return;
        UUID playerUuid = player.getUniqueId();

        dataMap.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .merge(playerUuid, damage, Double::sum);
        nameCache.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .put(playerUuid, player.getName());
    }

    /**
     * Resolve which tracked boss UUID is responsible for the damage.
     * Handles: direct hit, Projectile, AreaEffectCloud.
     */
    private UUID unwrapBossUuid(Entity damager) {
        UUID directUuid = damager.getUniqueId();
        if (trackedBosses.contains(directUuid)) return directUuid;

        if (damager instanceof Projectile p && p.getShooter() instanceof Entity source) {
            UUID sourceUuid = source.getUniqueId();
            if (trackedBosses.contains(sourceUuid)) return sourceUuid;
        }

        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity source) {
            UUID sourceUuid = source.getUniqueId();
            if (trackedBosses.contains(sourceUuid)) return sourceUuid;
        }

        return null;
    }

    @Override
    public TankResult buildResult(UUID bossEntityUuid) {
        Map<UUID, Double> damages = dataMap.get(bossEntityUuid);
        Map<UUID, String> names = nameCache.get(bossEntityUuid);
        if (damages == null || damages.isEmpty()) return TankResult.empty();

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
}
