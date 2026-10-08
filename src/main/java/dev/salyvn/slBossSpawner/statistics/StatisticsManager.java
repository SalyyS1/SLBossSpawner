package dev.salyvn.slBossSpawner.statistics;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.listener.DamageResult;
import dev.salyvn.slBossSpawner.listener.SupportResult;
import dev.salyvn.slBossSpawner.listener.TankResult;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Core statistics processor. Converts boss death data into per-player
 * StatsUpdateBatch entries (per-boss + __global__, all 7 period types)
 * and sends them to the asynchronous YAML store. The store owns persistence and
 * debounces disk writes so a boss death never performs file I/O on the server
 * thread.
 */
public class StatisticsManager {
    private final SLBossSpawner plugin;
    private final StatisticsStore store;
    private final SeasonResolver seasonResolver;

    public StatisticsManager(SLBossSpawner plugin, StatisticsStore store, SeasonResolver seasonResolver) {
        this.plugin = plugin;
        this.store = store;
        this.seasonResolver = seasonResolver;
    }

    public void recordBossDeath(String bossId, DamageResult damage, TankResult tank,
                                SupportResult support, UUID killerUuid) {
        if (store == null) return;

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

        store.record(batch);
        plugin.debug("Recorded stats for " + allPlayers.size() + " players, " + batch.size() + " rows");
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

    public void shutdown() {
        // The StatisticsStore owns its writer and is flushed by the plugin
        // after the manager is detached from event processing.
    }

    public SeasonResolver getSeasonResolver() { return seasonResolver; }
}
