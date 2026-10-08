package dev.salyvn.slBossSpawner.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable aggregate for one player, boss, period, and period key. */
final class StatisticsAggregate {
    private String playerName;
    private int kills;
    private int participations;
    private double totalDamage;
    private double totalDamageTaken;
    private double totalSupportScore;
    private int top1Count;
    private int top2Count;
    private int top3Count;
    private int top1TankCount;
    private int top1SupportCount;
    private double highestDamage;

    StatisticsAggregate(String playerName) {
        this.playerName = playerName == null ? "Unknown" : playerName;
    }

    static StatisticsAggregate fromMap(String playerName, Map<?, ?> values) {
        StatisticsAggregate stats = new StatisticsAggregate(playerName);
        stats.kills = intValue(values.get("kills"));
        stats.participations = intValue(values.get("participations"));
        stats.totalDamage = doubleValue(values.get("total-damage"));
        stats.totalDamageTaken = doubleValue(values.get("total-damage-taken"));
        stats.totalSupportScore = doubleValue(values.get("total-support-score"));
        stats.top1Count = intValue(values.get("top1-count"));
        stats.top2Count = intValue(values.get("top2-count"));
        stats.top3Count = intValue(values.get("top3-count"));
        stats.top1TankCount = intValue(values.get("top1-tank-count"));
        stats.top1SupportCount = intValue(values.get("top1-support-count"));
        stats.highestDamage = doubleValue(values.get("highest-damage"));
        return stats;
    }

    static StatisticsAggregate merge(StatisticsAggregate left, StatisticsAggregate right) {
        left.playerName = right.playerName == null || right.playerName.isBlank()
                ? left.playerName : right.playerName;
        left.kills += right.kills;
        left.participations += right.participations;
        left.totalDamage += right.totalDamage;
        left.totalDamageTaken += right.totalDamageTaken;
        left.totalSupportScore += right.totalSupportScore;
        left.top1Count += right.top1Count;
        left.top2Count += right.top2Count;
        left.top3Count += right.top3Count;
        left.top1TankCount += right.top1TankCount;
        left.top1SupportCount += right.top1SupportCount;
        left.highestDamage = Math.max(left.highestDamage, right.highestDamage);
        return left;
    }

    void apply(String name, StatsUpdate update) {
        if (name != null && !name.isBlank()) {
            playerName = name;
        }
        kills += update.killsDelta();
        participations += update.participationsDelta();
        totalDamage += update.damageDelta();
        totalDamageTaken += update.damageTakenDelta();
        totalSupportScore += update.supportScoreDelta();
        top1Count += update.top1Delta();
        top2Count += update.top2Delta();
        top3Count += update.top3Delta();
        top1TankCount += update.top1TankDelta();
        top1SupportCount += update.top1SupportDelta();
        highestDamage = Math.max(highestDamage, update.fightDamage());
    }

    String playerName() {
        return playerName;
    }

    double metric(String metric) {
        return switch (metric) {
            case "damage", "total_damage" -> totalDamage;
            case "kills" -> kills;
            case "participations" -> participations;
            case "tank", "total_damage_taken" -> totalDamageTaken;
            case "support", "total_support_score" -> totalSupportScore;
            case "top1", "top1_count" -> top1Count;
            case "top2", "top2_count" -> top2Count;
            case "top3", "top3_count" -> top3Count;
            case "top1tank", "top1_tank_count" -> top1TankCount;
            case "top1support", "top1_support_count" -> top1SupportCount;
            case "bestdamage", "highest_damage" -> highestDamage;
            default -> 0;
        };
    }

    PlayerStatsRow toRow(String uuid, String bossId, String periodType, String periodKey) {
        return new PlayerStatsRow(uuid, playerName, bossId, periodType, periodKey, kills,
                participations, totalDamage, totalDamageTaken, totalSupportScore, top1Count,
                top2Count, top3Count, top1TankCount, top1SupportCount, highestDamage);
    }

    Map<String, Object> toMap(StatisticsKey key) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("player-uuid", key.playerUuid());
        values.put("player-name", playerName);
        values.put("boss-id", key.bossId());
        values.put("period-type", key.periodType());
        values.put("period-key", key.periodKey());
        values.put("kills", kills);
        values.put("participations", participations);
        values.put("total-damage", totalDamage);
        values.put("total-damage-taken", totalDamageTaken);
        values.put("total-support-score", totalSupportScore);
        values.put("top1-count", top1Count);
        values.put("top2-count", top2Count);
        values.put("top3-count", top3Count);
        values.put("top1-tank-count", top1TankCount);
        values.put("top1-support-count", top1SupportCount);
        values.put("highest-damage", highestDamage);
        return values;
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0D;
    }
}
