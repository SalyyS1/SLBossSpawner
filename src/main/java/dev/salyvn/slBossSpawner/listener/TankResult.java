package dev.salyvn.slBossSpawner.listener;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of tank (damage received) data for a boss at time of death.
 */
public record TankResult(
    List<PlayerTankEntry> rankings, // sorted desc by damageTaken
    double totalDamageTaken
) {
    public List<PlayerTankEntry> getTopTanks(int n) {
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

    public static TankResult empty() {
        return new TankResult(List.of(), 0);
    }

    /**
     * Entry for a single player's cumulative damage taken from the boss.
     */
    public record PlayerTankEntry(UUID playerUuid, String playerName, double damageTaken) {}
}
