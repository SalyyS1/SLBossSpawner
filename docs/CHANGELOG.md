# Changelog

All notable changes to FluffyBossSpawner will be documented in this file.

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
