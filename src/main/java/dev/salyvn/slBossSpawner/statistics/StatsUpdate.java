package dev.salyvn.slBossSpawner.statistics;

public record StatsUpdate(
        int killsDelta,
        int participationsDelta,
        double damageDelta,
        double damageTakenDelta,
        double supportScoreDelta,
        int top1Delta,
        int top2Delta,
        int top3Delta,
        int top1TankDelta,
        int top1SupportDelta,
        double fightDamage
) {
}
