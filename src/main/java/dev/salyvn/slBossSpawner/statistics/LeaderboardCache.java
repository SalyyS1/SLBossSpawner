package dev.salyvn.slBossSpawner.statistics;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.database.DatabaseManager;
import dev.salyvn.slBossSpawner.database.LeaderboardRow;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory leaderboard cache refreshed asynchronously on a timer.
 * All reads are main-thread-safe via ConcurrentHashMap + atomic list replacement.
 */
public class LeaderboardCache {
    private final SLBossSpawner plugin;
    private final DatabaseManager db;
    private final SeasonResolver seasonResolver;
    private final int leaderboardSize;
    private final int refreshTicks;

    private final ConcurrentHashMap<String, List<LeaderboardRow>> cache = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    // All metrics that can be queried
    public static final String[] METRICS = {
        "damage", "kills", "participations", "tank", "support",
        "top1", "top2", "top3", "top1tank", "top1support", "bestdamage"
    };

    // DB column names corresponding to each metric
    private static final String[] DB_COLUMNS = {
        "total_damage", "kills", "participations", "total_damage_taken", "total_support_score",
        "top1_count", "top2_count", "top3_count", "top1_tank_count", "top1_support_count", "highest_damage"
    };

    public LeaderboardCache(SLBossSpawner plugin, DatabaseManager db, SeasonResolver seasonResolver) {
        this.plugin = plugin;
        this.db = db;
        this.seasonResolver = seasonResolver;
        this.leaderboardSize = plugin.getConfigManager().getLeaderboardSize();
        this.refreshTicks = plugin.getConfigManager().getCacheRefreshSeconds() * 20;
    }

    public void start() {
        // Initial refresh + repeating task
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::refreshAll, 20L, refreshTicks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        cache.clear();
    }

    /**
     * Main-thread-safe read. Returns empty list on cache miss.
     */
    public List<LeaderboardRow> getTopPlayers(String metric, String periodType, String periodKey, String bossId) {
        String key = buildKey(metric, periodType, periodKey, bossId);
        return cache.getOrDefault(key, Collections.emptyList());
    }

    /**
     * Async refresh. Queries ALL_TIME/__global__ and DAILY/today/__global__ for all metrics.
     */
    private void refreshAll() {
        try {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            String dailyKey = PeriodType.DAILY.computeKey(today, seasonResolver);

            for (int i = 0; i < METRICS.length; i++) {
                String metric = METRICS[i];
                String dbColumn = DB_COLUMNS[i];

                // ALL_TIME / all / __global__
                refreshSingle(metric, "ALL_TIME", "all", "__global__", dbColumn);

                // DAILY / today / __global__
                refreshSingle(metric, "DAILY", dailyKey, "__global__", dbColumn);
            }

            plugin.debug("Leaderboard cache refreshed (" + cache.size() + " entries)");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh leaderboard cache: " + e.getMessage());
        }
    }

    private void refreshSingle(String metric, String periodType, String periodKey, String bossId, String dbColumn) {
        try {
            List<LeaderboardRow> rows = db.getTopPlayers(periodType, periodKey, bossId, dbColumn, leaderboardSize);
            String key = buildKey(metric, periodType, periodKey, bossId);
            cache.put(key, List.copyOf(rows));
        } catch (Exception e) {
            plugin.getLogger().warning("Cache refresh failed for " + metric + "/" + periodType + ": " + e.getMessage());
        }
    }

    private String buildKey(String metric, String periodType, String periodKey, String bossId) {
        return metric + ":" + periodType + ":" + periodKey + ":" + bossId;
    }

    /**
     * Resolve metric name to DB column name. Returns null if invalid.
     */
    public static String metricToColumn(String metric) {
        for (int i = 0; i < METRICS.length; i++) {
            if (METRICS[i].equalsIgnoreCase(metric)) {
                return DB_COLUMNS[i];
            }
        }
        return null;
    }
}
