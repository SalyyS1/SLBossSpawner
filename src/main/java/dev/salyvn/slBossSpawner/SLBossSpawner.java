package dev.salyvn.slBossSpawner;

import dev.salyvn.slBossSpawner.boss.BossScheduler;
import dev.salyvn.slBossSpawner.broadcast.BroadcastManager;
import dev.salyvn.slBossSpawner.commands.BossCommand;
import dev.salyvn.slBossSpawner.config.ConfigManager;
import dev.salyvn.slBossSpawner.config.MessageManager;
import dev.salyvn.slBossSpawner.config.ScheduleManager;
import dev.salyvn.slBossSpawner.listener.BossDeathListener;
import dev.salyvn.slBossSpawner.statistics.CalendarQuarterSeasonResolver;
import dev.salyvn.slBossSpawner.statistics.FileStatisticsStore;
import dev.salyvn.slBossSpawner.statistics.LeaderboardCache;
import dev.salyvn.slBossSpawner.statistics.StatisticsManager;
import dev.salyvn.slBossSpawner.statistics.StatisticsStore;
import dev.salyvn.slBossSpawner.listener.DamageTracker;
import dev.salyvn.slBossSpawner.listener.SupportTracker;
import dev.salyvn.slBossSpawner.listener.TankTracker;
import dev.salyvn.slBossSpawner.persist.BossStateManager;
import dev.salyvn.slBossSpawner.persist.PendingRewardManager;
import dev.salyvn.slBossSpawner.placeholder.BossPlaceholder;
import dev.salyvn.slBossSpawner.reward.ItemProtectionListener;
import dev.salyvn.slBossSpawner.reward.PendingRewardListener;
import dev.salyvn.slBossSpawner.reward.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SLBossSpawner extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private ScheduleManager scheduleManager;
    private volatile BossScheduler bossScheduler;
    private BroadcastManager broadcastManager;
    private DamageTracker damageTracker;
    private TankTracker tankTracker;
    private SupportTracker supportTracker;
    private BossStateManager bossStateManager;
    private PendingRewardManager pendingRewardManager;
    private RewardManager rewardManager;
    private ItemProtectionListener itemProtectionListener;
    private BossPlaceholder placeholder;
    private StatisticsStore statisticsStore;
    private StatisticsManager statisticsManager;
    private LeaderboardCache leaderboardCache;

    // H3 FIX: flag to suppress death-event processing during reload despawn
    private volatile boolean reloading = false;

    @Override
    public void onEnable() {
        if (!checkDependencies()) {
            getLogger().severe("Missing required dependencies! Disabling plugin...");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        scheduleManager = new ScheduleManager(this);
        initializeStatistics();

        broadcastManager = new BroadcastManager(this);

        damageTracker = new DamageTracker(this);
        Bukkit.getPluginManager().registerEvents(damageTracker, this);

        tankTracker = new TankTracker(this);
        Bukkit.getPluginManager().registerEvents(tankTracker, this);

        supportTracker = new SupportTracker(this);
        Bukkit.getPluginManager().registerEvents(supportTracker, this);

        itemProtectionListener = new ItemProtectionListener(this);
        Bukkit.getPluginManager().registerEvents(itemProtectionListener, this);

        pendingRewardManager = new PendingRewardManager(this);
        rewardManager = new RewardManager(this, itemProtectionListener);

        Bukkit.getPluginManager().registerEvents(new PendingRewardListener(this), this);

        bossScheduler = new BossScheduler(this, scheduleManager);

        Bukkit.getPluginManager().registerEvents(
                new BossDeathListener(this, damageTracker, tankTracker, supportTracker, rewardManager), this);

        bossStateManager = new BossStateManager(this);

        registerCommands();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            placeholder = new BossPlaceholder(this);
            placeholder.register();
            getLogger().info("PlaceholderAPI integration enabled!");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            getLogger().info("MMOItems integration enabled!");
        }

        bossScheduler.start();

        // H4 FIX: delay state restore by 2 ticks to ensure worlds are fully loaded
        Bukkit.getScheduler().runTaskLater(this,
                () -> bossStateManager.restoreFromState(bossScheduler), 2L);

        getLogger().info("SLBossSpawner enabled successfully!");
    }

    @Override
    public void onDisable() {
        shutdownStatistics();

        if (bossStateManager != null && bossScheduler != null) {
            bossStateManager.saveAll();
            bossStateManager.setDisabled(true); // Keep the shutdown snapshot intact
        }

        if (bossScheduler != null) {
            bossScheduler.stop();
        }

        if (pendingRewardManager != null) {
            pendingRewardManager.saveSync();
        }

        if (damageTracker != null)
            damageTracker.clearAll();
        if (tankTracker != null)
            tankTracker.clearAll();
        if (supportTracker != null)
            supportTracker.clearAll();

        if (itemProtectionListener != null)
            itemProtectionListener.cleanup();
        if (placeholder != null)
            placeholder.unregister();

        getLogger().info("SLBossSpawner disabled!");
    }

    private boolean checkDependencies() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            getLogger().severe("MythicMobs not found! This plugin requires MythicMobs to work.");
            return false;
        }
        return true;
    }

    private void registerCommands() {
        PluginCommand command = getCommand("slboss");
        if (command != null) {
            BossCommand bossCommand = new BossCommand(this, messageManager);
            command.setExecutor(bossCommand);
            command.setTabCompleter(bossCommand);
        }
    }

    public boolean reload() {
        // Suppress death rewards if a removed schedule's boss is despawned.
        reloading = true;
        try {

        configManager.reload();
        messageManager.reload();
        scheduleManager.reload();
        // Parse every replacement schedule before touching the live scheduler.
        // A malformed time must leave the existing spawn loop running.
        BossScheduler replacementScheduler = new BossScheduler(this, scheduleManager);
        var removedBosses = bossScheduler.getAllBossInstances().entrySet().stream()
                .filter(entry -> scheduleManager.getBossConfig(entry.getKey()) == null)
                .toList();
        for (var entry : removedBosses) {
            boolean safe = bossScheduler.isRestoreReserved(entry.getKey())
                    ? bossStateManager.canDiscardReservedBoss(entry.getKey())
                    : entry.getValue().canDespawn();
            if (!safe) {
                getLogger().warning("Reload stopped: cannot verify boss '"
                        + entry.getKey() + "' before removing its schedule");
                return false;
            }
        }

        // Recreate the lightweight YAML store with potentially updated config.
        shutdownStatistics();
        initializeStatistics();

        // Remove bosses whose schedule was deleted; retain the others so reload
        // can attach the new scheduler to their live MythicMobs entities.
        for (var entry : removedBosses) {
            if (bossScheduler.isRestoreReserved(entry.getKey())
                    && !bossStateManager.discardReservedBoss(entry.getKey())) {
                getLogger().warning("Reload stopped: cannot verify saved boss '"
                        + entry.getKey() + "' before removing its schedule");
                return false;
            }
            bossScheduler.clearRestoreReservation(entry.getKey());
            if (!entry.getValue().despawn()) {
                getLogger().warning("Reload stopped: cannot remove boss '"
                        + entry.getKey() + "' before deleting its schedule");
                return false;
            }
        }
        bossStateManager.saveAll();
        bossScheduler.stop();

        bossScheduler = replacementScheduler;
        bossScheduler.start();
        bossStateManager.restoreFromState(bossScheduler);

        return true;
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Reload failed", e);
            return false;
        } finally {
            reloading = false;
        }
    }

    public boolean isReloading() {
        return reloading;
    }

    public void debug(String message) {
        if (configManager.isDebug()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }

    public BossScheduler getBossScheduler() {
        return bossScheduler;
    }

    public BroadcastManager getBroadcastManager() {
        return broadcastManager;
    }

    public DamageTracker getDamageTracker() {
        return damageTracker;
    }

    public TankTracker getTankTracker() {
        return tankTracker;
    }

    public SupportTracker getSupportTracker() {
        return supportTracker;
    }

    public BossStateManager getBossStateManager() {
        return bossStateManager;
    }

    public PendingRewardManager getPendingRewardManager() {
        return pendingRewardManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public StatisticsStore getStatisticsStore() {
        return statisticsStore;
    }

    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    public LeaderboardCache getLeaderboardCache() {
        return leaderboardCache;
    }

    private void initializeStatistics() {
        if (!configManager.isStatisticsEnabled()) {
            return;
        }

        FileStatisticsStore store = new FileStatisticsStore(this,
                configManager.getStatisticsFlushSeconds());
        statisticsStore = store;
        statisticsManager = new StatisticsManager(this, store, new CalendarQuarterSeasonResolver());
        leaderboardCache = new LeaderboardCache(this, store, statisticsManager.getSeasonResolver());
        store.start();
        getLogger().info("Statistics system enabled (async YAML store: statistics.yml)");
    }

    private void shutdownStatistics() {
        if (leaderboardCache != null) {
            leaderboardCache.stop();
            leaderboardCache = null;
        }
        if (statisticsManager != null) {
            statisticsManager.shutdown();
            statisticsManager = null;
        }
        if (statisticsStore != null) {
            statisticsStore.shutdown();
            statisticsStore = null;
        }
    }
}
