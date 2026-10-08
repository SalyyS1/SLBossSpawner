# Codebase Summary

## Project

**Name:** SLBossSpawner
**Version:** 3.1.0
**Target:** Paper 1.21.11 / Java 21
**Required integration:** MythicMobs 5.13.0
**Optional integrations:** PlaceholderAPI 2.12.3, MMOItems 6.10.1-SNAPSHOT, MythicLib 1.7.1-SNAPSHOT

SLBossSpawner schedules MythicMobs bosses, tracks fight contributions, distributes
tiered rewards, and persists live boss/offline reward state with YAML files.

## Package map

```text
dev.salyvn.slBossSpawner/
├── boss/          # BossConfig, BossInstance, BossScheduler, entity helpers
├── broadcast/     # Spawn countdowns and death announcements
├── commands/      # /slboss executor and stats/top handlers
├── config/        # Config, schedule, message loading, ConfigValidator
├── listener/      # Boss death, damage, tank, and support tracking
├── persist/       # boss-state.yml and pending-rewards.yml persistence
├── placeholder/   # PlaceholderAPI boss, stats, and leaderboard expansion
├── reward/        # Reward execution and item protection
├── statistics/    # YAML store, period keys, stats manager, leaderboard facade
└── utils/         # Color and time helpers
```

## Runtime flow

### Startup

`SLBossSpawner.onEnable()` loads configuration and schedules, starts the
asynchronous `FileStatisticsStore`, registers trackers/listeners, then starts
the boss scheduler. No network or JDBC initialization occurs on the server
thread.

### Spawn

`BossScheduler` owns one `BossInstance` per configured ID. The instance resolves
the world lazily, optionally holds a plugin chunk ticket, invokes MythicMobs,
verifies the resulting entity, and records state for restart recovery.

### Death, rewards, statistics

`BossDeathListener` snapshots damage/tank/support results before tracker cleanup.
`RewardManager` distributes online rewards or queues commands/items for the next
join. `StatisticsManager` expands one fight into per-boss and `__global__`
aggregates across seven periods, then sends updates to `FileStatisticsStore`.

The store keeps aggregates in a read/write locked map. It loads
`plugins/SLBossSpawner/statistics.yml` on one worker, debounces writes, and
replaces the file atomically through a temporary snapshot. Shutdown/reload
flushes the latest map synchronously. Existing external SQL data files are not deleted or imported.

## Public commands

- `/slboss reload` reloads messages, schedules, and the YAML statistics store.
- `/slboss state forget <id>` releases a saved boss slot only after verification.
- `/slboss spawn [id]`, `/slboss despawn [id]`, `/slboss info`, `/slboss list` manage boss lifecycle.
- `/slboss validate [id]` reports world, coordinate, schedule, mob-pool, and reward configuration issues.
- `/slboss stats [player]` reads all-time global player statistics.
- `/slboss top <metric> [period] [boss]` sorts the YAML aggregates for a leaderboard.

## Persistence files

| File | Owner | Purpose |
|---|---|---|
| `config.yml` | `ConfigManager` | Prefix, language, debug, YAML statistics settings |
| `schedules.yml` | `ScheduleManager` | Boss locations, times, broadcasts, rewards |
| `message.yml` / `message-vi.yml` | `MessageManager` | Localized command and broadcast text |
| `boss-state.yml` | `BossStateManager` | Live/deferred boss state across restart |
| `pending-rewards.yml` | `PendingRewardManager` | Offline command/item queue |
| `statistics.yml` | `FileStatisticsStore` | Aggregated stats and leaderboard source |

## Threading and safety

- Bukkit entity, scheduler, and command operations stay on the server thread.
- Statistics reads sort a bounded in-memory snapshot off-thread for command use;
  PlaceholderAPI reads use the locked aggregate map without disk I/O.
- YAML snapshots use a single writer and atomic replacement to avoid stale
  writes. A failed write leaves the previous file intact.
- Reward command inputs and pending commands retain the existing sanitization
  rules; exact online player names are preserved for Floodgate/Bedrock names.

## Build and verification

```bash
mvn clean package
```

The shaded output is `target/SLBossSpawner-3.1.0.jar`. There is no SQL driver or
connection-pool dependency in the build. The current repository has no automated
unit test suite; Maven compilation and packaged-jar inspection are the primary
release checks.
