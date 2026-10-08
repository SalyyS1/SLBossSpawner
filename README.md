# SLBossSpawner

A scheduled boss spawner plugin for Paper servers with MythicMobs integration. Supports damage-based reward distribution, spawn countdown warnings, offline reward queuing, and PlaceholderAPI placeholders.

**Version:** 3.1.0

## Requirements

- Paper 1.21.11
- Java 21
- MythicMobs 5.13.0 (required)
- PlaceholderAPI 2.12.3 (optional)
- MMOItems 6.10.1-SNAPSHOT with MythicLib 1.7.1-SNAPSHOT (optional)

## Installation

1. Place `SLBossSpawner-3.1.0.jar` in your server's `plugins/` folder
2. Restart the server
3. Edit configuration files in `plugins/SLBossSpawner/`
4. Run `/slboss reload`

## Configuration Files

| File | Purpose |
|---|---|
| `config.yml` | General settings (prefix, debug, language) |
| `schedules.yml` | Boss spawn schedules, locations, broadcast settings |
| `reward.yml` | Reward tiers, items, commands per boss |
| `message.yml` | English messages |
| `message-vi.yml` | Vietnamese messages |
| `statistics.yml` | Auto-generated YAML statistics snapshot |

Set `language` in `config.yml` to `en` or `vi`. Default is `en`.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/slboss` | `slboss.use` | Show help |
| `/slboss reload` | `slboss.reload` | Reload all configs |
| `/slboss state forget <id>` | `slboss.state` | Release an unresolved saved boss after checking the world |
| `/slboss spawn [id]` | `slboss.spawn` | Force spawn boss(es) |
| `/slboss despawn [id]` | `slboss.despawn` | Despawn boss(es) |
| `/slboss info` | `slboss.info` | Detailed boss status |
| `/slboss list` | `slboss.info` | List all bosses |
| `/slboss validate [id]` | `slboss.validate` | Check loaded boss configuration |
| `/slboss stats [player]` | `slboss.stats` | View all-time player statistics |
| `/slboss top <metric> [period] [boss]` | `slboss.top` | View a leaderboard |

Alias: `/slbs`

## Permissions

| Permission | Default | Description |
|---|---|---|
| `slboss.use` | true | Basic command access |
| `slboss.reload` | op | Reload configuration |
| `slboss.state` | op | Forget unresolved boss state |
| `slboss.spawn` | op | Force spawn bosses |
| `slboss.despawn` | op | Despawn bosses |
| `slboss.info` | true | View boss info and list |
| `slboss.validate` | op | Validate boss configuration |
| `slboss.stats` | true | View player statistics |
| `slboss.top` | true | View leaderboards |
| `slboss.*` | op | All permissions |

## PlaceholderAPI

Prefix: `%slbs_<bossId>.<placeholder>%`

| Placeholder | Output |
|---|---|
| `<id>.current` | Current MythicMob name or "None" |
| `<id>.next` | Time until next spawn |
| `<id>.next_formatted` | Next spawn time (HH:mm) |
| `<id>.expire` | Time until expiry |
| `<id>.status` | "Alive" or "Dead" |
| `<id>.mob` | MythicMob ID |
| `count` | Total boss count |

## Features

- **Scheduled Spawning** -- Time-based scheduling with timezone support
- **Damage Tracking** -- Per-player damage tracking (direct, projectile, pet, TNT, area)
- **Tiered Rewards** -- Commands, vanilla items, MMOItems per damage rank
- **Random Bonus Loot** -- Optional chance/weight loot pools inside each reward tier
- **Last-Hit Bonus** -- Multiplier for the killing blow
- **Item Protection** -- Owner-only pickup with configurable expiry
- **Offline Queuing** -- Pending rewards delivered on join
- **Spawn Warnings** -- Chat, title, bossbar countdown
- **Death Broadcast** -- Chat and title on boss death
- **Leash System** -- Boss teleported back if too far from spawn
- **State Persistence** -- Bosses survive server restarts
- **Chunk Loading** -- Optional chunk loading at spawn location
- **YAML Statistics** -- Async load and debounced atomic snapshots without a database driver
- **Configuration Validation** -- `/slboss validate` catches invalid worlds, schedules, locations, mob pools, and rewards

See [docs/wiki.html](docs/wiki.html) for detailed documentation.

## Documentation

- [Changelog](docs/CHANGELOG.md) - Version history and recent fixes
- [Security Fixes](docs/security-fixes.md) - Security implementation details
- [Codebase Summary](docs/codebase-summary.md) - Architecture and code overview
- [Code Standards](docs/code-standards.md) - Development guidelines
- [Wiki](docs/wiki.html) - Detailed feature documentation

## Recent Updates (v3.1.0)

- Remove synchronous SQL startup and all JDBC/Hikari/SQLite/MySQL dependencies; statistics now load asynchronously from `statistics.yml` and flush in batches.
- Keep `/slboss stats`, `/slboss top`, and statistics/top PlaceholderAPI expansions on the YAML store; per-player placeholders no longer depend on leaderboard rank.
- Add `/slboss validate [id]` with localized diagnostics for worlds, coordinates, schedules, mob weights, and reward tiers.
- Keep existing external SQL data files untouched; they are legacy data and are not imported automatically.

## Earlier Updates (v3.0.1)

- Keep scheduled bosses available in their spawn chunk; `loadChunks: true` holds a plugin chunk ticket until cleanup.
- Log and recover when MythicMobs reports a spawn but the entity disappears immediately.
- Record each boss's last known position and write boss state in lifecycle order.
- Keep live bosses and fight rankings through `/slboss reload`; preserve boss entities and state on plugin disable.
- Warn about invalid rank tiers and log tier/participant counts when `debug: true`; see the wiki's five-rank CMI example.
- Add optional per-tier random loot with weighted picks or independent chances.
- Attribute skill damage and tanking from the final damage event when Bukkit reports a causing entity.
- Preserve exact online player names in reward commands, including Floodgate's default `.` prefix and digits. Offline commands resolve `{player}` when the player joins.
- Repair known CMI and Mythic drop-table commands queued by older releases. Review old custom pending commands before affected players join.
- Add `/slboss state forget <id>` for old boss state without a saved location; check the world for the old mob before using it.
- Refresh Paper, MythicMobs, PlaceholderAPI, MMOItems, and Maven build dependencies.

## Earlier Updates (v3.0.0)

### Security Fixes
- Command injection prevention in reward distribution
- Drop table name sanitization
- Pending command validation against shell metacharacters

### Bug Fixes
- Fixed force spawn not rescheduling next spawn time
- Safe YAML number parsing with type checking
- Thread-safe scheduler reference for reload operations
- Removed duplicate tracker cleanup calls

See [CHANGELOG.md](docs/CHANGELOG.md) for complete details.

## Building

```bash
mvn package
```

Output: `target/SLBossSpawner-3.1.0.jar`

## Author

SalyVn
