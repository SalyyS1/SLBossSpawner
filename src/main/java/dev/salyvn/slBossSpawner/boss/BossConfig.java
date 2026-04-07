package dev.salyvn.slBossSpawner.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable boss configuration. Built via {@link Builder}.
 *
 * H5 FIX: Location is stored as world-name + raw coords and resolved lazily
 * at spawn time via {@link #resolveLocation()}, avoiding the startup-order
 * problem where worlds are not yet loaded when configs parse.
 * M2 FIX: Builder pattern replaces the 28-arg constructor.
 * M3 FIX: List fields are immutable copies (List.copyOf).
 */
public class BossConfig {

    private final String id;
    private final String mythicMobId;
    private final List<MobEntry> mythicMobEntries;
    private final boolean randomSpawn;
    private final int expireSeconds;
    private final boolean clearOnExpire;
    private final List<String> scheduleTimes;
    private final ZoneId timeZone;

    // H5: deferred world resolution
    private final String spawnWorldName;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float spawnYaw;
    private final float spawnPitch;

    private final int leashRadius;
    private final boolean loadChunks;
    private final int noPlayerDespawnSeconds;

    // Spawn sound
    private final boolean spawnSoundEnabled;
    private final String spawnSound;
    private final float spawnSoundVolume;
    private final float spawnSoundPitch;

    private final boolean deathChat;
    private final boolean deathTitle;
    private final boolean spawnWarningChat;
    private final boolean spawnWarningTitle;
    private final boolean spawnWarningBossbar;
    private final List<Integer> spawnWarningIntervals;

    private final boolean rewardsEnabled;
    private final boolean itemProtectionEnabled;
    private final int itemProtectionDuration;
    private final String itemProtectionExpireAction;
    private final double lastHitMultiplier;
    private final double globalMultiplier;
    private final boolean multiplyDropTables;

    private final boolean damageRewardsEnabled;
    private final List<RewardTierConfig> damageRewardTiers;
    private final boolean tankRewardsEnabled;
    private final List<RewardTierConfig> tankRewardTiers;
    private final boolean supportRewardsEnabled;
    private final List<RewardTierConfig> supportRewardTiers;

    private BossConfig(Builder b) {
        this.id = b.id;
        this.mythicMobId = b.mythicMobId;
        this.mythicMobEntries = List.copyOf(b.mythicMobEntries);
        this.randomSpawn = b.randomSpawn;
        this.expireSeconds = b.expireSeconds;
        this.clearOnExpire = b.clearOnExpire;
        this.scheduleTimes = List.copyOf(b.scheduleTimes);
        this.timeZone = b.timeZone;
        this.spawnWorldName = b.spawnWorldName;
        this.spawnX = b.spawnX;
        this.spawnY = b.spawnY;
        this.spawnZ = b.spawnZ;
        this.spawnYaw = b.spawnYaw;
        this.spawnPitch = b.spawnPitch;
        this.leashRadius = b.leashRadius;
        this.loadChunks = b.loadChunks;
        this.noPlayerDespawnSeconds = b.noPlayerDespawnSeconds;
        this.spawnSoundEnabled = b.spawnSoundEnabled;
        this.spawnSound = b.spawnSound;
        this.spawnSoundVolume = b.spawnSoundVolume;
        this.spawnSoundPitch = b.spawnSoundPitch;
        this.deathChat = b.deathChat;
        this.deathTitle = b.deathTitle;
        this.spawnWarningChat = b.spawnWarningChat;
        this.spawnWarningTitle = b.spawnWarningTitle;
        this.spawnWarningBossbar = b.spawnWarningBossbar;
        this.spawnWarningIntervals = List.copyOf(b.spawnWarningIntervals);
        this.rewardsEnabled = b.rewardsEnabled;
        this.itemProtectionEnabled = b.itemProtectionEnabled;
        this.itemProtectionDuration = b.itemProtectionDuration;
        this.itemProtectionExpireAction = b.itemProtectionExpireAction;
        this.lastHitMultiplier = b.lastHitMultiplier;
        this.globalMultiplier = b.globalMultiplier;
        this.multiplyDropTables = b.multiplyDropTables;
        this.damageRewardsEnabled = b.damageRewardsEnabled;
        this.damageRewardTiers = List.copyOf(b.damageRewardTiers);
        this.tankRewardsEnabled = b.tankRewardsEnabled;
        this.tankRewardTiers = List.copyOf(b.tankRewardTiers);
        this.supportRewardsEnabled = b.supportRewardsEnabled;
        this.supportRewardTiers = List.copyOf(b.supportRewardTiers);
    }

    /**
     * H5: Resolve the spawn location at call time (lazy).
     * Returns null if the world is not loaded yet — callers must handle this.
     */
    public Location resolveLocation() {
        World world = Bukkit.getWorld(spawnWorldName);
        if (world == null)
            return null;
        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    // --- Getters ---

    public String getId() {
        return id;
    }

    /** Returns the first / default MythicMob ID. */
    public String getMythicMobId() {
        return mythicMobId;
    }

    /** All configured mob entries (with optional weights). */
    public List<MobEntry> getMythicMobEntries() {
        return mythicMobEntries;
    }

    public boolean isRandomSpawn() {
        return randomSpawn;
    }

    /**
     * Pick a mob ID for this spawn cycle.
     * If random is disabled or list has only 1 entry, returns the default mob.
     * If entries have weights, uses weighted random; otherwise equal chance.
     */
    public String pickMobId() {
        if (!randomSpawn || mythicMobEntries.size() <= 1) {
            return mythicMobId;
        }

        boolean hasWeights = mythicMobEntries.stream().anyMatch(e -> e.weight() > 0);

        if (hasWeights) {
            // Weighted random: weight = percentage
            double totalWeight = mythicMobEntries.stream().mapToDouble(MobEntry::weight).sum();
            double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
            double cumulative = 0;
            for (MobEntry entry : mythicMobEntries) {
                cumulative += entry.weight();
                if (roll < cumulative) {
                    return entry.mobId();
                }
            }
            // Fallback (shouldn't happen)
            return mythicMobEntries.get(mythicMobEntries.size() - 1).mobId();
        } else {
            // Equal weight: pick random index
            int idx = ThreadLocalRandom.current().nextInt(mythicMobEntries.size());
            return mythicMobEntries.get(idx).mobId();
        }
    }

    public int getExpireSeconds() {
        return expireSeconds;
    }

    public boolean isClearOnExpire() {
        return clearOnExpire;
    }

    public List<String> getScheduleTimes() {
        return scheduleTimes;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public String getSpawnWorldName() {
        return spawnWorldName;
    }

    public int getLeashRadius() {
        return leashRadius;
    }

    public boolean isLoadChunks() {
        return loadChunks;
    }

    public int getNoPlayerDespawnSeconds() {
        return noPlayerDespawnSeconds;
    }

    public boolean isSpawnSoundEnabled() {
        return spawnSoundEnabled;
    }

    public String getSpawnSound() {
        return spawnSound;
    }

    public float getSpawnSoundVolume() {
        return spawnSoundVolume;
    }

    public float getSpawnSoundPitch() {
        return spawnSoundPitch;
    }

    public boolean isDeathChat() {
        return deathChat;
    }

    public boolean isDeathTitle() {
        return deathTitle;
    }

    public boolean isSpawnWarningChat() {
        return spawnWarningChat;
    }

    public boolean isSpawnWarningTitle() {
        return spawnWarningTitle;
    }

    public boolean isSpawnWarningBossbar() {
        return spawnWarningBossbar;
    }

    public List<Integer> getSpawnWarningIntervals() {
        return spawnWarningIntervals;
    }

    public boolean isRewardsEnabled() {
        return rewardsEnabled;
    }

    public boolean isItemProtectionEnabled() {
        return itemProtectionEnabled;
    }

    public int getItemProtectionDuration() {
        return itemProtectionDuration;
    }

    public String getItemProtectionExpireAction() {
        return itemProtectionExpireAction;
    }

    public double getLastHitMultiplier() {
        return lastHitMultiplier;
    }

    public double getGlobalMultiplier() {
        return globalMultiplier;
    }

    public boolean isMultiplyDropTables() {
        return multiplyDropTables;
    }

    public boolean isDamageRewardsEnabled() {
        return damageRewardsEnabled;
    }

    public List<RewardTierConfig> getDamageRewardTiers() {
        return damageRewardTiers;
    }

    public boolean isTankRewardsEnabled() {
        return tankRewardsEnabled;
    }

    public List<RewardTierConfig> getTankRewardTiers() {
        return tankRewardTiers;
    }

    public boolean isSupportRewardsEnabled() {
        return supportRewardsEnabled;
    }

    public List<RewardTierConfig> getSupportRewardTiers() {
        return supportRewardTiers;
    }

    /**
     * @deprecated Use {@link #getDamageRewardTiers()}. Kept for backward compat.
     */
    @Deprecated
    public List<RewardTierConfig> getRewardTiers() {
        return damageRewardTiers;
    }

    // --- Builder ---

    public static class Builder {
        private final String id;
        private final String mythicMobId;
        private List<MobEntry> mythicMobEntries = List.of();
        private boolean randomSpawn = false;
        private int expireSeconds = 3600;
        private boolean clearOnExpire = true;
        private List<String> scheduleTimes = List.of();
        private ZoneId timeZone = ZoneId.of("Asia/Ho_Chi_Minh");
        private String spawnWorldName = "world";
        private double spawnX, spawnY, spawnZ;
        private float spawnYaw, spawnPitch;
        private int leashRadius = 10;
        private boolean loadChunks = true;
        private int noPlayerDespawnSeconds = 180;
        private boolean spawnSoundEnabled = false;
        private String spawnSound = "ENTITY_ENDER_DRAGON_GROWL";
        private float spawnSoundVolume = 1.0f;
        private float spawnSoundPitch = 1.0f;
        private boolean deathChat = true;
        private boolean deathTitle = true;
        private boolean spawnWarningChat = true;
        private boolean spawnWarningTitle = true;
        private boolean spawnWarningBossbar = true;
        private List<Integer> spawnWarningIntervals = List.of(30, 15, 5, 1);
        private boolean rewardsEnabled;
        private boolean itemProtectionEnabled;
        private int itemProtectionDuration = 60;
        private String itemProtectionExpireAction = "public";
        private double lastHitMultiplier = 1.0;
        private double globalMultiplier = 1.0;
        private boolean multiplyDropTables;
        private boolean damageRewardsEnabled;
        private List<RewardTierConfig> damageRewardTiers = List.of();
        private boolean tankRewardsEnabled;
        private List<RewardTierConfig> tankRewardTiers = List.of();
        private boolean supportRewardsEnabled;
        private List<RewardTierConfig> supportRewardTiers = List.of();

        public Builder(String id, String mythicMobId) {
            this.id = id;
            this.mythicMobId = mythicMobId;
        }

        public Builder mythicMobEntries(List<MobEntry> v) {
            this.mythicMobEntries = v;
            return this;
        }

        public Builder randomSpawn(boolean v) {
            this.randomSpawn = v;
            return this;
        }

        public Builder expireSeconds(int v) {
            this.expireSeconds = v;
            return this;
        }

        public Builder clearOnExpire(boolean v) {
            this.clearOnExpire = v;
            return this;
        }

        public Builder scheduleTimes(List<String> v) {
            this.scheduleTimes = v;
            return this;
        }

        public Builder timeZone(ZoneId v) {
            this.timeZone = v;
            return this;
        }

        public Builder spawnWorldName(String v) {
            this.spawnWorldName = v;
            return this;
        }

        public Builder spawnX(double v) {
            this.spawnX = v;
            return this;
        }

        public Builder spawnY(double v) {
            this.spawnY = v;
            return this;
        }

        public Builder spawnZ(double v) {
            this.spawnZ = v;
            return this;
        }

        public Builder spawnYaw(float v) {
            this.spawnYaw = v;
            return this;
        }

        public Builder spawnPitch(float v) {
            this.spawnPitch = v;
            return this;
        }

        public Builder leashRadius(int v) {
            this.leashRadius = v;
            return this;
        }

        public Builder loadChunks(boolean v) {
            this.loadChunks = v;
            return this;
        }

        public Builder noPlayerDespawnSeconds(int v) {
            this.noPlayerDespawnSeconds = v;
            return this;
        }

        public Builder spawnSoundEnabled(boolean v) {
            this.spawnSoundEnabled = v;
            return this;
        }

        public Builder spawnSound(String v) {
            this.spawnSound = v;
            return this;
        }

        public Builder spawnSoundVolume(float v) {
            this.spawnSoundVolume = v;
            return this;
        }

        public Builder spawnSoundPitch(float v) {
            this.spawnSoundPitch = v;
            return this;
        }

        public Builder deathChat(boolean v) {
            this.deathChat = v;
            return this;
        }

        public Builder deathTitle(boolean v) {
            this.deathTitle = v;
            return this;
        }

        public Builder spawnWarningChat(boolean v) {
            this.spawnWarningChat = v;
            return this;
        }

        public Builder spawnWarningTitle(boolean v) {
            this.spawnWarningTitle = v;
            return this;
        }

        public Builder spawnWarningBossbar(boolean v) {
            this.spawnWarningBossbar = v;
            return this;
        }

        public Builder spawnWarningIntervals(List<Integer> v) {
            this.spawnWarningIntervals = v;
            return this;
        }

        public Builder rewardsEnabled(boolean v) {
            this.rewardsEnabled = v;
            return this;
        }

        public Builder itemProtectionEnabled(boolean v) {
            this.itemProtectionEnabled = v;
            return this;
        }

        public Builder itemProtectionDuration(int v) {
            this.itemProtectionDuration = v;
            return this;
        }

        public Builder itemProtectionExpireAction(String v) {
            this.itemProtectionExpireAction = v;
            return this;
        }

        public Builder lastHitMultiplier(double v) {
            this.lastHitMultiplier = v;
            return this;
        }

        public Builder globalMultiplier(double v) {
            this.globalMultiplier = v;
            return this;
        }

        public Builder multiplyDropTables(boolean v) {
            this.multiplyDropTables = v;
            return this;
        }

        public Builder damageRewardsEnabled(boolean v) {
            this.damageRewardsEnabled = v;
            return this;
        }

        public Builder damageRewardTiers(List<RewardTierConfig> v) {
            this.damageRewardTiers = v;
            return this;
        }

        public Builder tankRewardsEnabled(boolean v) {
            this.tankRewardsEnabled = v;
            return this;
        }

        public Builder tankRewardTiers(List<RewardTierConfig> v) {
            this.tankRewardTiers = v;
            return this;
        }

        public Builder supportRewardsEnabled(boolean v) {
            this.supportRewardsEnabled = v;
            return this;
        }

        public Builder supportRewardTiers(List<RewardTierConfig> v) {
            this.supportRewardTiers = v;
            return this;
        }

        public BossConfig build() {
            return new BossConfig(this);
        }
    }

    // --- Nested config records ---

    public record RewardTierConfig(
            int rank,
            double multiplier,
            List<String> commands,
            List<String> dropTables,
            List<ItemRewardConfig> items) {
    }

    public record ItemRewardConfig(
            String type, // "vanilla" | "mmoitems" | "mythicmobs"
            String material, // vanilla: Material name; mmoitems: TYPE; mythicmobs: item id
            String id, // mmoitems: item ID; others: unused
            int amount,
            String displayName) {
    }

    /**
     * A mob entry with an optional weight (percentage).
     * weight <= 0 means "equal chance" (no explicit weight).
     */
    public record MobEntry(String mobId, double weight) {
    }
}
