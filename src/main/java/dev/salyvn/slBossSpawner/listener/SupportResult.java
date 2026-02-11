package dev.salyvn.slBossSpawner.listener;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of support (debuff effects) data for a boss at time of death.
 */
public record SupportResult(
    List<PlayerSupportEntry> rankings, // sorted desc by supportScore
    double totalSupportScore
) {
    public List<PlayerSupportEntry> getTopSupports(int n) {
        return rankings.subList(0, Math.min(n, rankings.size()));
    }

    public int getRank(UUID playerUuid) {
        for (int i = 0; i < rankings.size(); i++) {
            if (rankings.get(i).playerUuid().equals(playerUuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    public static SupportResult empty() {
        return new SupportResult(List.of(), 0);
    }

    /**
     * Entry for a single player's cumulative support score.
     */
    public record PlayerSupportEntry(UUID playerUuid, String playerName, double supportScore) {}
}
