package dev.salyvn.slBossSpawner.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.salyvn.slBossSpawner.SLBossSpawner;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SQLite implementation of DatabaseManager using HikariCP connection pool.
 * Single-connection pool (maximumPoolSize=1) appropriate for SQLite.
 */
public class SQLiteProvider implements DatabaseManager {

    // Allowlist of valid metric column names to prevent SQL injection
    private static final Set<String> VALID_COLUMNS = Set.of(
            "total_damage", "kills", "participations", "total_damage_taken",
            "total_support_score", "top1_count", "top2_count", "top3_count",
            "top1_tank_count", "top1_support_count", "highest_damage");

    private final SLBossSpawner plugin;
    private HikariDataSource dataSource;

    public SQLiteProvider(SLBossSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws SQLException {
        plugin.getDataFolder().mkdirs();
        String dbPath = plugin.getDataFolder().getAbsolutePath() + "/statistics.db";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            stmt.executeUpdate(
                    "INSERT OR IGNORE INTO schema_version VALUES (1)");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS slbs_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL,
                        boss_id VARCHAR(64) NOT NULL,
                        period_type VARCHAR(16) NOT NULL,
                        period_key VARCHAR(16) NOT NULL,
                        kills INT DEFAULT 0,
                        participations INT DEFAULT 0,
                        total_damage DOUBLE DEFAULT 0,
                        total_damage_taken DOUBLE DEFAULT 0,
                        total_support_score DOUBLE DEFAULT 0,
                        top1_count INT DEFAULT 0,
                        top2_count INT DEFAULT 0,
                        top3_count INT DEFAULT 0,
                        top1_tank_count INT DEFAULT 0,
                        top1_support_count INT DEFAULT 0,
                        highest_damage DOUBLE DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        UNIQUE(player_uuid, boss_id, period_type, period_key)
                    )""");

            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_slbs_leaderboard ON slbs_stats(period_type, period_key, boss_id)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_slbs_player ON slbs_stats(player_uuid, period_type)");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS slbs_stats_archive (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(64) NOT NULL,
                        boss_id VARCHAR(64) NOT NULL,
                        period_type VARCHAR(16) NOT NULL,
                        period_key VARCHAR(16) NOT NULL,
                        kills INT DEFAULT 0,
                        participations INT DEFAULT 0,
                        total_damage DOUBLE DEFAULT 0,
                        total_damage_taken DOUBLE DEFAULT 0,
                        total_support_score DOUBLE DEFAULT 0,
                        top1_count INT DEFAULT 0,
                        top2_count INT DEFAULT 0,
                        top3_count INT DEFAULT 0,
                        top1_tank_count INT DEFAULT 0,
                        top1_support_count INT DEFAULT 0,
                        highest_damage DOUBLE DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS slbs_seasons (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(32) NOT NULL,
                        start_date TEXT NOT NULL,
                        end_date TEXT NOT NULL,
                        active INTEGER DEFAULT 1
                    )""");
        }
    }

    @Override
    public void batchUpsertStats(List<StatsUpdateBatch> batch) throws SQLException {
        if (batch == null || batch.isEmpty())
            return;

        String sql = """
                INSERT INTO slbs_stats (
                    player_uuid, player_name, boss_id, period_type, period_key,
                    kills, participations, total_damage, total_damage_taken,
                    total_support_score, top1_count, top2_count, top3_count,
                    top1_tank_count, top1_support_count, highest_damage,
                    created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(player_uuid, boss_id, period_type, period_key) DO UPDATE SET
                    player_name = excluded.player_name,
                    kills = kills + excluded.kills,
                    participations = participations + excluded.participations,
                    total_damage = total_damage + excluded.total_damage,
                    total_damage_taken = total_damage_taken + excluded.total_damage_taken,
                    total_support_score = total_support_score + excluded.total_support_score,
                    top1_count = top1_count + excluded.top1_count,
                    top2_count = top2_count + excluded.top2_count,
                    top3_count = top3_count + excluded.top3_count,
                    top1_tank_count = top1_tank_count + excluded.top1_tank_count,
                    top1_support_count = top1_support_count + excluded.top1_support_count,
                    highest_damage = MAX(highest_damage, excluded.highest_damage),
                    updated_at = excluded.updated_at
                """;

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
                    ps.executeUpdate();
                }
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
                "FROM slbs_stats WHERE period_type=? AND period_key=? AND boss_id=? " +
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
        String sql = """
                SELECT player_uuid, player_name, boss_id, period_type, period_key,
                       kills, participations, total_damage, total_damage_taken,
                       total_support_score, top1_count, top2_count, top3_count,
                       top1_tank_count, top1_support_count, highest_damage
                FROM slbs_stats
                WHERE player_uuid=? AND period_type=? AND period_key=? AND boss_id=?
                """;

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
        String insertSql = "INSERT INTO slbs_stats_archive SELECT * FROM slbs_stats WHERE period_type=? AND period_key=?";
        String deleteSql = "DELETE FROM slbs_stats WHERE period_type=? AND period_key=?";

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
