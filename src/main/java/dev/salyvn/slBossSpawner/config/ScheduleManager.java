package dev.salyvn.slBossSpawner.config;

import dev.salyvn.slBossSpawner.SLBossSpawner;
import dev.salyvn.slBossSpawner.boss.BossConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.ItemRewardConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.RewardTierConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.ZoneId;
import java.util.*;

public class ScheduleManager {
    private final SLBossSpawner plugin;
    private final Map<String, BossConfig> bossConfigs = new HashMap<>();

    public ScheduleManager(SLBossSpawner plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void loadAll() {
        // Load schedules
        File scheduleFile = new File(plugin.getDataFolder(), "schedules.yml");
        if (!scheduleFile.exists()) {
            plugin.saveResource("schedules.yml", false);
        }
        YamlConfiguration scheduleConfig = YamlConfiguration.loadConfiguration(scheduleFile);

        // Load rewards
        File rewardFile = new File(plugin.getDataFolder(), "reward.yml");
        if (!rewardFile.exists()) {
            plugin.saveResource("reward.yml", false);
        }
        YamlConfiguration rewardConfig = YamlConfiguration.loadConfiguration(rewardFile);

        bossConfigs.clear();

        for (String bossId : scheduleConfig.getKeys(false)) {
            ConfigurationSection bossSection = scheduleConfig.getConfigurationSection(bossId);
            if (bossSection == null) continue;

            // --- Schedule config ---
            String mythicMobId = bossSection.getString("mythicmob", "SkeletonKing");
            int expireSeconds = bossSection.getInt("expire", 3600);
            boolean clearOnExpire = bossSection.getBoolean("clearOnExpire", true);
            List<String> scheduleTimes = bossSection.getStringList("time-settings.time");

            String zoneStr = bossSection.getString("time-settings.zone", "Asia/Ho_Chi_Minh");
            ZoneId timeZone;
            try {
                timeZone = ZoneId.of(zoneStr);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid timezone '" + zoneStr + "' for boss '" + bossId + "', using Asia/Ho_Chi_Minh");
                timeZone = ZoneId.of("Asia/Ho_Chi_Minh");
            }

            String worldName = bossSection.getString("location.world", "world");
            World world = Bukkit.getWorld(worldName);
            Location spawnLocation = null;
            if (world != null) {
                double x = bossSection.getDouble("location.x");
                double y = bossSection.getDouble("location.y");
                double z = bossSection.getDouble("location.z");
                spawnLocation = new Location(world, x, y, z);
            } else {
                plugin.getLogger().warning("World '" + worldName + "' not found for boss '" + bossId + "'");
            }

            int leashRadius = bossSection.getInt("location.leashRadius", 10);
            boolean loadChunks = bossSection.getBoolean("loadChunks", true);

            // Broadcast config
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
                if (!intervals.isEmpty()) {
                    spawnWarningIntervals = intervals;
                }
            }

            // --- Reward config from reward.yml ---
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

                // Parse per-category rewards
                // Damage category
                ConfigurationSection damageSection = rewardSection.getConfigurationSection("damage");
                if (damageSection != null) {
                    damageRewardsEnabled = damageSection.getBoolean("enabled", true);
                    damageRewardTiers = parseTiers(damageSection.getConfigurationSection("tiers"));
                } else {
                    // Backward compat: parse old flat "tiers" as damage tiers
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
            }

            BossConfig config = new BossConfig(
                bossId, mythicMobId, expireSeconds, clearOnExpire,
                scheduleTimes, timeZone, spawnLocation, leashRadius, loadChunks,
                deathChat, deathTitle,
                spawnWarningChat, spawnWarningTitle, spawnWarningBossbar,
                spawnWarningIntervals,
                rewardsEnabled, itemProtectionEnabled, itemProtectionDuration,
                itemProtectionExpireAction, lastHitMultiplier, globalMultiplier,
                multiplyDropTables,
                damageRewardsEnabled, damageRewardTiers,
                tankRewardsEnabled, tankRewardTiers,
                supportRewardsEnabled, supportRewardTiers
            );

            bossConfigs.put(bossId, config);
        }

        plugin.getLogger().info("Loaded " + bossConfigs.size() + " boss configurations");
    }

    /**
     * Parse reward tiers from a "tiers" ConfigurationSection.
     */
    private List<RewardTierConfig> parseTiers(ConfigurationSection tiersSection) {
        List<RewardTierConfig> tiers = new ArrayList<>();
        if (tiersSection == null) return tiers;

        for (String rankKey : tiersSection.getKeys(false)) {
            ConfigurationSection tierSection = tiersSection.getConfigurationSection(rankKey);
            if (tierSection == null) continue;

            int rank;
            try {
                rank = Integer.parseInt(rankKey);
            } catch (NumberFormatException e) {
                continue;
            }

            double multiplier = tierSection.getDouble("multiplier", 1.0);
            List<String> commands = tierSection.getStringList("commands");
            List<String> dropTables = tierSection.getStringList("drop-tables");

            List<ItemRewardConfig> items = new ArrayList<>();
            List<Map<?, ?>> itemList = tierSection.getMapList("items");
            for (Map<?, ?> itemMap : itemList) {
                String type = itemMap.containsKey("type") ? String.valueOf(itemMap.get("type")) : "vanilla";
                String material = itemMap.containsKey("material") ? String.valueOf(itemMap.get("material")) : "DIAMOND";
                String itemId = itemMap.containsKey("id") ? String.valueOf(itemMap.get("id")) : "";
                int amount = itemMap.containsKey("amount") ? ((Number) itemMap.get("amount")).intValue() : 1;
                String displayName = itemMap.containsKey("display-name") ? String.valueOf(itemMap.get("display-name")) : "";
                items.add(new ItemRewardConfig(type, material, itemId, amount, displayName));
            }

            tiers.add(new RewardTierConfig(rank, multiplier, commands, dropTables, items));
        }

        return tiers;
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
