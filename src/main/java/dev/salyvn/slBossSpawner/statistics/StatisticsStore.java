package dev.salyvn.slBossSpawner.statistics;

import java.util.List;

/**
 * Storage contract for aggregated boss statistics. Implementations must keep
 * reads safe while a death event is updating the in-memory aggregates.
 */
public interface StatisticsStore {
    void start();

    void record(List<StatsUpdateBatch> updates);

    PlayerStatsRow getPlayerStats(String playerUuid, String periodType,
                                  String periodKey, String bossId);

    List<LeaderboardRow> getTopPlayers(String periodType, String periodKey,
                                       String bossId, String metric, int limit);

    void shutdown();
}
