# SLBossSpawner

A scheduled boss spawner plugin for Paper/Spigot servers with MythicMobs integration. Supports damage-based reward distribution, spawn countdown warnings, offline reward queuing, and PlaceholderAPI placeholders.

**Version:** 2.0.1 (Security & Bug Fix Release)

## Requirements

- Paper/Spigot 1.21+
- Java 21+
- MythicMobs (required)
- PlaceholderAPI (optional)
- MMOItems (optional)

## Installation

1. Place `SLBossSpawner-2.0.1.jar` in your server's `plugins/` folder
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

Set `language` in `config.yml` to `en` or `vi`. Default is `vi`.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/slboss` | `slboss.use` | Show help |
| `/slboss reload` | `slboss.reload` | Reload all configs |
| `/slboss spawn [id]` | `slboss.spawn` | Force spawn boss(es) |
| `/slboss despawn [id]` | `slboss.despawn` | Despawn boss(es) |
| `/slboss info` | `slboss.info` | Detailed boss status |
| `/slboss list` | `slboss.info` | List all bosses |

Alias: `/slbs`

## Permissions

| Permission | Default | Description |
|---|---|---|
| `slboss.use` | op | Basic command access |
| `slboss.reload` | op | Reload configuration |
| `slboss.spawn` | op | Force spawn bosses |
| `slboss.despawn` | op | Despawn bosses |
| `slboss.info` | true | View boss info and list |
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
- **Last-Hit Bonus** -- Multiplier for the killing blow
- **Item Protection** -- Owner-only pickup with configurable expiry
- **Offline Queuing** -- Pending rewards delivered on join
- **Spawn Warnings** -- Chat, title, bossbar countdown
- **Death Broadcast** -- Chat and title on boss death
- **Leash System** -- Boss teleported back if too far from spawn
- **State Persistence** -- Bosses survive server restarts
- **Chunk Loading** -- Optional chunk loading at spawn location

See [docs/wiki.html](docs/wiki.html) for detailed documentation.

## Documentation

- [Changelog](docs/CHANGELOG.md) - Version history and recent fixes
- [Security Fixes](docs/security-fixes.md) - Security implementation details
- [Codebase Summary](docs/codebase-summary.md) - Architecture and code overview
- [Code Standards](docs/code-standards.md) - Development guidelines
- [Wiki](docs/wiki.html) - Detailed feature documentation

## Recent Updates (v2.0.1)

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

Output: `target/SLBossSpawner-2.0.1.jar`

## Author

SalyVn
