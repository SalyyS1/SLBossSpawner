# Code Standards

## Language & Platform

- **Language:** Java 21
- **Platform:** Paper/Spigot 1.21+
- **Build Tool:** Maven
- **Target:** Server-side Minecraft plugin

## Naming Conventions

### Classes
- **PascalCase** for all class names
- Descriptive names indicating purpose
- Examples: `BossScheduler`, `RewardManager`, `DamageTracker`

### Files
- **PascalCase** matching class name (Java standard)
- One public class per file
- File name must match public class name

### Packages
- **lowercase** with dots as separators
- Organized by functional domain
- Pattern: `dev.salyvn.slBossSpawner.<module>`

### Variables & Methods
- **camelCase** for variables and methods
- Descriptive names avoiding abbreviations
- Boolean methods prefixed with `is`, `has`, `can`
- Examples: `nextSpawnTime`, `calculateNextSpawn()`, `isAlive()`

### Constants
- **UPPER_SNAKE_CASE** for static final fields
- Group related constants together
- Example: `DEFAULT_SPAWN_INTERVAL`

## Package Organization

```
dev.salyvn.slBossSpawner/
├── boss/                 # Core boss lifecycle and scheduling
├── broadcast/           # Notification and warning systems
├── commands/            # Command executors
├── config/              # Configuration loaders and managers
├── listener/            # Bukkit event listeners and trackers
├── persist/             # Data persistence (YAML, state)
├── placeholder/         # External API integrations
├── reward/              # Reward distribution logic
└── utils/               # Shared utilities
```

### Module Responsibilities

**boss/** - Boss instance management, scheduling, spawning
**broadcast/** - Player notifications (chat, title, bossbar)
**commands/** - Command parsing and execution
**config/** - YAML loading, validation, caching
**listener/** - Event handling, damage/support/tank tracking
**persist/** - State saving/loading, pending reward queue
**placeholder/** - PlaceholderAPI expansion
**reward/** - Reward calculation, item protection, distribution
**utils/** - Color codes, time formatting, shared helpers

## Code Structure

### Class Size
- Target: **< 200 lines per class**
- Exceeding 200 LOC? Consider splitting into:
  - Manager + Helper pattern
  - Tracker + Result pattern
  - Config + Validator pattern

### Method Size
- Target: **< 30 lines per method**
- Long methods indicate need for extraction
- Single Responsibility Principle

### Separation of Concerns
- **Data models** (e.g., `BossConfig`) - immutable configuration objects
- **Managers** (e.g., `RewardManager`) - coordinate complex workflows
- **Trackers** (e.g., `DamageTracker`) - maintain runtime state
- **Results** (e.g., `DamageResult`) - immutable computation outputs
- **Listeners** (e.g., `BossDeathListener`) - event handling only

## Error Handling

### Input Validation
```java
// Sanitize user-controlled input at entry point
String safeName = playerName.replaceAll("[^a-zA-Z0-9_]", "");
if (safeName.isEmpty()) {
    plugin.getLogger().warning("Invalid player name: " + playerName);
    return;
}
```

### Defensive Parsing
```java
// Safe YAML parsing with instanceof check
if (raw instanceof Number n) {
    amount = n.intValue();
} else {
    try { amount = Integer.parseInt(String.valueOf(raw)); }
    catch (NumberFormatException ignored) { amount = 1; }
}
```

### Logging Standards
- **SEVERE** - Critical errors requiring immediate attention
- **WARNING** - Unexpected conditions that don't break functionality
- **INFO** - Important state changes (spawn, death, reload)
- **FINE** - Debugging details (disabled by default)

## Security Patterns

### Command Injection Prevention
1. **Sanitize at entry point** - Remove special characters before storage
2. **Validate at execution** - Block shell metacharacters before dispatch
3. **Defense in depth** - Multiple validation layers

```java
// Entry point sanitization
String safeName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

// Execution validation
if (cmd.matches(".*[;&|`$].*")) {
    plugin.getLogger().warning("Blocked suspicious command: " + cmd);
    return;
}
```

### Thread Safety
- Mark shared mutable fields as `volatile` when accessed across threads
- Use main thread for all Bukkit API calls
- Async I/O operations must sync back to main thread

```java
// Volatile for visibility across reload
private volatile BossScheduler bossScheduler;
```

## Data Persistence

### YAML Patterns
- **Read:** Parse with type checking, provide defaults
- **Write:** Build intermediate object, serialize atomically
- **Save timing:** Debounce rapid saves, sync on shutdown

```java
// Type-safe YAML reading
Object raw = config.get("key");
if (raw instanceof Number n) {
    value = n.intValue();
} else {
    value = defaultValue;
}
```

### File Operations
- Save to temp file, then rename (atomic)
- Handle `IOException` with fallback logging
- Never block main thread for I/O

## Bukkit API Usage

### Event Handlers
- Use `@EventHandler` annotation
- Check event validity before processing
- Don't modify world state in MONITOR priority

### Scheduler
- Main thread: `runTask()`, `runTaskTimer()`
- Async: `runTaskAsynchronously()` (no Bukkit API calls)
- Cancel tasks on plugin disable

### Command Execution
- Validate permissions before execution
- Send feedback to command sender
- Use `dispatchCommand()` for plugin commands

## Integration Patterns

### MythicMobs (Required)
```java
MythicMob mob = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
ActiveMob activeMob = mob.spawn(location, level);
```

### PlaceholderAPI (Optional)
```java
if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
    new BossPlaceholder(this).register();
}
```

### MMOItems (Optional)
- Use string commands via `dispatchCommand()`
- Validate item type existence before distribution

## Testing Standards

### Manual Testing Checklist
- Compile without errors
- Test command syntax and permissions
- Verify config reload behavior
- Test offline reward delivery
- Validate sanitization on special characters

### Code Review Focus
1. Input sanitization completeness
2. Thread safety for reload operations
3. Resource cleanup (tasks, listeners)
4. YAML parsing edge cases
5. Null safety and optional handling

## Documentation

### Code Comments
- Explain **why**, not **what** (code shows what)
- Document non-obvious behavior
- Mark security-critical sections
- Use `// FIXME:` and `// TODO:` sparingly

```java
// Sanitize drop table name to prevent command injection (C1 fix)
String safeTable = dropTable.replaceAll("[^a-zA-Z0-9_\\-.], "");
```

### JavaDoc
- Public API methods require JavaDoc
- Internal helpers: optional but encouraged
- Include `@param`, `@return`, `@throws` tags

## Version Control

### Commit Messages
- Conventional commit format: `type(scope): description`
- Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`
- Examples:
  - `fix(reward): sanitize drop table names to prevent injection`
  - `refactor(boss): make scheduler field volatile for reload safety`

### Branch Strategy
- `main` - production-ready code
- Feature branches for development
- Tag releases: `v2.0.1`

## Performance Guidelines

### Avoid in Hot Paths
- String concatenation in loops (use `StringBuilder`)
- Repeated YAML parsing (cache parsed values)
- Synchronous I/O on main thread

### Optimize
- Cache frequently accessed config values
- Use efficient data structures (`HashMap` for lookups)
- Debounce frequent saves

## Dependencies

### Required
- MythicMobs - Boss entity management
- Paper/Spigot API - Server platform

### Optional
- PlaceholderAPI - Custom placeholders
- MMOItems - Advanced item rewards

### Build Dependencies
- Maven - Build automation
- Java 21 JDK - Compilation

## Forbidden Patterns

**DO NOT:**
- Block main thread with I/O operations
- Modify Bukkit world state from async threads
- Use raw `Object` casts without instanceof check
- Store sensitive data in plain text logs
- Execute user input as commands without sanitization
- Create new schedulers/listeners without cleanup on disable

## Best Practices Summary

1. **Input sanitization at entry point** - Never trust external data
2. **Type-safe YAML parsing** - Always use instanceof before casting
3. **Thread safety** - Volatile for cross-thread visibility
4. **Resource cleanup** - Cancel tasks, unregister listeners on disable
5. **Defensive programming** - Null checks, empty collection handling
6. **Clear separation** - Manager orchestrates, Tracker maintains state, Result is immutable
7. **Consistent naming** - PascalCase classes, camelCase methods, UPPER_SNAKE_CASE constants
