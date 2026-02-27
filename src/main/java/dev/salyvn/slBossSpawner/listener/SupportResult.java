package dev.salyvn.slBossSpawner.listener;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of support (harmful-effect) data for a boss at time of death.
 */
public record SupportResult(
    List<PlayerSupportEntry> rankings,
    double totalSupportScore
) implements TrackingResult<SupportResult.PlayerSupportEntry> {

    @Override
    public double totalValue() { return totalSupportScore; }

    @Override
    public List<PlayerSupportEntry> getTop(int n) {
        return rankings.subList(0, Math.min(n, rankings.size()));
    }

    public int getRank(UUID playerUuid) {
        return getRank(playerUuid, PlayerSupportEntry::playerUuid);
    }

    /** Kept for backward compatibility with existing callers. */
    public List<PlayerSupportEntry> getTopSupports(int n) {
        return getTop(n);
    }

    public static SupportResult empty() {
        return new SupportResult(List.of(), 0);
    }

    public record PlayerSupportEntry(UUID playerUuid, String playerName, double supportScore) {}
}
