package dev.salyvn.slBossSpawner.statistics;

public record PlayerStats(
    String playerUuid,
    String playerName,
    int kills,
    int participations,
    double totalDamage,
    double totalDamageTaken,
    double totalSupportScore,
    int top1Count, int top2Count, int top3Count,
    int top1TankCount, int top1SupportCount,
    double highestDamage
) {}
