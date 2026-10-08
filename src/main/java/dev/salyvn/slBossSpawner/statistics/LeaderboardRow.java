package dev.salyvn.slBossSpawner.statistics;

public record LeaderboardRow(
        String playerUuid,
        String playerName,
        double value,
        int rank
) {
}
