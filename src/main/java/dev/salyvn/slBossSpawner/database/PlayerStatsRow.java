package dev.salyvn.slBossSpawner.database;

public record PlayerStatsRow(
    String playerUuid,
    String playerName,
    String bossId,
    String periodType,
    String periodKey,
    int kills,
    int participations,
    double totalDamage,
    double totalDamageTaken,
    double totalSupportScore,
    int top1Count, int top2Count, int top3Count,
    int top1TankCount, int top1SupportCount,
    double highestDamage
) {}
