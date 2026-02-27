package dev.salyvn.slBossSpawner.listener;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Generic interface for immutable boss fight result snapshots.
 * Provides shared top-N and rank lookup without duplicating logic.
 */
public interface TrackingResult<E> {
    List<E> rankings();
    double totalValue();

    default List<E> getTop(int n) {
        return rankings().subList(0, Math.min(n, rankings().size()));
    }

    default int getRank(UUID playerUuid, Function<E, UUID> uuidExtractor) {
        List<E> list = rankings();
        for (int i = 0; i < list.size(); i++) {
            if (uuidExtractor.apply(list.get(i)).equals(playerUuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    static <E> TrackingResult<E> empty() {
        return new TrackingResult<>() {
            @Override public List<E> rankings() { return List.of(); }
            @Override public double totalValue() { return 0; }
        };
    }
}
