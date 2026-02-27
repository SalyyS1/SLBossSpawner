package dev.salyvn.slBossSpawner.database;

public record StatsUpdateBatch(
    String playerUuid,
    String playerName,
    String bossId,
    String periodType,
    String periodKey,
    StatsUpdate update
) {}
