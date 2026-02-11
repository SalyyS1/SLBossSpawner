package dev.salyvn.slBossSpawner.listener;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of damage data for a boss at time of death.
 */
public record DamageResult(
    List<PlayerDamageEntry> rankings, // sorted desc by damage
    UUID lastHitPlayer,               // nullable
    double totalDamage
) {
    public List<PlayerDamageEntry> getTopDamagers(int n) {
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

    public static DamageResult empty() {
        return new DamageResult(List.of(), null, 0);
    }

    /**
     * Entry for a single player's cumulative damage.
     */
    public record PlayerDamageEntry(UUID playerUuid, String playerName, double damage) {}
}
