package dev.salyvn.slBossSpawner.listener;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks harmful potion/debuff effects applied to boss entities by players.
 * Score formula: (amplifier + 1) * (durationTicks / 20.0)
 *
 * Attribution: EntityPotionEffectEvent doesn't tell who caused the effect,
 * so we track splash/lingering potion throwers via PotionSplashEvent and
 * AreaEffectCloudApplyEvent, maintaining a short-lived attribution cache.
 */
public class SupportTracker implements Listener {
    private final SLBossSpawner plugin;

    // Boss entity UUID -> player UUID -> cumulative support score
    private final Map<UUID, Map<UUID, Double>> supportMap = new HashMap<>();
    // Boss entity UUID -> player name cache
    private final Map<UUID, Map<UUID, String>> nameCache = new HashMap<>();
    // Set of tracked boss entity UUIDs
    private final Set<UUID> trackedBosses = new HashSet<>();

    // Attribution cache: boss entity UUID -> (player UUID, timestamp tick)
    // Tracks who last threw a potion at each boss (expires after 5 ticks)
    private final Map<UUID, AttributionEntry> attributionCache = new HashMap<>();

    private static final long ATTRIBUTION_EXPIRY_TICKS = 5;

    private static final Set<PotionEffectType> HARMFUL_EFFECTS = Set.of(
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.INSTANT_DAMAGE,
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.HUNGER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.LEVITATION,
            PotionEffectType.UNLUCK,
            PotionEffectType.DARKNESS,
            PotionEffectType.INFESTED,
            PotionEffectType.OOZING,
            PotionEffectType.WEAVING,
            PotionEffectType.WIND_CHARGED);

    public SupportTracker(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    public void startTracking(UUID bossEntityUuid) {
        trackedBosses.add(bossEntityUuid);
        supportMap.put(bossEntityUuid, new HashMap<>());
        nameCache.put(bossEntityUuid, new HashMap<>());
        plugin.debug("Started support tracking for boss entity: " + bossEntityUuid);
    }

    public void stopTracking(UUID bossEntityUuid) {
        trackedBosses.remove(bossEntityUuid);
        supportMap.remove(bossEntityUuid);
        nameCache.remove(bossEntityUuid);
        attributionCache.remove(bossEntityUuid);
    }

    public boolean isTracking(UUID bossEntityUuid) {
        return trackedBosses.contains(bossEntityUuid);
    }

    /**
     * Track splash potion hits on boss — records who threw it for attribution.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player player))
            return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            UUID entityUuid = affected.getUniqueId();
            if (trackedBosses.contains(entityUuid)) {
                long currentTick = affected.getWorld().getGameTime();
                attributionCache.put(entityUuid,
                        new AttributionEntry(player.getUniqueId(), player.getName(), currentTick));
                plugin.debug("Attribution: " + player.getName() + " splashed potion on boss " + entityUuid);
            }
        }
    }

    /**
     * Track lingering potion cloud applications on boss.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaEffectCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player player))
            return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            UUID entityUuid = affected.getUniqueId();
            if (trackedBosses.contains(entityUuid)) {
                long currentTick = affected.getWorld().getGameTime();
                attributionCache.put(entityUuid,
                        new AttributionEntry(player.getUniqueId(), player.getName(), currentTick));
            }
        }
    }

    /**
     * Track when a harmful potion effect is applied/changed on a tracked boss.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        UUID bossUuid = event.getEntity().getUniqueId();
        if (!trackedBosses.contains(bossUuid))
            return;

        // Only count new effects or amplified/extended effects
        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED
                && action != EntityPotionEffectEvent.Action.CHANGED)
            return;

        // Only count harmful effects
        if (event.getNewEffect() == null)
            return;
        PotionEffectType effectType = event.getNewEffect().getType();
        if (!HARMFUL_EFFECTS.contains(effectType))
            return;

        // Look up attribution — who caused this effect?
        AttributionEntry attribution = attributionCache.get(bossUuid);
        if (attribution == null)
            return;

        // Check if attribution is still fresh (within expiry ticks)
        long currentTick = event.getEntity().getWorld().getGameTime();
        if (currentTick - attribution.tick() > ATTRIBUTION_EXPIRY_TICKS) {
            attributionCache.remove(bossUuid);
            return;
        }

        // Calculate support score: (amplifier + 1) * (duration in seconds)
        int amplifier = event.getNewEffect().getAmplifier();
        int durationTicks = event.getNewEffect().getDuration();
        double score = (amplifier + 1) * (durationTicks / 20.0);

        // For INSTANT_DAMAGE, use amplifier only (no duration)
        if (effectType == PotionEffectType.INSTANT_DAMAGE) {
            score = (amplifier + 1) * 10.0;
        }

        UUID playerUuid = attribution.playerUuid();

        supportMap.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .merge(playerUuid, score, (a, b) -> a + b);

        nameCache.computeIfAbsent(bossUuid, k -> new HashMap<>())
                .put(playerUuid, attribution.playerName());

        plugin.debug("Support score +" + String.format("%.1f", score) + " for "
                + attribution.playerName() + " (" + effectType.getKey() + " on boss " + bossUuid + ")");
    }

    /**
     * Build sorted SupportResult for a boss entity.
     */
    public SupportResult buildResult(UUID bossEntityUuid) {
        Map<UUID, Double> scores = supportMap.get(bossEntityUuid);
        Map<UUID, String> names = nameCache.get(bossEntityUuid);

        if (scores == null || scores.isEmpty()) {
            return SupportResult.empty();
        }

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

    public void clearAll() {
        trackedBosses.clear();
        supportMap.clear();
        nameCache.clear();
        attributionCache.clear();
    }

    private record AttributionEntry(UUID playerUuid, String playerName, long tick) {
    }
}
