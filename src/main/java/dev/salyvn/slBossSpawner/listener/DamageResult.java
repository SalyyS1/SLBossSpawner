package dev.salyvn.slBossSpawner.listener;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of damage data for a boss at time of death.
 */
public record DamageResult(
    List<PlayerDamageEntry> rankings,
    UUID lastHitPlayer,
    double totalDamage
) implements TrackingResult<DamageResult.PlayerDamageEntry> {

    @Override
    public double totalValue() { return totalDamage; }

    @Override
    public List<PlayerDamageEntry> getTop(int n) {
        return rankings.subList(0, Math.min(n, rankings.size()));
    }

    public int getRank(UUID playerUuid) {
        return getRank(playerUuid, PlayerDamageEntry::playerUuid);
    }

    /** Kept for backward compatibility with existing callers. */
    public List<PlayerDamageEntry> getTopDamagers(int n) {
        return getTop(n);
    }

    public static DamageResult empty() {
        return new DamageResult(List.of(), null, 0);
    }

    public record PlayerDamageEntry(UUID playerUuid, String playerName, double damage) {}
}
