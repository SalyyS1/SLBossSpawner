# Codebase Summary

## Project Overview

**Name:** FluffyBossSpawner (SLBossSpawner)
**Version:** 2.0.1
**Type:** Minecraft Plugin (Paper/Spigot)
**Language:** Java 21
**Build Tool:** Maven
**Primary Dependency:** MythicMobs

## Architecture

### Package Structure

```
dev.salyvn.slBossSpawner/
├── boss/                    # Core boss management
├── broadcast/              # Warning and notification system
├── commands/               # Command handlers
├── config/                 # Configuration management
├── listener/               # Event handlers and tracking
├── persist/                # Data persistence
├── placeholder/            # PlaceholderAPI integration
├── reward/                 # Reward distribution
└── utils/                  # Utility classes
```

### Module Breakdown (26 Java files)

#### Boss Management (`boss/`)
- **BossConfig.java** - Boss configuration data model
- **BossEntityHelper.java** - Entity manipulation utilities
- **BossInstance.java** - Individual boss lifecycle management
- **BossScheduler.java** - Spawn scheduling and timer coordination

#### Broadcast System (`broadcast/`)
- **BroadcastManager.java** - Centralized broadcast coordination
- **SpawnWarningTask.java** - Countdown warning task

#### Commands (`commands/`)
- **BossCommand.java** - Command executor (`/slboss` and `/slbs`)

#### Configuration (`config/`)
- **ConfigManager.java** - Main config loader
- **MessageManager.java** - Multi-language message handling
- **ScheduleManager.java** - Schedule config parser

#### Event Listeners & Tracking (`listener/`)
- **BossDeathListener.java** - Boss death event handler
- **DamageTracker.java** - Damage contribution tracking
- **DamageResult.java** - Damage ranking result
- **SupportTracker.java** - Support action tracking
- **SupportResult.java** - Support ranking result
- **TankTracker.java** - Tank/absorption tracking
- **TankResult.java** - Tank ranking result

#### Persistence (`persist/`)
- **BossStateManager.java** - Boss state persistence across restarts
- **PendingRewardManager.java** - Offline reward queue management

#### Placeholder Integration (`placeholder/`)
- **BossPlaceholder.java** - PlaceholderAPI expansion

#### Reward System (`reward/`)
- **RewardManager.java** - Reward distribution coordinator
- **ItemProtectionListener.java** - Owner-only item pickup
- **PendingRewardListener.java** - Offline reward delivery

#### Core (`root`)
- **SLBossSpawner.java** - Main plugin class

#### Utilities (`utils/`)
- **ColorUtils.java** - Color code utilities
- **TimeUtils.java** - Time formatting and parsing

## Key Features

### 1. Scheduled Boss Spawning
- Time-based scheduling with timezone support
- Configurable spawn intervals and locations
- Force spawn/despawn commands
- State persistence across server restarts

### 2. Damage Tracking System
Types tracked:
- Direct melee/ranged damage
- Projectile damage
- Pet/tamed entity damage
- TNT damage
- Area effect damage

### 3. Tiered Reward Distribution
- Damage-based ranking
- Last-hit bonus multiplier
- Commands, vanilla items, MMOItems support
- Offline reward queuing with YAML persistence

### 4. Broadcast System
- Spawn countdown warnings (chat, title, bossbar)
- Death announcements
- Configurable message templates
- Multi-language support (EN, VI)

### 5. Boss Mechanics
- Leash system (teleport-back if too far from spawn)
- Optional chunk loading at spawn location
- Automatic cleanup on expiry
- Spawn warning cancellation

### 6. Integration
- **MythicMobs** - Boss entity management (required)
- **PlaceholderAPI** - Custom placeholders (optional)
- **MMOItems** - Advanced item rewards (optional)

## Data Flow

### Spawn Workflow
```
BossScheduler.checkSpawns()
  → BossInstance.spawn()
  → MythicMobs API spawn
  → SpawnWarningTask (countdown)
  → BossStateManager.save() (persistence)
```

### Death & Reward Workflow
```
BossDeathListener.onMythicMobDeath()
  → Tracker.buildResult() (damage/support/tank)
  → RewardManager.distributeRewards()
  → Command sanitization (C1 fix)
  → PendingRewardManager.queueCommand() (offline players)
  → BossInstance.cleanup()
  → Tracker.stopTracking()
```

### Offline Reward Delivery
```
PlayerJoinEvent
  → PendingRewardListener
  → Load pending-rewards.yml
  → Command validation (C2 fix)
  → Bukkit.dispatchCommand()
  → Remove from pending queue
```

## Configuration Files

| File | Purpose |
|------|---------|
| `config.yml` | General settings, prefix, debug, language |
| `schedules.yml` | Boss spawn schedules, locations, broadcast |
| `reward.yml` | Reward tiers, items, commands per boss |
| `message.yml` | English messages |
| `message-vi.yml` | Vietnamese messages |
| `boss-state.yml` | Boss state persistence (auto-generated) |
| `pending-rewards.yml` | Offline reward queue (auto-generated) |

## Security Model

### Input Sanitization
1. **Player names** - Sanitized in `RewardManager:194` before command construction
2. **Drop table names** - Sanitized in `RewardManager:211` (C1 fix)
3. **Pending commands** - Validated in `PendingRewardListener:44` (C2 fix)

### Thread Safety
- Main plugin field `bossScheduler` marked volatile (H2 fix)
- All command execution on main thread (Bukkit guarantee)
- Timer tasks run on main thread via `runTaskTimer`

### Data Integrity
- YAML parsing with type checking (H3 fix)
- Tracker cleanup after data read (H1 fix)
- State persistence on shutdown (`saveSync`)

## Recent Changes (v2.0.1)

### Security Fixes
- **C1**: Drop table name sanitization to prevent command injection
- **C2**: Pending command validation against shell metacharacters

### Bug Fixes
- **H1**: Removed duplicate tracker cleanup calls
- **H2**: Volatile scheduler reference for thread safety
- **H3**: Safe YAML number parsing with instanceof check
- **H4**: Calculate next spawn after force spawn

See `CHANGELOG.md` and `security-fixes.md` for details.

## Code Metrics

- **Total Java files**: 26
- **LOC**: ~2,500 (estimated)
- **Package count**: 8
- **External dependencies**: MythicMobs (required), PlaceholderAPI (optional), MMOItems (optional)

## Build & Test

**Build command:**
```bash
mvn package
```

**Output:** `target/SLBossSpawner-2.0.0.jar`

**Test coverage:** Not currently implemented

## Documentation

- `README.md` - Setup and usage guide
- `docs/wiki.html` - Detailed feature documentation
- `docs/CHANGELOG.md` - Version history
- `docs/security-fixes.md` - Security implementation details
- `docs/codebase-summary.md` - This file

## Known Limitations

1. Empty drop table names after sanitization fail silently (low impact)
2. Reload during death event processing may reference old scheduler (pre-existing edge case)
3. No automated test coverage
4. Thread safety relies on Bukkit main-thread guarantees
