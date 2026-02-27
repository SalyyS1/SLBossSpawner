package dev.salyvn.slBossSpawner.listener;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks harmful potion/debuff effects applied to boss entities by players.
 *
 * Fixes applied:
 * H1 - attributionCache is now per-cloud per-boss (Map<bossUUID, Map<cloudUUID, entry>>)
 *      so multiple simultaneous lingering clouds don't overwrite each other.
 * H2 - ATTRIBUTION_EXPIRY_TICKS raised from 5 to 40 (2 seconds) to survive event ordering lag.
 * M1 - HARMFUL_EFFECTS built with try-catch for 1.21+ constants so it compiles on older servers.
 */
public class SupportTracker extends AbstractBossTracker<SupportResult.PlayerSupportEntry> {

    // H1 FIX: boss UUID -> (cloud/potion UUID -> attribution entry)
    private final Map<UUID, Map<UUID, AttributionEntry>> attributionCache = new HashMap<>();

    // H2 FIX: raised from 5 to 40 ticks (~2 seconds)
    private static final long ATTRIBUTION_EXPIRY_TICKS = 40;

    // M1 FIX: version-safe set — 1.21+ fields wrapped in try-catch
    private static final Set<PotionEffectType> HARMFUL_EFFECTS;
    static {
        Set<PotionEffectType> effects = new HashSet<>();
        effects.add(PotionEffectType.SLOWNESS);
        effects.add(PotionEffectType.MINING_FATIGUE);
        effects.add(PotionEffectType.INSTANT_DAMAGE);
        effects.add(PotionEffectType.NAUSEA);
        effects.add(PotionEffectType.BLINDNESS);
        effects.add(PotionEffectType.HUNGER);
        effects.add(PotionEffectType.WEAKNESS);
        effects.add(PotionEffectType.POISON);
        effects.add(PotionEffectType.WITHER);
        effects.add(PotionEffectType.LEVITATION);
        effects.add(PotionEffectType.UNLUCK);
        effects.add(PotionEffectType.DARKNESS);
        // 1.21+ constants — silently ignored on older server versions
        for (String name : List.of("INFESTED", "OOZING", "WEAVING", "WIND_CHARGED")) {
            try {
                effects.add((PotionEffectType) PotionEffectType.class.getField(name).get(null));
            } catch (NoSuchFieldException | IllegalAccessException ignored) {}
        }
        HARMFUL_EFFECTS = Collections.unmodifiableSet(effects);
    }

    public SupportTracker(SLBossSpawner plugin) {
        super(plugin);
    }

    @Override
    protected String getTrackerName() { return "support"; }

    @Override
    protected void onStopTracking(UUID bossEntityUuid) {
        attributionCache.remove(bossEntityUuid);
    }

    @Override
    protected void onClearAll() {
        attributionCache.clear();
    }

    /** Record who threw a splash potion that hit the boss. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player player)) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            UUID entityUuid = affected.getUniqueId();
            if (!trackedBosses.contains(entityUuid)) continue;

            long tick = affected.getWorld().getGameTime();
            // H1: use potion entity UUID as the cloud key for splash potions
            UUID potionUuid = event.getPotion().getUniqueId();
            attributionCache.computeIfAbsent(entityUuid, k -> new HashMap<>())
                    .put(potionUuid, new AttributionEntry(player.getUniqueId(), player.getName(), tick));
            plugin.debug("Attribution: " + player.getName() + " splashed potion on boss " + entityUuid);
        }
    }

    /** Record who owns a lingering cloud that ticked on the boss. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player player)) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            UUID entityUuid = affected.getUniqueId();
            if (!trackedBosses.contains(entityUuid)) continue;

            long tick = affected.getWorld().getGameTime();
            // H1: use cloud UUID so multiple clouds don't collide
            attributionCache.computeIfAbsent(entityUuid, k -> new HashMap<>())
                    .put(cloud.getUniqueId(), new AttributionEntry(player.getUniqueId(), player.getName(), tick));
        }
    }

    /** Score harmful effects applied to the boss and credit the most-recent attributed player. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        UUID bossUuid = event.getEntity().getUniqueId();
        if (!trackedBosses.contains(bossUuid)) return;

        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED
                && action != EntityPotionEffectEvent.Action.CHANGED) return;

        if (event.getNewEffect() == null) return;
        PotionEffectType effectType = event.getNewEffect().getType();
        if (!HARMFUL_EFFECTS.contains(effectType)) return;

        // H1: scan all cloud-keyed entries, pick the freshest non-expired one
        Map<UUID, AttributionEntry> cloudMap = attributionCache.get(bossUuid);
        if (cloudMap == null || cloudMap.isEmpty()) return;

        long currentTick = event.getEntity().getWorld().getGameTime();

        AttributionEntry best = null;
        for (AttributionEntry attr : cloudMap.values()) {
            if (currentTick - attr.tick() <= ATTRIBUTION_EXPIRY_TICKS) {
                if (best == null || attr.tick() > best.tick()) {
                    best = attr;
                }
            }
        }

        // Clean up expired entries
        cloudMap.entrySet().removeIf(e -> currentTick - e.getValue().tick() > ATTRIBUTION_EXPIRY_TICKS);

        if (best == null) return;

        int amplifier = event.getNewEffect().getAmplifier();
        int durationTicks = event.getNewEffect().getDuration();
        double score = (amplifier + 1) * (durationTicks / 20.0);
        if (effectType == PotionEffectType.INSTANT_DAMAGE) {
            score = (amplifier + 1) * 10.0;
        }

        UUID playerUuid = best.playerUuid();
        dataMap.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .merge(playerUuid, score, Double::sum);
        nameCache.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .put(playerUuid, best.playerName());

        plugin.debug("Support score +" + String.format("%.1f", score) + " for "
                + best.playerName() + " (" + effectType.getKey() + " on boss " + bossUuid + ")");
    }

    @Override
    public SupportResult buildResult(UUID bossEntityUuid) {
        Map<UUID, Double> scores = dataMap.get(bossEntityUuid);
        Map<UUID, String> names = nameCache.get(bossEntityUuid);
        if (scores == null || scores.isEmpty()) return SupportResult.empty();

        double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        List<SupportResult.PlayerSupportEntry> rankings = scores.entrySet().stream()
                .map(e -> new SupportResult.PlayerSupportEntry(
                        e.getKey(),
                        names != null ? names.getOrDefault(e.getKey(), "Unknown") : "Unknown",
                        e.getValue()))
                .sorted(Comparator.comparingDouble(SupportResult.PlayerSupportEntry::supportScore).reversed())
                .collect(Collectors.toList());

        return new SupportResult(rankings, totalScore);
    }

    private record AttributionEntry(UUID playerUuid, String playerName, long tick) {}
}
