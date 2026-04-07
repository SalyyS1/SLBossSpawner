package dev.salyvn.slBossSpawner.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.salyvn.slBossSpawner.SLBossSpawner;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MySQL implementation of DatabaseManager using HikariCP connection pool.
 * Supports table prefix and configurable pool size from config.yml.
 */
public class MySQLProvider implements DatabaseManager {

    // Allowlist of valid metric column names to prevent SQL injection
    private static final Set<String> VALID_COLUMNS = Set.of(
            "total_damage", "kills", "participations", "total_damage_taken",
            "total_support_score", "top1_count", "top2_count", "top3_count",
            "top1_tank_count", "top1_support_count", "highest_damage");

    private final SLBossSpawner plugin;
    private HikariDataSource dataSource;
    private String prefix;

    public MySQLProvider(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws SQLException {
        var cfg = plugin.getConfigManager();
        this.prefix = cfg.getTablePrefix();

        String url = "jdbc:mysql://" + cfg.getMysqlHost() + ":" + cfg.getMysqlPort()
                + "/" + cfg.getMysqlDatabase()
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(cfg.getMysqlUsername());
        config.setPassword(cfg.getMysqlPassword());
        config.setMaximumPoolSize(cfg.getMysqlPoolSize());
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + prefix + "schema_version " +
                            "(version INT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            stmt.executeUpdate(
                    "INSERT IGNORE INTO " + prefix + "schema_version VALUES (1)");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + prefix + "stats (" +
                            "  id INT AUTO_INCREMENT PRIMARY KEY," +
                            "  player_uuid VARCHAR(36) NOT NULL," +
                            "  player_name VARCHAR(16) NOT NULL," +
                            "  boss_id VARCHAR(64) NOT NULL," +
                            "  period_type VARCHAR(16) NOT NULL," +
                            "  period_key VARCHAR(16) NOT NULL," +
                            "  kills INT DEFAULT 0," +
                            "  participations INT DEFAULT 0," +
                            "  total_damage DOUBLE DEFAULT 0," +
                            "  total_damage_taken DOUBLE DEFAULT 0," +
                            "  total_support_score DOUBLE DEFAULT 0," +
                            "  top1_count INT DEFAULT 0," +
                            "  top2_count INT DEFAULT 0," +
                            "  top3_count INT DEFAULT 0," +
                            "  top1_tank_count INT DEFAULT 0," +
                            "  top1_support_count INT DEFAULT 0," +
                            "  highest_damage DOUBLE DEFAULT 0," +
                            "  created_at BIGINT NOT NULL," +
                            "  updated_at BIGINT NOT NULL," +
                            "  UNIQUE KEY uq_player_boss_period (player_uuid, boss_id, period_type, period_key)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_" + prefix + "leaderboard " +
                            "ON " + prefix + "stats(period_type, period_key, boss_id)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_" + prefix + "player " +
                            "ON " + prefix + "stats(player_uuid, period_type)");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + prefix + "stats_archive (" +
                            "  id INT AUTO_INCREMENT PRIMARY KEY," +
                            "  player_uuid VARCHAR(36) NOT NULL," +
                            "  player_name VARCHAR(16) NOT NULL," +
                            "  boss_id VARCHAR(64) NOT NULL," +
                            "  period_type VARCHAR(16) NOT NULL," +
                            "  period_key VARCHAR(16) NOT NULL," +
                            "  kills INT DEFAULT 0," +
                            "  participations INT DEFAULT 0," +
                            "  total_damage DOUBLE DEFAULT 0," +
                            "  total_damage_taken DOUBLE DEFAULT 0," +
                            "  total_support_score DOUBLE DEFAULT 0," +
                            "  top1_count INT DEFAULT 0," +
                            "  top2_count INT DEFAULT 0," +
                            "  top3_count INT DEFAULT 0," +
                            "  top1_tank_count INT DEFAULT 0," +
                            "  top1_support_count INT DEFAULT 0," +
                            "  highest_damage DOUBLE DEFAULT 0," +
                            "  created_at BIGINT NOT NULL," +
                            "  updated_at BIGINT NOT NULL" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " + prefix + "seasons (" +
                            "  id INT AUTO_INCREMENT PRIMARY KEY," +
                            "  name VARCHAR(32) NOT NULL," +
                            "  start_date TEXT NOT NULL," +
                            "  end_date TEXT NOT NULL," +
                            "  active TINYINT(1) DEFAULT 1" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    @Override
    public void batchUpsertStats(List<StatsUpdateBatch> batch) throws SQLException {
        if (batch == null || batch.isEmpty())
            return;

        String sql = "INSERT INTO " + prefix + "stats " +
                "(player_uuid, player_name, boss_id, period_type, period_key," +
                " kills, participations, total_damage, total_damage_taken," +
                " total_support_score, top1_count, top2_count, top3_count," +
                " top1_tank_count, top1_support_count, highest_damage, created_at, updated_at)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" +
                " ON DUPLICATE KEY UPDATE" +
                "  player_name = VALUES(player_name)," +
                "  kills = kills + VALUES(kills)," +
                "  participations = participations + VALUES(participations)," +
                "  total_damage = total_damage + VALUES(total_damage)," +
                "  total_damage_taken = total_damage_taken + VALUES(total_damage_taken)," +
                "  total_support_score = total_support_score + VALUES(total_support_score)," +
                "  top1_count = top1_count + VALUES(top1_count)," +
                "  top2_count = top2_count + VALUES(top2_count)," +
                "  top3_count = top3_count + VALUES(top3_count)," +
                "  top1_tank_count = top1_tank_count + VALUES(top1_tank_count)," +
                "  top1_support_count = top1_support_count + VALUES(top1_support_count)," +
                "  highest_damage = GREATEST(highest_damage, VALUES(highest_damage))," +
                "  updated_at = VALUES(updated_at)";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                for (StatsUpdateBatch entry : batch) {
                    StatsUpdate u = entry.update();
                    ps.setString(1, entry.playerUuid());
                    ps.setString(2, entry.playerName());
                    ps.setString(3, entry.bossId());
                    ps.setString(4, entry.periodType());
                    ps.setString(5, entry.periodKey());
                    ps.setInt(6, u.killsDelta());
                    ps.setInt(7, u.participationsDelta());
                    ps.setDouble(8, u.damageDelta());
                    ps.setDouble(9, u.damageTakenDelta());
                    ps.setDouble(10, u.supportScoreDelta());
                    ps.setInt(11, u.top1Delta());
                    ps.setInt(12, u.top2Delta());
                    ps.setInt(13, u.top3Delta());
                    ps.setInt(14, u.top1TankDelta());
                    ps.setInt(15, u.top1SupportDelta());
                    ps.setDouble(16, u.fightDamage());
                    ps.setLong(17, now);
                    ps.setLong(18, now);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<LeaderboardRow> getTopPlayers(String periodType, String periodKey,
            String bossId, String metric, int limit) throws SQLException {
        // metric may already be a DB column name (from LeaderboardCache) or a
        // user-facing name
        String column;
        if (VALID_COLUMNS.contains(metric)) {
            column = metric; // already a valid DB column
        } else {
            column = metricToColumn(metric); // try resolving user-facing name
        }
        if (column == null || !VALID_COLUMNS.contains(column)) {
            throw new IllegalArgumentException("Invalid metric: " + metric);
        }

        // column is validated against allowlist — safe to embed in query
        String sql = "SELECT player_uuid, player_name, " + column + " AS value " +
                "FROM " + prefix + "stats WHERE period_type=? AND period_key=? AND boss_id=? " +
                "ORDER BY value DESC LIMIT ?";

        List<LeaderboardRow> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, periodType);
            ps.setString(2, periodKey);
            ps.setString(3, bossId);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    rows.add(new LeaderboardRow(
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getDouble("value"),
                            rank++));
                }
            }
        }
        return rows;
    }

    @Override
    public PlayerStatsRow getPlayerStats(String playerUuid, String periodType,
            String periodKey, String bossId) throws SQLException {
        String sql = "SELECT player_uuid, player_name, boss_id, period_type, period_key," +
                " kills, participations, total_damage, total_damage_taken," +
                " total_support_score, top1_count, top2_count, top3_count," +
                " top1_tank_count, top1_support_count, highest_damage" +
                " FROM " + prefix + "stats" +
                " WHERE player_uuid=? AND period_type=? AND period_key=? AND boss_id=?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, periodType);
            ps.setString(3, periodKey);
            ps.setString(4, bossId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStatsRow(
                            rs.getString("player_uuid"), rs.getString("player_name"),
                            rs.getString("boss_id"), rs.getString("period_type"),
                            rs.getString("period_key"), rs.getInt("kills"),
                            rs.getInt("participations"), rs.getDouble("total_damage"),
                            rs.getDouble("total_damage_taken"), rs.getDouble("total_support_score"),
                            rs.getInt("top1_count"), rs.getInt("top2_count"), rs.getInt("top3_count"),
                            rs.getInt("top1_tank_count"), rs.getInt("top1_support_count"),
                            rs.getDouble("highest_damage"));
                }
            }
        }
        return null;
    }

    @Override
    public int archivePeriod(String periodType, String periodKey) throws SQLException {
        String insertSql = "INSERT INTO " + prefix + "stats_archive SELECT * FROM " + prefix +
                "stats WHERE period_type=? AND period_key=?";
        String deleteSql = "DELETE FROM " + prefix + "stats WHERE period_type=? AND period_key=?";

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int archived;
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, periodType);
                    ps.setString(2, periodKey);
                    archived = ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setString(1, periodType);
                    ps.setString(2, periodKey);
                    ps.executeUpdate();
                }
                conn.commit();
                return archived;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private String metricToColumn(String metric) {
        return switch (metric.toLowerCase()) {
            case "damage" -> "total_damage";
            case "kills" -> "kills";
            case "participation", "participations" -> "participations";
            case "tank" -> "total_damage_taken";
            case "support" -> "total_support_score";
            case "top1" -> "top1_count";
            case "top2" -> "top2_count";
            case "top3" -> "top3_count";
            case "top1tank" -> "top1_tank_count";
            case "top1support" -> "top1_support_count";
            case "bestdamage" -> "highest_damage";
            default -> null;
        };
    }
}
