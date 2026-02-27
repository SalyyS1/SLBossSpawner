package dev.salyvn.slBossSpawner.listener;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of tank (damage received from boss) data at time of death.
 */
public record TankResult(
    List<PlayerTankEntry> rankings,
    double totalDamageTaken
) implements TrackingResult<TankResult.PlayerTankEntry> {

    @Override
    public double totalValue() { return totalDamageTaken; }

    @Override
    public List<PlayerTankEntry> getTop(int n) {
        return rankings.subList(0, Math.min(n, rankings.size()));
    }

    public int getRank(UUID playerUuid) {
        return getRank(playerUuid, PlayerTankEntry::playerUuid);
    }

    /** Kept for backward compatibility with existing callers. */
    public List<PlayerTankEntry> getTopTanks(int n) {
        return getTop(n);
    }

    public static TankResult empty() {
        return new TankResult(List.of(), 0);
    }

    public record PlayerTankEntry(UUID playerUuid, String playerName, double damageTaken) {}
}
