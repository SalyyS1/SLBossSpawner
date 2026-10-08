package dev.salyvn.slBossSpawner.placeholder;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossInstance;
import dev.salyvn.slBossSpawner.boss.BossScheduler;
import dev.salyvn.slBossSpawner.statistics.LeaderboardCache;
import dev.salyvn.slBossSpawner.statistics.LeaderboardRow;
import dev.salyvn.slBossSpawner.statistics.PlayerStatsRow;
import dev.salyvn.slBossSpawner.statistics.StatisticsStore;
import dev.salyvn.slBossSpawner.utils.TimeUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * M15 FIX: Removed stored LeaderboardCache reference. Now uses
 * plugin.getLeaderboardCache() for live reference that survives reload().
 */

public class BossPlaceholder extends PlaceholderExpansion {
    private final SLBossSpawner plugin;

    public BossPlaceholder(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "slbs";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SalyVn";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // Handle leaderboard/stats placeholders with underscore separator
        // %slbs_top_<metric>_<rank>_<name|value>%
        // %slbs_stats_<metric>%
        // %slbs_stats_<metric>_<period>%
        // %slbs_stats_<metric>_<period>_<boss>%
        if (params.startsWith("top_")) {
            return handleTopPlaceholder(params.substring(4));
        }
        if (params.startsWith("stats_") && player != null) {
            return handleStatsPlaceholder(player, params.substring(6));
        }

        // Existing boss placeholders use dot separator
        BossScheduler scheduler = plugin.getBossScheduler();

        String[] parts = params.split("\\.", 2);
        if (parts.length < 2) {
            if (params.equalsIgnoreCase("count")) {
                return String.valueOf(scheduler.getAllBossInstances().size());
            }
            return null;
        }

        String bossId = parts[0];
        String placeholder = parts[1];

        BossInstance instance = scheduler.getBossInstance(bossId);
        if (instance == null)
            return null;

        return switch (placeholder.toLowerCase()) {
            case "current_boss", "current" ->
                instance.isAlive()
                        ? (instance.getCurrentMythicMobId() != null
                                ? instance.getCurrentMythicMobId()
                                : instance.getConfig().getMythicMobId())
                        : "None";
            case "next" -> {
                long seconds = instance.getSecondsUntilNext();
                yield seconds < 0 ? "N/A" : TimeUtils.formatDuration(seconds);
            }
            case "next_formatted", "nextformatted" ->
                instance.getNextSpawnFormatted();
            case "expired", "expire" -> {
                long seconds = instance.getSecondsUntilExpire();
                yield seconds < 0 ? "N/A" : TimeUtils.formatDuration(seconds);
            }
            case "status" ->
                instance.isAlive() ? "Alive" : "Dead";
            case "mythicmob", "mob" ->
                instance.isAlive() && instance.getCurrentMythicMobId() != null
                        ? instance.getCurrentMythicMobId()
                        : instance.getConfig().getMythicMobId();
            default -> null;
        };
    }

    /**
     * %slbs_top_<metric>_<rank>_<name|value>% → ALL_TIME (backwards-compat)
     * %slbs_top_<period>_<metric>_<rank>_<name|value>% → specific period
     *
     * Supported periods: alltime, daily, weekly, monthly, quarterly, seasonal,
     * yearly
     */
    private String handleTopPlaceholder(String remainder) {
        LeaderboardCache leaderboardCache = plugin.getLeaderboardCache();
        if (leaderboardCache == null)
            return "---";

        String[] parts = remainder.split("_");
        // Try 4-part format first: period_metric_rank_field
        if (parts.length >= 4) {
            String[] resolved = resolvePeriod(parts[0]);
            if (resolved != null) {
                String metric = parts[1];
                int rank;
                try {
                    rank = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    return null;
                }
                String field = parts[3];

                List<LeaderboardRow> rows = leaderboardCache.getTopPlayers(
                        metric, resolved[0], resolved[1], "__global__");
                if (rank < 1 || rank > rows.size())
                    return "---";
                LeaderboardRow row = rows.get(rank - 1);
                return switch (field.toLowerCase()) {
                    case "name" -> row.playerName();
                    case "value" -> formatDouble(row.value());
                    default -> null;
                };
            }
        }

        // Fallback: 3-part format: metric_rank_field (ALL_TIME)
        if (parts.length < 3)
            return null;

        String metric = parts[0];
        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        String field = parts[2];

        List<LeaderboardRow> rows = leaderboardCache.getTopPlayers(
                metric, "ALL_TIME", "all", "__global__");

        if (rank < 1 || rank > rows.size())
            return "---";

        LeaderboardRow row = rows.get(rank - 1);
        return switch (field.toLowerCase()) {
            case "name" -> row.playerName();
            case "value" -> formatDouble(row.value());
            default -> null;
        };
    }

    /**
     * %slbs_stats_<metric>% → ALL_TIME / all / __global__
     * %slbs_stats_<metric>_<period>% → period / key / __global__
     * %slbs_stats_<metric>_<period>_<boss>% → period / key / boss
     * Reads the player's aggregate directly so a player outside the configured
     * leaderboard size still receives an accurate value.
     */
    private String handleStatsPlaceholder(OfflinePlayer player, String remainder) {
        StatisticsStore store = plugin.getStatisticsStore();
        if (store == null)
            return "0";

        String[] parts = remainder.split("_");
        if (parts.length < 1)
            return null;

        String metric = parts[0];
        String periodType = "ALL_TIME";
        String periodKey = "all";
        String bossId = "__global__";

        if (parts.length >= 2) {
            String periodAlias = parts[1].toLowerCase();
            String[] resolved = resolvePeriod(periodAlias);
            if (resolved == null)
                return null;
            periodType = resolved[0];
            periodKey = resolved[1];
        }
        if (parts.length >= 3) {
            bossId = parts[2];
        }

        String resolvedMetric = LeaderboardCache.metricToColumn(metric);
        if (resolvedMetric == null) {
            return null;
        }
        PlayerStatsRow stats = store.getPlayerStats(player.getUniqueId().toString(),
                periodType, periodKey, bossId);
        return stats == null ? "0" : formatDouble(metricValue(stats, resolvedMetric));
    }

    private double metricValue(PlayerStatsRow stats, String metric) {
        return switch (metric) {
            case "damage" -> stats.totalDamage();
            case "kills" -> stats.kills();
            case "participations" -> stats.participations();
            case "tank" -> stats.totalDamageTaken();
            case "support" -> stats.totalSupportScore();
            case "top1" -> stats.top1Count();
            case "top2" -> stats.top2Count();
            case "top3" -> stats.top3Count();
            case "top1tank" -> stats.top1TankCount();
            case "top1support" -> stats.top1SupportCount();
            case "bestdamage" -> stats.highestDamage();
            default -> 0D;
        };
    }

    /**
     * Resolve period alias to [periodType, periodKey]. Returns null if invalid.
     */
    private String[] resolvePeriod(String alias) {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        dev.salyvn.slBossSpawner.statistics.SeasonResolver sr = plugin.getStatisticsManager() != null
                ? plugin.getStatisticsManager().getSeasonResolver()
                : null;
        return switch (alias.toLowerCase()) {
            case "alltime" -> new String[] { "ALL_TIME", "all" };
            case "daily" ->
                new String[] { "DAILY", dev.salyvn.slBossSpawner.statistics.PeriodType.DAILY.computeKey(today, sr) };
            case "weekly", "week" ->
                new String[] { "WEEKLY", dev.salyvn.slBossSpawner.statistics.PeriodType.WEEKLY.computeKey(today, sr) };
            case "monthly", "month" -> new String[] { "MONTHLY",
                    dev.salyvn.slBossSpawner.statistics.PeriodType.MONTHLY.computeKey(today, sr) };
            case "quarterly", "quarter" -> new String[] { "QUARTERLY",
                    dev.salyvn.slBossSpawner.statistics.PeriodType.QUARTERLY.computeKey(today, sr) };
            case "seasonal", "season" -> new String[] { "SEASONAL",
                    dev.salyvn.slBossSpawner.statistics.PeriodType.SEASONAL.computeKey(today, sr) };
            case "yearly", "year" ->
                new String[] { "YEARLY", dev.salyvn.slBossSpawner.statistics.PeriodType.YEARLY.computeKey(today, sr) };
            default -> null;
        };
    }

    private String formatDouble(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}
