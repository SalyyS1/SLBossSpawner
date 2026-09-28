package dev.salyvn.slBossSpawner.config;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.ItemRewardConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.MobEntry;
import dev.salyvn.slBossSpawner.boss.BossConfig.RandomLootConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.RandomLootEntry;
import dev.salyvn.slBossSpawner.boss.BossConfig.RewardTierConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.ZoneId;
import java.util.*;

/**
 * Parses schedules.yml + reward.yml into {@link BossConfig} objects.
 *
 * H5 FIX: World is no longer resolved at parse time — only the world name
 * string
 * and raw coordinates are stored. {@link BossConfig#resolveLocation()} is
 * called
 * lazily at actual spawn time, avoiding the startup ordering issue.
 * M2 FIX: Uses {@link BossConfig.Builder} instead of the old 28-arg
 * constructor.
 */
public class ScheduleManager {
    private final SLBossSpawner plugin;
    private final Map<String, BossConfig> bossConfigs = new HashMap<>();

    public ScheduleManager(SLBossSpawner plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        File scheduleFile = new File(plugin.getDataFolder(), "schedules.yml");
        if (!scheduleFile.exists())
            plugin.saveResource("schedules.yml", false);
        YamlConfiguration scheduleConfig = YamlConfiguration.loadConfiguration(scheduleFile);

        File rewardFile = new File(plugin.getDataFolder(), "reward.yml");
        if (!rewardFile.exists())
            plugin.saveResource("reward.yml", false);
        YamlConfiguration rewardConfig = YamlConfiguration.loadConfiguration(rewardFile);

        bossConfigs.clear();

        for (String bossId : scheduleConfig.getKeys(false)) {
            ConfigurationSection bossSection = scheduleConfig.getConfigurationSection(bossId);
            if (bossSection == null)
                continue;

            // --- Schedule fields ---
            // Parse mythicmob field: supports 3 formats
            // Format 1 (string): "boss1, boss2, boss3"         → equal chance
            // Format 2 (string): "boss1 10, boss2 40, boss3 50" → weighted by percentage
            // Format 3 (map):    mythicmob:
            //                      boss1: 20   ← expire in minutes
            //                      boss2: 30
            List<MobEntry> mobEntries;
            ConfigurationSection mythicMobSection = bossSection.getConfigurationSection("mythicmob");
            if (mythicMobSection != null) {
                // Map format: key = mobId, value = expire minutes
                mobEntries = parseMobEntriesFromSection(mythicMobSection);
            } else {
                // String format (legacy)
                String mythicMobRaw = bossSection.getString("mythicmob", "SkeletonKing");
                mobEntries = parseMobEntries(mythicMobRaw);
            }
            String mythicMobId = mobEntries.isEmpty() ? "SkeletonKing" : mobEntries.get(0).mobId();
            boolean randomSpawn = bossSection.getBoolean("random", false);

            // Friendly name shown in broadcasts (supports color codes). Empty = use mob ID.
            String displayName = bossSection.getString("name", "");

            // Auto-enable random when map format has 2+ mob entries
            if (mythicMobSection != null && mobEntries.size() > 1) {
                randomSpawn = true;
            }

            int expireSeconds = bossSection.getInt("expire", 3600);
            boolean clearOnExpire = bossSection.getBoolean("clearOnExpire", true);
            List<String> scheduleTimes = bossSection.getStringList("time-settings.time");

            String zoneStr = bossSection.getString("time-settings.zone", "Asia/Ho_Chi_Minh");
            ZoneId timeZone;
            try {
                timeZone = ZoneId.of(zoneStr);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid timezone '" + zoneStr + "' for boss '"
                        + bossId + "', using Asia/Ho_Chi_Minh");
                timeZone = ZoneId.of("Asia/Ho_Chi_Minh");
            }

            // H5 FIX: store world name + coords only — no Bukkit.getWorld() here
            String worldName = bossSection.getString("location.world", "world");
            double x = bossSection.getDouble("location.x");
            double y = bossSection.getDouble("location.y");
            double z = bossSection.getDouble("location.z");
            float yaw = (float) bossSection.getDouble("location.yaw", 0);
            float pitch = (float) bossSection.getDouble("location.pitch", 0);

            int leashRadius = bossSection.getInt("location.leashRadius", 10);
            boolean loadChunks = bossSection.getBoolean("loadChunks", true);
            // 0 = disabled (boss only despawns on expire/death). Avoids surprise
            // despawns when no player happens to be standing near the boss.
            int noPlayerDespawnSeconds = bossSection.getInt("no-player-despawn", 0);

            // --- Broadcast fields ---
            boolean deathChat = true, deathTitle = true;
            boolean spawnWarningChat = true, spawnWarningTitle = true, spawnWarningBossbar = true;
            List<Integer> spawnWarningIntervals = List.of(30, 15, 5, 1);

            ConfigurationSection broadcastSection = bossSection.getConfigurationSection("broadcast");
            if (broadcastSection != null) {
                deathChat = broadcastSection.getBoolean("death.chat", true);
                deathTitle = broadcastSection.getBoolean("death.title", true);
                spawnWarningChat = broadcastSection.getBoolean("spawn-warning.chat", true);
                spawnWarningTitle = broadcastSection.getBoolean("spawn-warning.title", true);
                spawnWarningBossbar = broadcastSection.getBoolean("spawn-warning.bossbar", true);
                List<Integer> intervals = broadcastSection.getIntegerList("spawn-warning.intervals");
                if (!intervals.isEmpty())
                    spawnWarningIntervals = intervals;
            }

            // --- Spawn sound ---
            boolean spawnSoundEnabled = false;
            String spawnSound = "ENTITY_ENDER_DRAGON_GROWL";
            float spawnSoundVolume = 1.0f;
            float spawnSoundPitch = 1.0f;

            ConfigurationSection soundSection = bossSection.getConfigurationSection("spawn-sound");
            if (soundSection != null) {
                spawnSoundEnabled = soundSection.getBoolean("enabled", false);
                spawnSound = soundSection.getString("sound", "ENTITY_ENDER_DRAGON_GROWL");
                spawnSoundVolume = (float) soundSection.getDouble("volume", 1.0);
                spawnSoundPitch = (float) soundSection.getDouble("pitch", 1.0);
            }

            // --- Reward fields from reward.yml ---
            boolean rewardsEnabled = false;
            boolean itemProtectionEnabled = false;
            int itemProtectionDuration = 60;
            String itemProtectionExpireAction = "public";
            double lastHitMultiplier = 1.0;
            double globalMultiplier = 1.0;
            boolean multiplyDropTables = false;

            boolean damageRewardsEnabled = false;
            List<RewardTierConfig> damageRewardTiers = new ArrayList<>();
            boolean tankRewardsEnabled = false;
            List<RewardTierConfig> tankRewardTiers = new ArrayList<>();
            boolean supportRewardsEnabled = false;
            List<RewardTierConfig> supportRewardTiers = new ArrayList<>();

            ConfigurationSection rewardSection = rewardConfig.getConfigurationSection(bossId);
            if (rewardSection != null) {
                rewardsEnabled = rewardSection.getBoolean("enabled", false);
                itemProtectionEnabled = rewardSection.getBoolean("item-protection.enabled", false);
                itemProtectionDuration = rewardSection.getInt("item-protection.duration", 60);
                itemProtectionExpireAction = rewardSection.getString("item-protection.expire-action", "public");
                lastHitMultiplier = rewardSection.getDouble("last-hit-bonus.multiplier", 1.0);
                globalMultiplier = rewardSection.getDouble("global-multiplier", 1.0);
                multiplyDropTables = rewardSection.getBoolean("multiply-drop-tables", false);

                // Damage category (with legacy flat-tiers fallback)
                ConfigurationSection damageSection = rewardSection.getConfigurationSection("damage");
                if (damageSection != null) {
                    damageRewardsEnabled = damageSection.getBoolean("enabled", true);
                    damageRewardTiers = parseTiers(damageSection.getConfigurationSection("tiers"));
                } else {
                    ConfigurationSection legacyTiers = rewardSection.getConfigurationSection("tiers");
                    if (legacyTiers != null) {
                        damageRewardsEnabled = true;
                        damageRewardTiers = parseTiers(legacyTiers);
                    }
                }

                // Tank category
                ConfigurationSection tankSection = rewardSection.getConfigurationSection("tank");
                if (tankSection != null) {
                    tankRewardsEnabled = tankSection.getBoolean("enabled", true);
                    tankRewardTiers = parseTiers(tankSection.getConfigurationSection("tiers"));
                }

                // Support category
                ConfigurationSection supportSection = rewardSection.getConfigurationSection("support");
                if (supportSection != null) {
                    supportRewardsEnabled = supportSection.getBoolean("enabled", true);
                    supportRewardTiers = parseTiers(supportSection.getConfigurationSection("tiers"));
                }

                if (rewardsEnabled) {
                    plugin.getLogger().info("Boss '" + bossId + "' reward tiers: damage="
                            + damageRewardTiers.size() + ", tank=" + tankRewardTiers.size()
                            + ", support=" + supportRewardTiers.size());
                }
            }

            // Log random mob pool info
            if (randomSpawn && mobEntries.size() > 1) {
                plugin.getLogger().info("Boss '" + bossId + "' has random spawn pool: "
                        + mobEntries.size() + " mobs");
            }

            // M2 FIX: use Builder instead of 28-arg constructor
            BossConfig config = new BossConfig.Builder(bossId, mythicMobId)
                    .mythicMobEntries(mobEntries)
                    .displayName(displayName)
                    .randomSpawn(randomSpawn)
                    .expireSeconds(expireSeconds)
                    .clearOnExpire(clearOnExpire)
                    .scheduleTimes(scheduleTimes)
                    .timeZone(timeZone)
                    .spawnWorldName(worldName)
                    .spawnX(x).spawnY(y).spawnZ(z)
                    .spawnYaw(yaw).spawnPitch(pitch)
                    .leashRadius(leashRadius)
                    .loadChunks(loadChunks)
                    .noPlayerDespawnSeconds(noPlayerDespawnSeconds)
                    .spawnSoundEnabled(spawnSoundEnabled)
                    .spawnSound(spawnSound)
                    .spawnSoundVolume(spawnSoundVolume)
                    .spawnSoundPitch(spawnSoundPitch)
                    .deathChat(deathChat)
                    .deathTitle(deathTitle)
                    .spawnWarningChat(spawnWarningChat)
                    .spawnWarningTitle(spawnWarningTitle)
                    .spawnWarningBossbar(spawnWarningBossbar)
                    .spawnWarningIntervals(spawnWarningIntervals)
                    .rewardsEnabled(rewardsEnabled)
                    .itemProtectionEnabled(itemProtectionEnabled)
                    .itemProtectionDuration(itemProtectionDuration)
                    .itemProtectionExpireAction(itemProtectionExpireAction)
                    .lastHitMultiplier(lastHitMultiplier)
                    .globalMultiplier(globalMultiplier)
                    .multiplyDropTables(multiplyDropTables)
                    .damageRewardsEnabled(damageRewardsEnabled)
                    .damageRewardTiers(damageRewardTiers)
                    .tankRewardsEnabled(tankRewardsEnabled)
                    .tankRewardTiers(tankRewardTiers)
                    .supportRewardsEnabled(supportRewardsEnabled)
                    .supportRewardTiers(supportRewardTiers)
                    .build();

            bossConfigs.put(bossId, config);
        }

        plugin.getLogger().info("Loaded " + bossConfigs.size() + " boss configurations");
    }

    /**
     * Parse a comma-separated mythicmob string into MobEntry list.
     * Supports optional weight after mob ID:
     *   "boss1, boss2, boss3"         → equal chance (weight=0)
     *   "boss1 10, boss2 40, boss3 50" → weighted by percentage
     */
    private List<MobEntry> parseMobEntries(String raw) {
        List<MobEntry> entries = new ArrayList<>();
        if (raw == null || raw.isBlank()) return entries;

        String[] parts = raw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            // Check if the LAST token is a number (weight)
            // e.g. "ink[Main]Wu_summoner 10" → mobId="ink[Main]Wu_summoner", weight=10
            int lastSpace = trimmed.lastIndexOf(' ');
            if (lastSpace > 0) {
                String possibleWeight = trimmed.substring(lastSpace + 1);
                try {
                    double weight = Double.parseDouble(possibleWeight);
                    String mobId = trimmed.substring(0, lastSpace).trim();
                    entries.add(new MobEntry(mobId, weight, 0));
                    continue;
                } catch (NumberFormatException ignored) {
                    // Not a number — entire string is the mob ID (may contain spaces)
                }
            }

            // No weight, just mob ID
            entries.add(new MobEntry(trimmed, 0, 0));
        }
        return entries;
    }

    /**
     * Parse a YAML map section into MobEntry list with per-mob expire times.
     * Format:
     *   mythicmob:
     *     BossA: 20    ← expire in minutes (0 = use global)
     *     BossB: 30
     * Automatically enables random spawn when multiple entries exist.
     */
    private List<MobEntry> parseMobEntriesFromSection(ConfigurationSection section) {
        List<MobEntry> entries = new ArrayList<>();
        for (String mobId : section.getKeys(false)) {
            int expireMinutes = section.getInt(mobId, 0);
            entries.add(new MobEntry(mobId, 0, expireMinutes));
        }
        return entries;
    }

    private List<RewardTierConfig> parseTiers(ConfigurationSection tiersSection) {
        List<RewardTierConfig> tiers = new ArrayList<>();
        if (tiersSection == null)
            return tiers;

        for (String rankKey : tiersSection.getKeys(false)) {
            ConfigurationSection tierSection = tiersSection.getConfigurationSection(rankKey);
            if (tierSection == null)
                continue;

            int rank;
            try {
                rank = Integer.parseInt(rankKey);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Ignoring non-numeric reward rank '" + rankKey
                        + "' in " + tiersSection.getCurrentPath());
                continue;
            }
            if (rank <= 0) {
                plugin.getLogger().warning("Ignoring reward rank " + rank
                        + " in " + tiersSection.getCurrentPath() + ": ranks start at 1");
                continue;
            }

            double multiplier = tierSection.getDouble("multiplier", 1.0);
            List<String> commands = tierSection.getStringList("commands");
            List<String> dropTables = tierSection.getStringList("drop-tables");

            List<ItemRewardConfig> items = new ArrayList<>();
            for (Map<?, ?> itemMap : tierSection.getMapList("items")) {
                String type = itemMap.containsKey("type") ? String.valueOf(itemMap.get("type")) : "vanilla";
                String material = itemMap.containsKey("material") ? String.valueOf(itemMap.get("material")) : "DIAMOND";
                String itemId = itemMap.containsKey("id") ? String.valueOf(itemMap.get("id")) : "";
                int amount = getInt(itemMap.get("amount"), 1);
                String displayName = itemMap.containsKey("display-name") ? String.valueOf(itemMap.get("display-name"))
                        : "";
                items.add(new ItemRewardConfig(type, material, itemId, amount, displayName));
            }

            RandomLootConfig randomLoot = parseRandomLoot(tierSection.getConfigurationSection("random-loot"));

            tiers.add(new RewardTierConfig(rank, multiplier, commands, dropTables, items, randomLoot));
        }

        return tiers;
    }

    private RandomLootConfig parseRandomLoot(ConfigurationSection section) {
        if (section == null) {
            return new RandomLootConfig(false, 0, List.of());
        }

        boolean enabled = section.getBoolean("enabled", true);
        int rolls = Math.max(0, section.getInt("rolls", 0));
        List<RandomLootEntry> entries = new ArrayList<>();

        for (Map<?, ?> entryMap : section.getMapList("entries")) {
            String type = (entryMap.containsKey("type") ? String.valueOf(entryMap.get("type")) : "vanilla")
                    .toLowerCase(Locale.ROOT);
            double chance = clamp(getDouble(entryMap.get("chance"), 0.0), 0.0, 100.0);
            double weight = Math.max(0.0, getDouble(entryMap.get("weight"), 1.0));
            String material = entryMap.containsKey("material") ? String.valueOf(entryMap.get("material")) : "";
            String itemId = entryMap.containsKey("id") ? String.valueOf(entryMap.get("id")) : "";
            int amount = getInt(entryMap.get("amount"), 1);
            String displayName = entryMap.containsKey("display-name") ? String.valueOf(entryMap.get("display-name")) : "";
            String command = entryMap.containsKey("command") ? String.valueOf(entryMap.get("command")) : "";
            String dropTable = entryMap.containsKey("drop-table") ? String.valueOf(entryMap.get("drop-table")) : "";

            entries.add(new RandomLootEntry(type, chance, weight, material, itemId,
                    amount, displayName, command, dropTable));
        }

        return new RandomLootConfig(enabled, rolls, List.copyOf(entries));
    }

    private int getInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double getDouble(Object raw, double fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Map<String, BossConfig> getAllBossConfigs() {
        return new HashMap<>(bossConfigs);
    }

    public BossConfig getBossConfig(String bossId) {
        return bossConfigs.get(bossId);
    }

    public void reload() {
        loadAll();
    }
}
