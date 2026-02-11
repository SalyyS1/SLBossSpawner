# Changelog

All notable changes to FluffyBossSpawner will be documented in this file.

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
