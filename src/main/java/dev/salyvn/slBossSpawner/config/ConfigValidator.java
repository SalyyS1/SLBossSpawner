package dev.salyvn.slBossSpawner.config;

import dev.salyvn.slBossSpawner.boss.BossConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.ItemRewardConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.RandomLootConfig;
import dev.salyvn.slBossSpawner.boss.BossConfig.RandomLootEntry;
import dev.salyvn.slBossSpawner.boss.BossConfig.RewardTierConfig;
import org.bukkit.Bukkit;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Performs cheap, read-only checks for the configuration behind a boss. */
public final class ConfigValidator {
    private ConfigValidator() {
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public record Issue(Severity severity, String message) {
    }

    public static List<Issue> validate(BossConfig config) {
        if (config == null) {
            return List.of(new Issue(Severity.ERROR, "configuration is missing"));
        }

        List<Issue> issues = new ArrayList<>();
        if (blank(config.getMythicMobId())) {
            issues.add(error("mythicMob is empty"));
        }
        validateMobEntries(config, issues);
        validateLocation(config, issues);
        validateSchedule(config, issues);
        validateTiming(config, issues);
        if (config.isDamageRewardsEnabled()) {
            validateTiers("damage", config.getDamageRewardTiers(), issues);
        }
        if (config.isTankRewardsEnabled()) {
            validateTiers("tank", config.getTankRewardTiers(), issues);
        }
        if (config.isSupportRewardsEnabled()) {
            validateTiers("support", config.getSupportRewardTiers(), issues);
        }
        return List.copyOf(issues);
    }

    private static void validateMobEntries(BossConfig config, List<Issue> issues) {
        List<BossConfig.MobEntry> entries = config.getMythicMobEntries();
        if (entries == null || entries.isEmpty()) {
            issues.add(warning("no random mob entries configured; the default mythicMob is used"));
            return;
        }

        double totalWeight = 0D;
        boolean weighted = false;
        for (BossConfig.MobEntry entry : entries) {
            if (entry == null || blank(entry.mobId())) {
                issues.add(error("a random mob entry has no mob id"));
                continue;
            }
            if (!Double.isFinite(entry.weight()) || entry.weight() < 0D) {
                issues.add(error("mob '" + entry.mobId() + "' has an invalid weight"));
            } else if (entry.weight() > 0D) {
                weighted = true;
                totalWeight += entry.weight();
            }
            if (entry.expireMinutes() < 0) {
                issues.add(warning("mob '" + entry.mobId() + "' has a negative expireMinutes"));
            }
        }
        if (weighted && totalWeight <= 0D) {
            issues.add(error("random mob weights must total more than zero"));
        } else if (weighted && totalWeight > 100.0001D) {
            issues.add(warning("random mob weights total " + trim(totalWeight)
                    + "; values are treated as relative weights"));
        }
    }

    private static void validateLocation(BossConfig config, List<Issue> issues) {
        if (blank(config.getSpawnWorldName())) {
            issues.add(error("spawn world is empty"));
        } else if (Bukkit.getWorld(config.getSpawnWorldName()) == null) {
            issues.add(warning("spawn world '" + config.getSpawnWorldName() + "' is not loaded"));
        }
        if (!Double.isFinite(config.getSpawnX()) || !Double.isFinite(config.getSpawnY())
                || !Double.isFinite(config.getSpawnZ())) {
            issues.add(error("spawn coordinates must be finite numbers"));
        } else if (config.getSpawnY() < -64 || config.getSpawnY() > 320) {
            issues.add(error("spawn Y must be between -64 and 320 on Paper 1.21"));
        }
    }

    private static void validateSchedule(BossConfig config, List<Issue> issues) {
        if (config.getTimeZone() == null) {
            issues.add(error("timezone is missing"));
        }
        List<String> times = config.getScheduleTimes();
        if (times == null || times.isEmpty()) {
            issues.add(warning("no schedule times configured; only manual spawning is available"));
            return;
        }

        Set<String> seen = new HashSet<>();
        for (String value : times) {
            if (blank(value)) {
                issues.add(error("schedule contains an empty time"));
                continue;
            }
            try {
                LocalTime.parse(value);
            } catch (RuntimeException e) {
                issues.add(error("invalid schedule time '" + value + "' (use HH:mm)"));
            }
            if (!seen.add(value.toLowerCase(Locale.ROOT))) {
                issues.add(warning("schedule time '" + value + "' is duplicated"));
            }
        }
    }

    private static void validateTiming(BossConfig config, List<Issue> issues) {
        if (config.getExpireSeconds() <= 0) {
            issues.add(error("expireSeconds must be greater than zero"));
        }
        if (config.getLeashRadius() < 0) {
            issues.add(error("leashRadius cannot be negative"));
        }
        if (config.getNoPlayerDespawnSeconds() < 0) {
            issues.add(error("noPlayerDespawnSeconds cannot be negative"));
        }
    }

    private static void validateTiers(String category, List<RewardTierConfig> tiers, List<Issue> issues) {
        if (tiers == null || tiers.isEmpty()) {
            issues.add(warning(category + " rewards are enabled but no tiers are configured"));
            return;
        }

        Set<Integer> ranks = new HashSet<>();
        for (RewardTierConfig tier : tiers) {
            if (tier == null) {
                issues.add(error(category + " rewards contain an empty tier"));
                continue;
            }
            if (tier.rank() <= 0 || !ranks.add(tier.rank())) {
                issues.add(error(category + " reward tiers must have unique positive ranks"));
            }
            if (!Double.isFinite(tier.multiplier()) || tier.multiplier() < 0D) {
                issues.add(error(category + " tier " + tier.rank() + " has an invalid multiplier"));
            }
            List<ItemRewardConfig> items = tier.items() == null ? List.of() : tier.items();
            for (ItemRewardConfig item : items) {
                if (item == null || blank(item.type()) || blank(item.material()) || item.amount() <= 0) {
                    issues.add(error(category + " tier " + tier.rank() + " has an invalid item reward"));
                }
            }
            RandomLootConfig loot = tier.randomLoot();
            if (loot != null && loot.enabled()) {
                if (loot.rolls() <= 0 || loot.entries() == null || loot.entries().isEmpty()) {
                    issues.add(error(category + " tier " + tier.rank()
                            + " enables random loot without rolls and entries"));
                }
                if (loot.entries() != null) {
                    for (RandomLootEntry entry : loot.entries()) {
                        if (entry == null || !Double.isFinite(entry.chance()) || entry.chance() < 0D
                                || entry.chance() > 100D || !Double.isFinite(entry.weight()) || entry.weight() < 0D) {
                            issues.add(error(category + " tier " + tier.rank()
                                    + " has an invalid random loot chance/weight"));
                        }
                    }
                }
            }
        }
    }

    private static Issue error(String message) {
        return new Issue(Severity.ERROR, message);
    }

    private static Issue warning(String message) {
        return new Issue(Severity.WARNING, message);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(double value) {
        return value == (long) value ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
