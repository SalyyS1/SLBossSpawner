package dev.salyvn.slBossSpawner.statistics;

import dev.salyvn.slBossSpawner.SLBossSpawner;

import java.util.List;

/**
 * Compatibility facade for leaderboard callers.
 *
 * Statistics are already held in memory by {@link FileStatisticsStore}, so a
 * timer that repeatedly queried a persistent backend is unnecessary. Keeping this class
 * preserves the public plugin and PlaceholderAPI call sites while making every
 * read a bounded, thread-safe in-memory sort.
 */
public final class LeaderboardCache {
    private final StatisticsStore store;
    private final int leaderboardSize;

    public static final String[] METRICS = {
            "damage", "kills", "participations", "tank", "support",
            "top1", "top2", "top3", "top1tank", "top1support", "bestdamage"
    };

    public LeaderboardCache(SLBossSpawner plugin, StatisticsStore store,
                            SeasonResolver seasonResolver) {
        this.store = store;
        this.leaderboardSize = plugin.getConfigManager().getLeaderboardSize();
    }

    /** Kept for lifecycle compatibility; the store loads independently. */
    public void start() {
    }

    /** Kept for lifecycle compatibility; the store is stopped by the plugin. */
    public void stop() {
    }

    public List<LeaderboardRow> getTopPlayers(String metric, String periodType,
                                               String periodKey, String bossId) {
        if (store == null) {
            return List.of();
        }
        return store.getTopPlayers(periodType, periodKey, bossId, metric, leaderboardSize);
    }

    /**
     * Resolve a user-facing metric. The old method name is retained so command
     * and expansion integrations compiled against 3.0.x continue to work.
     */
    public static String metricToColumn(String metric) {
        if (metric == null) {
            return null;
        }
        for (String supported : METRICS) {
            if (supported.equalsIgnoreCase(metric)) {
                return supported;
            }
        }
        return null;
    }
}
