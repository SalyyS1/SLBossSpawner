package dev.salyvn.slBossSpawner.statistics;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.database.DatabaseManager;
import dev.salyvn.slBossSpawner.database.StatsUpdate;
import dev.salyvn.slBossSpawner.database.StatsUpdateBatch;
import dev.salyvn.slBossSpawner.listener.DamageResult;
import dev.salyvn.slBossSpawner.listener.SupportResult;
import dev.salyvn.slBossSpawner.listener.TankResult;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Core statistics processor. Converts boss death data into per-player
 * StatsUpdateBatch entries (per-boss + __global__, all 7 period types)
 * and writes them asynchronously. Runs hourly archival checks for period rollovers.
 */
public class StatisticsManager {
    private final SLBossSpawner plugin;
    private final DatabaseManager db;
    private final SeasonResolver seasonResolver;
    private BukkitTask archivalTask;
    private final Map<PeriodType, String> lastPeriodKeys = new EnumMap<>(PeriodType.class);

    public StatisticsManager(SLBossSpawner plugin, DatabaseManager db, SeasonResolver seasonResolver) {
        this.plugin = plugin;
        this.db = db;
        this.seasonResolver = seasonResolver;
        initializePeriodKeys();
    }

    private void initializePeriodKeys() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (PeriodType type : PeriodType.ARCHIVABLE) {
            lastPeriodKeys.put(type, type.computeKey(today, seasonResolver));
        }
    }

    public void recordBossDeath(String bossId, DamageResult damage, TankResult tank,
                                SupportResult support, UUID killerUuid) {
        if (db == null) return;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Compute current period keys for all types
        Map<PeriodType, String> periodKeys = new EnumMap<>(PeriodType.class);
        for (PeriodType type : PeriodType.values()) {
            periodKeys.put(type, type.computeKey(today, seasonResolver));
        }

        // Collect all unique players across all results
        Map<UUID, String> allPlayers = new LinkedHashMap<>();
        for (var e : damage.rankings()) allPlayers.putIfAbsent(e.playerUuid(), e.playerName());
        for (var e : tank.rankings()) allPlayers.putIfAbsent(e.playerUuid(), e.playerName());
        for (var e : support.rankings()) allPlayers.putIfAbsent(e.playerUuid(), e.playerName());

        if (allPlayers.isEmpty()) return;

        List<StatsUpdateBatch> batch = buildBatch(bossId, damage, tank, support, killerUuid, allPlayers, periodKeys);

        // Async write — never block the main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                db.batchUpsertStats(batch);
                plugin.debug("Recorded stats for " + allPlayers.size() + " players, " + batch.size() + " rows");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to record statistics: " + e.getMessage());
            }
        });
    }

    private List<StatsUpdateBatch> buildBatch(String bossId, DamageResult damage, TankResult tank,
                                               SupportResult support, UUID killerUuid,
                                               Map<UUID, String> allPlayers,
                                               Map<PeriodType, String> periodKeys) {
        List<StatsUpdateBatch> batch = new ArrayList<>();

        for (var entry : allPlayers.entrySet()) {
            UUID playerUuid = entry.getKey();
            String playerName = entry.getValue();

            int killsDelta = playerUuid.equals(killerUuid) ? 1 : 0;

            // Damage delta + rank
            double damageDelta = 0;
            int damageRank = damage.getRank(playerUuid, DamageResult.PlayerDamageEntry::playerUuid);
            for (var de : damage.rankings()) {
                if (de.playerUuid().equals(playerUuid)) { damageDelta = de.damage(); break; }
            }

            // Tank delta + rank
            double damageTakenDelta = 0;
            int tankRank = tank.getRank(playerUuid, TankResult.PlayerTankEntry::playerUuid);
            for (var te : tank.rankings()) {
                if (te.playerUuid().equals(playerUuid)) { damageTakenDelta = te.damageTaken(); break; }
            }

            // Support delta + rank
            double supportScoreDelta = 0;
            int supportRank = support.getRank(playerUuid, SupportResult.PlayerSupportEntry::playerUuid);
            for (var se : support.rankings()) {
                if (se.playerUuid().equals(playerUuid)) { supportScoreDelta = se.supportScore(); break; }
            }

            StatsUpdate update = new StatsUpdate(
                killsDelta, 1,
                damageDelta, damageTakenDelta, supportScoreDelta,
                (damageRank == 1) ? 1 : 0,
                (damageRank == 2) ? 1 : 0,
                (damageRank == 3) ? 1 : 0,
                (tankRank == 1) ? 1 : 0,
                (supportRank == 1) ? 1 : 0,
                damageDelta
            );

            String uuidStr = playerUuid.toString();

            // Per-boss + __global__ entry for each period type (7 types × 2 = 14 rows per player)
            for (PeriodType type : PeriodType.values()) {
                String key = periodKeys.get(type);
                batch.add(new StatsUpdateBatch(uuidStr, playerName, bossId, type.name(), key, update));
                batch.add(new StatsUpdateBatch(uuidStr, playerName, "__global__", type.name(), key, update));
            }
        }

        return batch;
    }

    public void startArchivalScheduler() {
        // 20 ticks/sec × 60 sec × 60 min = 72000 ticks per hour
        archivalTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkAndArchive, 72000L, 72000L);
    }

    private void checkAndArchive() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (PeriodType type : PeriodType.ARCHIVABLE) {
            String currentKey = type.computeKey(today, seasonResolver);
            String lastKey = lastPeriodKeys.get(type);
            if (lastKey != null && !lastKey.equals(currentKey)) {
                try {
                    int archived = db.archivePeriod(type.name(), lastKey);
                    plugin.getLogger().info(
                            "Archived " + type + " period " + lastKey + " (" + archived + " rows)");
                } catch (Exception e) {
                    plugin.getLogger().warning(
                            "Failed to archive " + type + " period " + lastKey + ": " + e.getMessage());
                }
                lastPeriodKeys.put(type, currentKey);
            }
        }
    }

    public void shutdown() {
        if (archivalTask != null) {
            archivalTask.cancel();
            archivalTask = null;
        }
    }

    public SeasonResolver getSeasonResolver() { return seasonResolver; }
}
