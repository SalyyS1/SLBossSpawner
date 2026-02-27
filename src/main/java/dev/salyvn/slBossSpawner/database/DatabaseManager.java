package dev.salyvn.slBossSpawner.database;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import java.sql.SQLException;
import java.util.List;

public interface DatabaseManager {
    void initialize() throws SQLException;
    void shutdown();

    void batchUpsertStats(List<StatsUpdateBatch> batch) throws SQLException;
    List<LeaderboardRow> getTopPlayers(String periodType, String periodKey,
                                       String bossId, String metric, int limit) throws SQLException;
    PlayerStatsRow getPlayerStats(String playerUuid, String periodType,
                                   String periodKey, String bossId) throws SQLException;
    int archivePeriod(String periodType, String periodKey) throws SQLException;

    static DatabaseManager create(SLBossSpawner plugin, String type) {
        return switch (type.toLowerCase()) {
            case "mysql" -> new MySQLProvider(plugin);
            default -> new SQLiteProvider(plugin);
        };
    }
}
