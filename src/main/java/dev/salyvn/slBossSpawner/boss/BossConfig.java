package dev.salyvn.slBossSpawner.boss;

import org.bukkit.Location;

import java.time.ZoneId;
import java.util.List;

public class BossConfig {
    private final String id;
    private final String mythicMobId;
    private final int expireSeconds;
    private final boolean clearOnExpire;
    private final List<String> scheduleTimes;
    private final ZoneId timeZone;
    private final Location spawnLocation;
    private final int leashRadius;
    private final boolean loadChunks;

    // Broadcast config
    private final boolean deathChat;
    private final boolean deathTitle;
    private final boolean spawnWarningChat;
    private final boolean spawnWarningTitle;
    private final boolean spawnWarningBossbar;
    private final List<Integer> spawnWarningIntervals;

    // Reward config
    private final boolean rewardsEnabled;
    private final boolean itemProtectionEnabled;
    private final int itemProtectionDuration;
    private final String itemProtectionExpireAction;
    private final double lastHitMultiplier;
    private final double globalMultiplier;
    private final boolean multiplyDropTables;

    // Per-category reward tiers
    private final List<RewardTierConfig> damageRewardTiers;
    private final boolean damageRewardsEnabled;
    private final List<RewardTierConfig> tankRewardTiers;
    private final boolean tankRewardsEnabled;
    private final List<RewardTierConfig> supportRewardTiers;
    private final boolean supportRewardsEnabled;

    public BossConfig(String id, String mythicMobId, int expireSeconds, boolean clearOnExpire,
                      List<String> scheduleTimes, ZoneId timeZone, Location spawnLocation,
                      int leashRadius, boolean loadChunks,
                      boolean deathChat, boolean deathTitle,
                      boolean spawnWarningChat, boolean spawnWarningTitle, boolean spawnWarningBossbar,
                      List<Integer> spawnWarningIntervals,
                      boolean rewardsEnabled, boolean itemProtectionEnabled, int itemProtectionDuration,
                      String itemProtectionExpireAction, double lastHitMultiplier, double globalMultiplier,
                      boolean multiplyDropTables,
                      boolean damageRewardsEnabled, List<RewardTierConfig> damageRewardTiers,
                      boolean tankRewardsEnabled, List<RewardTierConfig> tankRewardTiers,
                      boolean supportRewardsEnabled, List<RewardTierConfig> supportRewardTiers) {
        this.id = id;
        this.mythicMobId = mythicMobId;
        this.expireSeconds = expireSeconds;
        this.clearOnExpire = clearOnExpire;
        this.scheduleTimes = scheduleTimes;
        this.timeZone = timeZone;
        this.spawnLocation = spawnLocation;
        this.leashRadius = leashRadius;
        this.loadChunks = loadChunks;
        this.deathChat = deathChat;
        this.deathTitle = deathTitle;
        this.spawnWarningChat = spawnWarningChat;
        this.spawnWarningTitle = spawnWarningTitle;
        this.spawnWarningBossbar = spawnWarningBossbar;
        this.spawnWarningIntervals = spawnWarningIntervals;
        this.rewardsEnabled = rewardsEnabled;
        this.itemProtectionEnabled = itemProtectionEnabled;
        this.itemProtectionDuration = itemProtectionDuration;
        this.itemProtectionExpireAction = itemProtectionExpireAction;
        this.lastHitMultiplier = lastHitMultiplier;
        this.globalMultiplier = globalMultiplier;
        this.multiplyDropTables = multiplyDropTables;
        this.damageRewardsEnabled = damageRewardsEnabled;
        this.damageRewardTiers = damageRewardTiers;
        this.tankRewardsEnabled = tankRewardsEnabled;
        this.tankRewardTiers = tankRewardTiers;
        this.supportRewardsEnabled = supportRewardsEnabled;
        this.supportRewardTiers = supportRewardTiers;
    }

    public String getId() { return id; }
    public String getMythicMobId() { return mythicMobId; }
    public int getExpireSeconds() { return expireSeconds; }
    public boolean isClearOnExpire() { return clearOnExpire; }
    public List<String> getScheduleTimes() { return scheduleTimes; }
    public ZoneId getTimeZone() { return timeZone; }
    public Location getSpawnLocation() { return spawnLocation; }
    public int getLeashRadius() { return leashRadius; }
    public boolean isLoadChunks() { return loadChunks; }
    public boolean isDeathChat() { return deathChat; }
    public boolean isDeathTitle() { return deathTitle; }
    public boolean isSpawnWarningChat() { return spawnWarningChat; }
    public boolean isSpawnWarningTitle() { return spawnWarningTitle; }
    public boolean isSpawnWarningBossbar() { return spawnWarningBossbar; }
    public List<Integer> getSpawnWarningIntervals() { return spawnWarningIntervals; }
    public boolean isRewardsEnabled() { return rewardsEnabled; }
    public boolean isItemProtectionEnabled() { return itemProtectionEnabled; }
    public int getItemProtectionDuration() { return itemProtectionDuration; }
    public String getItemProtectionExpireAction() { return itemProtectionExpireAction; }
    public double getLastHitMultiplier() { return lastHitMultiplier; }
    public double getGlobalMultiplier() { return globalMultiplier; }
    public boolean isMultiplyDropTables() { return multiplyDropTables; }

    /** @deprecated Use getDamageRewardTiers() instead. Kept for backward compat. */
    public List<RewardTierConfig> getRewardTiers() { return damageRewardTiers; }

    public boolean isDamageRewardsEnabled() { return damageRewardsEnabled; }
    public List<RewardTierConfig> getDamageRewardTiers() { return damageRewardTiers; }
    public boolean isTankRewardsEnabled() { return tankRewardsEnabled; }
    public List<RewardTierConfig> getTankRewardTiers() { return tankRewardTiers; }
    public boolean isSupportRewardsEnabled() { return supportRewardsEnabled; }
    public List<RewardTierConfig> getSupportRewardTiers() { return supportRewardTiers; }

    /**
     * Per-rank reward tier configuration.
     */
    public record RewardTierConfig(
        int rank,
        double multiplier,
        List<String> commands,
        List<String> dropTables,
        List<ItemRewardConfig> items
    ) {}

    /**
     * Item reward definition (vanilla, mmoitems, or mythicmobs).
     */
    public record ItemRewardConfig(
        String type,       // "vanilla", "mmoitems", or "mythicmobs"
        String material,   // vanilla: Material name; mmoitems: TYPE; mythicmobs: item internal name
        String id,         // mmoitems: item ID; others: unused
        int amount,
        String displayName // for vanilla display name
    ) {}
}
