# Changelog

All notable changes to SLBossSpawner will be documented in this file.

## [3.1.0] - 2026-10-09

### Added

- Added `/slboss validate [id]` with localized checks for loaded worlds, spawn coordinates, schedules, MythicMob pools, and reward tiers.
- Added asynchronous YAML statistics snapshots in `plugins/SLBossSpawner/statistics.yml` with debounced writes and atomic replacement.

### Fixed

- Removed synchronous database initialization from server startup and reload.
- Removed JDBC, HikariCP, SQLite, and MySQL dependencies from the plugin JAR, reducing the packaged artifact to the plugin's runtime classes and resources.
- Preserved `/slboss stats`, `/slboss top`, and statistics/top PlaceholderAPI expansions on the YAML store. Player statistics placeholders now read the requested player's aggregate directly instead of searching only the leaderboard cache.

### Migration notes

- Existing external SQL data files are left untouched and are not imported automatically. New statistics are written to `statistics.yml`; copy or export legacy data separately if it must be retained.
- Existing `statistics.database` settings are ignored after reload and a migration warning is logged. Use `statistics.flush-seconds` and `statistics.leaderboard-size` instead.

## [3.0.1] - 2026-09-29

### Added

- Optional per-tier `random-loot` pools: use positive `rolls` for weighted picks (with an optional `chance` check on the picked entry), or `rolls: 0` for independent entry chances. Entries can grant items, commands, or Mythic drop tables.
- The base `/slboss` help command is available to non-operators (`slboss.use: true`); admin actions still require their own permissions.

### Fixed

- A successful MythicMobs `spawnMob` call no longer hides an unloaded or disappearing spawn entity. With `loadChunks: true`, the plugin holds a chunk ticket until boss cleanup, including when bosses share a chunk. With `false`, spawning requires an already loaded chunk and logs a warning otherwise. A one-second check logs the entity's actual position or disappearance.
- Boss state now records the last known entity location. Ordered synchronous state-file writes prevent overlapping lifecycle updates from overwriting newer state.
- Unresolved saved boss UUIDs keep their spawn slot reserved until verified. Administrators can use `/slboss state forget <bossId>` after checking the world to release a legacy state row with no saved location.
- `/slboss reload` keeps live bosses and their damage, tank, and support rankings for ongoing fights. Plugin disable preserves boss entities and saved state for restart instead of forcing despawn.
- Tier validation now warns about and skips invalid or nonpositive rank keys. Existing valid numeric tiers 1–5 are parsed and distributed to matching ranked players; new debug logs show ranked participant and loaded tier counts plus each rewarded or skipped rank.
- Damage and tank tracking now use the final Bukkit damage event's causing entity to include skill-caused hits when the server reports an attributable source.
- Command rewards use the online player's exact name rather than rewriting it, preserving digits and Floodgate's default `.` prefix for CMI `-t:{player}`. Offline commands and Mythic drop-table rewards resolve `{player}` on delivery after join.
- Pending rewards from older releases repair known CMI `saveditems get`, CMI `money give`, and Mythic drop-table player targets when the previously stripped name matches the joining player's UUID. Other custom command formats cannot be inferred safely.
- Pending reward file writes now stay ordered and synchronous after a one-tick debounce, so older snapshots cannot restore claimed rewards or erase newly queued rewards.
- Failed Bukkit command dispatches are logged. A third-party plugin may still accept the dispatch and then reject its own target; check its console output if a CMI reward is missing.
- Fixed shaded SQLite JDBC startup: preserved its original JNI class/package names and merged the JDBC service descriptor. An in-memory SQLite connection now opens from the packaged plugin JAR.
- Bundled MySQL Connector/J and merged its JDBC service registration with SQLite's, so MySQL statistics storage has a driver in the packaged plugin JAR.
- MySQL index creation now rechecks metadata after a concurrent create attempt, avoiding startup failure when multiple initializers race.
- MySQL statistics now store longer player names, including a Floodgate prefix plus a 16-character gamertag; existing columns are widened on startup.

### Updated dependencies

- Target Paper API `1.21.11-R0.1-SNAPSHOT` on Java 21.
- Compile against MythicMobs `5.13.0`, PlaceholderAPI `2.12.3`, MMOItems API `6.10.1-SNAPSHOT`, and MythicLib `1.7.1-SNAPSHOT`.
- Updated HikariCP to `7.1.0`, SQLite JDBC to `3.53.4.0`, Maven Compiler Plugin to `3.16.0`, and Maven Shade Plugin to `3.6.2`. Added MySQL Connector/J `8.2.0`, the newest release explicitly supporting the documented MySQL Server 5.7+ range.
- Use PlaceholderAPI's documented Maven repository at `https://repo.helpch.at/releases/`.
- Replaced the deprecated Paper `JavaPlugin#getDescription()` version lookup with `getPluginMeta()`.

### Configuration notes

- Spawning adds no configuration keys: set `loadChunks: true` for remote arena spawn chunks. When `false`, the chunk must already be loaded and may unload later. Reward tiers can use the new optional `random-loot` section.
- Configure five rank rewards as `damage.tiers.1` through `damage.tiers.5` (or the same structure under `tank`). A tier runs only when a player has tracked contribution at that rank. The reported two-of-five reward case still needs server-side participant and CMI output logs to identify its exact cause. `pending-rewards.yml` contains only rewards for players offline at distribution time.
- Player names used in commands must match `[A-Za-z0-9_.-]+`; custom Floodgate prefixes outside that set are rejected with a warning. Review old `pending-rewards.yml` entries using custom commands before affected players join. See [the wiki](wiki.html#rewards-config) for an exact CMI YAML example.

---

## [3.0.0] - 2026-02-27

### Added

#### Statistics & Database System (Phase 2-3)
- **SQLite/MySQL database** for persistent player statistics across server restarts
- **7 period types**: daily, weekly, monthly, quarterly, seasonal, yearly, all-time
- **11 tracked metrics**: damage, kills, participations, tank, support, top1-3, top1tank, top1support, bestdamage
- **Per-boss + global** stat aggregation — every boss kill records both per-boss and `__global__` stats
- **Automatic period archival** — hourly scheduler detects period rollovers and archives old data
- **Season resolver** — calendar quarter-based seasons (S1-S4)
- **Configurable** via `statistics.*` section in config.yml (database type, MySQL connection, table prefix)

#### Leaderboard & Commands (Phase 4)
- **`/slboss stats [player]`** — view a player's all-time statistics (12 stat fields)
- **`/slboss top <metric> [period] [boss]`** — view leaderboard with tab completion for all arguments
- **LeaderboardCache** — async in-memory cache refreshed every 60s (configurable), thread-safe reads
- **PlaceholderAPI expansions**:
  - `%slbs_top_<metric>_<rank>_<name|value>%` — leaderboard rank data
  - `%slbs_stats_<metric>%` — player's own stat from cache
  - `%slbs_stats_<metric>_<period>%` — player stat for specific period
- **New permissions**: `slboss.stats` (default: true), `slboss.top` (default: true)
- **Vietnamese translations** for all new message keys

#### Code Architecture Improvements (Phase 1)
- **AbstractBossTracker** — extracted common tracking logic (start/stop/cleanup, result building)
- **TrackingResult** — generic result record with ranking + percentage calculation
- **CommandValidator** — centralized shell metacharacter validation
- **ItemFactory** — extracted item creation logic from RewardManager
- **Modularized RewardManager** — split 400+ LOC monolith into focused utilities

### Bug Fixes (Phase 1)
- **H3**: Added reload-safe flag to suppress death-event processing during `/slboss reload`
- **H4**: Delayed state restore by 2 ticks to ensure worlds are fully loaded
- **M8**: Only send spawn success message if boss actually spawned
- Fixed thread safety of `bossScheduler` reference with volatile field

### Technical
- Java 21+ / Paper 1.21+ required
- SQLite (default) or MySQL 5.7+ for statistics storage
- HikariCP connection pooling for MySQL
- ConcurrentHashMap-based leaderboard cache with atomic list replacement

---

## [2.0.1] - 2026-02-11

### Security

#### Critical (C1, C2)
- **C1**: Added drop table name sanitization in `RewardManager.java` to prevent command injection
  - Strips non-alphanumeric characters (except underscore, hyphen, dot) from drop table names
  - Combined with player name sanitization to fully prevent injection through reward distribution
  - Location: `RewardManager.java:211`

- **C2**: Added command validation in `PendingRewardListener.java` to block shell metacharacters
  - Regex blocks `;`, `&`, `|`, backticks, and `$` in pending commands
  - Logs blocked commands for debugging
  - Defense-in-depth against YAML file tampering
  - Location: `PendingRewardListener.java:44`

### Bug Fixes

#### High Priority (H1-H4)
- **H1**: Removed duplicate `stopTracking()` calls in `BossDeathListener.java`
  - Eliminated wasteful double-removal of tracker data
  - Tracker cleanup now occurs once in `BossInstance.cleanup()`
  - Verified correct ordering: data read via `buildResult()` before cleanup

- **H2**: Made `bossScheduler` field volatile in `SLBossSpawner.java`
  - Ensures thread-safe visibility of scheduler reference during reload
  - Prevents stale references when `/slboss reload` is executed
  - Location: `SLBossSpawner.java:28`

- **H3**: Safe YAML number parsing in `PendingRewardManager.java`
  - Added `instanceof Number` check before casting YAML values
  - Fallback to string parsing with exception handling
  - Prevents ClassCastException when YAML returns unexpected types
  - Location: `PendingRewardManager.java:94-100`

- **H4**: Added `calculateNextSpawn()` after `forceSpawn()` in `BossScheduler.java`
  - Force-spawned bosses now correctly reschedule their next spawn time
  - Matches pattern used in `checkSpawns()` method
  - Fixed in both `forceSpawn()` and `forceSpawnAll()` methods
  - Location: `BossScheduler.java:108, 119`

### Technical Details

**Files Modified:**
- `src/main/java/dev/salyvn/slBossSpawner/reward/RewardManager.java`
- `src/main/java/dev/salyvn/slBossSpawner/reward/PendingRewardListener.java`
- `src/main/java/dev/salyvn/slBossSpawner/listener/BossDeathListener.java`
- `src/main/java/dev/salyvn/slBossSpawner/SLBossSpawner.java`
- `src/main/java/dev/salyvn/slBossSpawner/persist/PendingRewardManager.java`
- `src/main/java/dev/salyvn/slBossSpawner/boss/BossScheduler.java`

**Review Status:** All 6 fixes verified correct with no regressions (see `plans/reports/code-reviewer-260211-1634-six-fixes-review.md`)

### Known Limitations

1. **Empty drop table names**: If sanitization results in empty string, MythicMobs command will fail silently (low impact)
2. **Reload during death event**: Pre-existing edge case where reload during boss death processing may reference old scheduler

---

## [2.0.0] - 2026-02-11

### Added
- Scheduled boss spawning with timezone support
- Damage tracking system (direct, projectile, pet, TNT, area damage)
- Tiered reward distribution based on damage contribution
- Last-hit bonus multiplier
- Item protection system with owner-only pickup
- Offline reward queuing and delivery on player join
- Spawn countdown warnings (chat, title, bossbar)
- Death broadcast system
- Boss leash system with teleport-back functionality
- State persistence across server restarts
- Optional chunk loading at spawn locations
- PlaceholderAPI integration
- Multi-language support (English, Vietnamese)

### Technical
- Java 21+ support
- Paper/Spigot 1.21+ compatibility
- MythicMobs integration
- MMOItems optional integration
