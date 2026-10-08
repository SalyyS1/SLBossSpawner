package dev.salyvn.slBossSpawner.statistics;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lightweight statistics store backed by a debounced YAML snapshot.
 *
 * The file is loaded and written on a single worker. Death events only update
 * the aggregate map and schedule a later snapshot, so no disk I/O runs on the
 * server thread. A temporary file and atomic replacement protect
 * the last good snapshot if the server stops during a write.
 */
public final class FileStatisticsStore implements StatisticsStore {
    private static final int FORMAT_VERSION = 1;

    private final SLBossSpawner plugin;
    private final File file;
    private final int flushSeconds;
    private final Map<StatisticsKey, StatisticsAggregate> entries = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ExecutorService ioExecutor;

    private volatile boolean stopping;
    private volatile boolean loaded;
    private BukkitTask pendingFlush;

    public FileStatisticsStore(SLBossSpawner plugin, int flushSeconds) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "statistics.yml");
        this.flushSeconds = Math.max(1, Math.min(flushSeconds, 60));
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SLBossSpawner-statistics");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start() {
        ioExecutor.submit(this::loadFromDisk);
    }

    @Override
    public void record(List<StatsUpdateBatch> updates) {
        if (stopping || updates == null || updates.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            for (StatsUpdateBatch batch : updates) {
                if (batch == null || batch.update() == null) {
                    continue;
                }
                StatisticsKey key = new StatisticsKey(batch.playerUuid(), batch.bossId(),
                        batch.periodType(), batch.periodKey());
                StatisticsAggregate aggregate = entries.computeIfAbsent(key,
                        ignored -> new StatisticsAggregate(batch.playerName()));
                aggregate.apply(batch.playerName(), batch.update());
            }
        } finally {
            lock.writeLock().unlock();
        }
        scheduleFlush();
    }

    @Override
    public PlayerStatsRow getPlayerStats(String playerUuid, String periodType,
                                         String periodKey, String bossId) {
        lock.readLock().lock();
        try {
            StatisticsAggregate aggregate = entries.get(new StatisticsKey(playerUuid, bossId, periodType, periodKey));
            return aggregate == null ? null
                    : aggregate.toRow(playerUuid, bossId, periodType, periodKey);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<LeaderboardRow> getTopPlayers(String periodType, String periodKey,
                                              String bossId, String metric, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        String normalizedMetric = metric == null ? "" : metric.toLowerCase(Locale.ROOT);

        List<LeaderboardCandidate> candidates = new ArrayList<>();
        lock.readLock().lock();
        try {
            for (Map.Entry<StatisticsKey, StatisticsAggregate> entry : entries.entrySet()) {
                StatisticsKey key = entry.getKey();
                if (!key.periodType().equalsIgnoreCase(periodType)
                        || !key.periodKey().equals(periodKey)
                        || !key.bossId().equals(bossId)) {
                    continue;
                }
                StatisticsAggregate aggregate = entry.getValue();
                candidates.add(new LeaderboardCandidate(key.playerUuid(),
                        aggregate.playerName(), aggregate.metric(normalizedMetric)));
            }
        } finally {
            lock.readLock().unlock();
        }

        candidates.sort(Comparator
                .comparingDouble(LeaderboardCandidate::value).reversed()
                .thenComparing(LeaderboardCandidate::playerName,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LeaderboardCandidate::playerUuid));

        List<LeaderboardRow> result = new ArrayList<>(Math.min(safeLimit, candidates.size()));
        for (int i = 0; i < candidates.size() && i < safeLimit; i++) {
            LeaderboardCandidate candidate = candidates.get(i);
            result.add(new LeaderboardRow(candidate.playerUuid(), candidate.playerName(),
                    candidate.value(), i + 1));
        }
        return result;
    }

    private void scheduleFlush() {
        if (stopping || !loaded) {
            return;
        }
        Runnable schedule = () -> {
            if (stopping || pendingFlush != null) {
                return;
            }
            pendingFlush = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                pendingFlush = null;
                if (stopping) {
                    return;
                }
                List<Map<String, Object>> snapshot = snapshot();
                ioExecutor.submit(() -> writeSnapshot(snapshot));
            }, flushSeconds * 20L);
        };

        if (Bukkit.isPrimaryThread()) {
            schedule.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, schedule);
        }
    }

    private void loadFromDisk() {
        if (!file.exists()) {
            markLoaded();
            plugin.debug("Statistics file not found; starting with empty YAML store");
            return;
        }

        Map<StatisticsKey, StatisticsAggregate> loadedEntries = new HashMap<>();
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> raw : yaml.getMapList("entries")) {
                String playerUuid = stringValue(raw.get("player-uuid"));
                String playerName = stringValue(raw.get("player-name"));
                String bossId = stringValue(raw.get("boss-id"));
                String periodType = stringValue(raw.get("period-type"));
                String periodKey = stringValue(raw.get("period-key"));
                if (playerUuid == null || bossId == null || periodType == null || periodKey == null) {
                    continue;
                }
                try {
                    UUID.fromString(playerUuid);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                StatisticsKey key = new StatisticsKey(playerUuid, bossId, periodType, periodKey);
                loadedEntries.put(key, StatisticsAggregate.fromMap(playerName, raw));
            }

            lock.writeLock().lock();
            try {
                loadedEntries.forEach((key, loadedValue) -> entries.merge(key, loadedValue,
                        StatisticsAggregate::merge));
            } finally {
                lock.writeLock().unlock();
            }
            markLoaded();
            plugin.getLogger().info("Loaded " + loadedEntries.size() + " statistics entries from statistics.yml");
        } catch (Exception e) {
            markLoaded();
            plugin.getLogger().warning("Could not load statistics.yml; keeping the last in-memory data: "
                    + e.getMessage());
        }
    }

    private void markLoaded() {
        loaded = true;
        lock.readLock().lock();
        try {
            if (!entries.isEmpty()) {
                scheduleFlush();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<Map<String, Object>> snapshot() {
        lock.readLock().lock();
        try {
            List<Map.Entry<StatisticsKey, StatisticsAggregate>> sorted = new ArrayList<>(entries.entrySet());
            sorted.sort(Map.Entry.comparingByKey(Comparator.comparing(StatisticsKey::sortKey)));
            List<Map<String, Object>> snapshot = new ArrayList<>(sorted.size());
            for (Map.Entry<StatisticsKey, StatisticsAggregate> entry : sorted) {
                snapshot.add(entry.getValue().toMap(entry.getKey()));
            }
            return snapshot;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void writeSnapshot(List<Map<String, Object>> snapshot) {
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("version", FORMAT_VERSION);
            yaml.set("entries", snapshot);

            Path target = file.toPath();
            Path temporary = target.resolveSibling(file.getName() + ".tmp");
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save statistics.yml: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        stopping = true;
        if (pendingFlush != null) {
            pendingFlush.cancel();
            pendingFlush = null;
        }

        Future<?> finalFlush = ioExecutor.submit(() -> writeSnapshot(snapshot()));
        ioExecutor.shutdown();
        try {
            finalFlush.get(10, TimeUnit.SECONDS);
            if (!ioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (Exception e) {
            ioExecutor.shutdownNow();
            plugin.getLogger().warning("Statistics flush did not finish cleanly: " + e.getMessage());
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private record LeaderboardCandidate(String playerUuid, String playerName, double value) {
    }
}
