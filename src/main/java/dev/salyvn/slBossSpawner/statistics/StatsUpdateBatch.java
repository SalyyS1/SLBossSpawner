package dev.salyvn.slBossSpawner.statistics;

public record StatsUpdateBatch(
        String playerUuid,
        String playerName,
        String bossId,
        String periodType,
        String periodKey,
        StatsUpdate update
) {
}
