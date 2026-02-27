package dev.salyvn.slBossSpawner.database;

public record LeaderboardRow(
    String playerUuid,
    String playerName,
    double value,
    int rank
) {}
